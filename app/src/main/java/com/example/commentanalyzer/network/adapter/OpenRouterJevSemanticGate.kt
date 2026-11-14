package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.domain.gate.SemanticGate
import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import com.example.commentanalyzer.domain.model.GateItemResult
import com.example.commentanalyzer.network.dto.jev.JevAnswerValue
import com.example.commentanalyzer.network.dto.jev.JevCriteria
import com.example.commentanalyzer.network.dto.jev.JevDecisionResponse
import com.example.commentanalyzer.network.dto.jev.JevQuestionItem
import com.example.commentanalyzer.network.dto.jev.JevRequest
import com.example.commentanalyzer.network.jev.JevApiService
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class OpenRouterJevSemanticGate(
    private val jevApiService: JevApiService,
    private val tokenProvider: () -> String?
) : SemanticGate {

    override suspend fun screen(batch: List<GateInput>, config: GateConfig): GateBatchResult {
        if (batch.isEmpty()) {
            return GateBatchResult(
                batchId = "jev-empty-${System.currentTimeMillis()}",
                modelResolved = config.model,
                provider = null,
                inputTokens = 0L,
                outputTokens = 0L,
                costUsd = 0.0,
                results = emptyList(),
                error = null
            )
        }

        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            return GateBatchResult(
                batchId = "jev-error-${System.currentTimeMillis()}",
                modelResolved = config.model,
                provider = null,
                inputTokens = null,
                outputTokens = null,
                costUsd = null,
                results = emptyList(),
                error = "OPENROUTER_KEY_MISSING"
            )
        }

        val contract = config.questionContract
        val requestedKey = contract.questionKey
        val batchId = "jev-batch-${System.currentTimeMillis()}"

        val outcomes = arrayOfNulls<JevCallOutcome>(batch.size)
        coroutineScope {
            val nextIndex = AtomicInteger()
            repeat(minOf(MAX_CONCURRENT_JEV_REQUESTS, batch.size)) {
                launch {
                    while (true) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= batch.size) break
                        outcomes[index] = requestDecision(batch[index], config, requestedKey)
                    }
                }
            }
        }

        val completed = outcomes.map { requireNotNull(it) }
        val resolvedModel = completed.lastOrNull { it.modelResolved != null }?.modelResolved
            ?: config.model
        val provider = completed.lastOrNull { it.provider != null }?.provider
        val totalInputTokens = completed.mapNotNull { it.inputTokens }.sum()
        val totalOutputTokens = completed.mapNotNull { it.outputTokens }.sum()
        val totalCost = completed.mapNotNull { it.costUsd }.sum()
        val firstFailure = completed.firstOrNull { it.error != null }
        val firstError = firstFailure?.error
        val firstRetryAfter = firstFailure?.retryAfterSeconds
        val results = completed.mapNotNull { it.result }

        return GateBatchResult(
            batchId = batchId,
            modelResolved = resolvedModel,
            provider = provider,
            inputTokens = totalInputTokens.takeIf { it > 0 },
            outputTokens = totalOutputTokens.takeIf { it > 0 },
            costUsd = totalCost.takeIf { it > 0.0 },
            results = if (firstError == null) results else emptyList(),
            error = firstError,
            retryAfterSeconds = firstRetryAfter
        )
    }

    private suspend fun requestDecision(
        input: GateInput,
        config: GateConfig,
        requestedKey: String
    ): JevCallOutcome {
        val isV2 = config.configVersion >= 2L
        val questions = if (isV2) {
            mapOf(
                KEY_QUALITY_GATE to JevQuestionItem(
                    type = "noul",
                    instructions = config.questionContract.instructions,
                    criteria = JevCriteria(
                        `true` = config.questionContract.trueCriterion,
                        `false` = config.questionContract.falseCriterion
                    )
                ),
                KEY_CATEGORY to JevQuestionItem(
                    type = "choice",
                    instructions = "Classify this YouTube comment into exactly one category.",
                    criteria = ALLOWED_CATEGORIES.associateWith { "Comment category: $it" }
                ),
                KEY_SENTIMENT to JevQuestionItem(
                    type = "choice",
                    instructions = "Classify the sentiment of this YouTube comment into exactly one sentiment.",
                    criteria = ALLOWED_SENTIMENTS.associateWith { "Comment sentiment: $it" }
                )
            )
        } else {
            mapOf(
                requestedKey to JevQuestionItem(
                    type = "noul",
                    instructions = config.questionContract.instructions,
                    criteria = JevCriteria(
                        `true` = config.questionContract.trueCriterion,
                        `false` = config.questionContract.falseCriterion
                    )
                )
            )
        }

        val request = JevRequest(
            model = if (config.model.isNotBlank()) config.model else "typesafe/jev-1.14",
            state = input.text,
            questions = questions
        )

        try {
            val response = jevApiService.getDecisions(request)
            if (!response.isSuccessful) {
                val code = response.code()
                val retryAfter = response.headers().get("Retry-After")?.toLongOrNull()
                val error = when (code) {
                    401 -> "HTTP_401_UNAUTHORIZED"
                    429 -> "HTTP_429_RATE_LIMIT"
                    in 500..599 -> "HTTP_SERVER_ERROR_$code"
                    else -> "HTTP_ERROR_$code"
                }
                return JevCallOutcome(error = error, retryAfterSeconds = retryAfter)
            }

            val body = response.body() ?: return JevCallOutcome(error = "JEV_NULL_RESPONSE")
            val metadata = JevCallOutcome(
                modelResolved = body.model,
                provider = body.provider,
                inputTokens = body.usage?.inputTokens,
                outputTokens = body.usage?.outputTokens,
                costUsd = body.usage?.costUsd
            )
            fun validationError(message: String) = metadata.copy(error = "JEV_VALIDATION_ERROR: $message")

            val answers = body.answers
                ?: return validationError("answers is null or empty")
            if (answers.isEmpty()) return validationError("answers is null or empty")

            if (isV2) {
                val expectedKeys = setOf(KEY_QUALITY_GATE, KEY_CATEGORY, KEY_SENTIMENT)
                if (answers.keys != expectedKeys) {
                    return validationError("Answers keys $answers.keys do not match expected $expectedKeys")
                }

                // 1. Quality gate validation
                val gateAnswer = answers[KEY_QUALITY_GATE]
                    ?: return validationError("Missing quality gate answer")
                if (!gateAnswer.type.equals("noul", ignoreCase = true)) {
                    return validationError("Wrong gate answer type (expected noul, got ${gateAnswer.type})")
                }
                val probability = gateAnswer.noul
                if (probability == null || !probability.isFinite() || probability !in 0.0..1.0) {
                    return validationError("Invalid probability value: $probability")
                }

                // 2. Category choice validation
                val catAnswer = answers[KEY_CATEGORY]
                    ?: return validationError("Missing category answer")
                if (!catAnswer.type.equals("choice", ignoreCase = true)) {
                    return validationError("Wrong category answer type (expected choice, got ${catAnswer.type})")
                }
                val category = catAnswer.choice
                if (category == null || category !in ALLOWED_CATEGORIES) {
                    return validationError("Invalid category value: $category")
                }
                val catConfidence = catAnswer.confidence
                if (catConfidence != null && (!catConfidence.isFinite() || catConfidence !in 0.0..1.0)) {
                    return validationError("Invalid category confidence: $catConfidence")
                }
                if (catAnswer.probabilities != null) {
                    for ((k, p) in catAnswer.probabilities) {
                        if (!p.isFinite() || p !in 0.0..1.0) {
                            return validationError("Invalid category probability for $k: $p")
                        }
                    }
                }

                // 3. Sentiment choice validation
                val sentAnswer = answers[KEY_SENTIMENT]
                    ?: return validationError("Missing sentiment answer")
                if (!sentAnswer.type.equals("choice", ignoreCase = true)) {
                    return validationError("Wrong sentiment answer type (expected choice, got ${sentAnswer.type})")
                }
                val sentiment = sentAnswer.choice
                if (sentiment == null || sentiment !in ALLOWED_SENTIMENTS) {
                    return validationError("Invalid sentiment value: $sentiment")
                }
                val sentConfidence = sentAnswer.confidence
                if (sentConfidence != null && (!sentConfidence.isFinite() || sentConfidence !in 0.0..1.0)) {
                    return validationError("Invalid sentiment confidence: $sentConfidence")
                }
                if (sentAnswer.probabilities != null) {
                    for ((k, p) in sentAnswer.probabilities) {
                        if (!p.isFinite() || p !in 0.0..1.0) {
                            return validationError("Invalid sentiment probability for $k: $p")
                        }
                    }
                }

                return metadata.copy(
                    result = GateItemResult(
                        commentId = input.commentId,
                        questionKey = KEY_QUALITY_GATE,
                        probability = probability,
                        rawOutput = body.id,
                        category = category,
                        categoryConfidence = catConfidence,
                        sentiment = sentiment,
                        sentimentConfidence = sentConfidence
                    )
                )
            } else {
                if (!answers.containsKey(requestedKey)) {
                    return validationError("Missing requested answer key: $requestedKey")
                }
                if (answers.keys.any { it != requestedKey }) {
                    val unexpectedKey = answers.keys.first { it != requestedKey }
                    return validationError("Unexpected answer key: $unexpectedKey")
                }
                val answer = answers[requestedKey]
                    ?: return validationError("Answer value is null for key: $requestedKey")
                if (!answer.type.equals("noul", ignoreCase = true)) {
                    return validationError("Wrong answer type (expected noul, got ${answer.type})")
                }
                val probability = answer.noul
                if (probability == null || !probability.isFinite() || probability !in 0.0..1.0) {
                    return validationError("Invalid probability value: $probability")
                }
                return metadata.copy(
                    result = GateItemResult(
                        commentId = input.commentId,
                        questionKey = requestedKey,
                        probability = probability,
                        rawOutput = body.id
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            return JevCallOutcome(error = "NETWORK_ERROR: ${failure.localizedMessage ?: "Unknown network error"}")
        } catch (failure: Exception) {
            return JevCallOutcome(error = "EXCEPTION: ${failure.localizedMessage ?: "Unknown exception"}")
        }
    }

    private data class JevCallOutcome(
        val result: GateItemResult? = null,
        val modelResolved: String? = null,
        val provider: String? = null,
        val inputTokens: Long? = null,
        val outputTokens: Long? = null,
        val costUsd: Double? = null,
        val error: String? = null,
        val retryAfterSeconds: Long? = null
    )

    companion object {
        const val MAX_CONCURRENT_JEV_REQUESTS = 4
        const val KEY_QUALITY_GATE = "comment_quality_gate"
        const val KEY_CATEGORY = "comment_category"
        const val KEY_SENTIMENT = "comment_sentiment"
        val ALLOWED_CATEGORIES = setOf("praise", "criticism", "feedback", "question", "spam", "other")
        val ALLOWED_SENTIMENTS = setOf("positive", "neutral", "negative")
    }
}
