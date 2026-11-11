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

        val results = mutableListOf<GateItemResult>()
        var resolvedModel = config.model
        var provider: String? = null
        var totalInputTokens = 0L
        var totalOutputTokens = 0L
        var totalCost = 0.0

        for (input in batch) {
            val request = JevRequest(
                model = if (config.model.isNotBlank()) config.model else "typesafe/jev-1.14",
                state = input.text,
                questions = mapOf(
                    requestedKey to JevQuestionItem(
                        type = "noul",
                        instructions = contract.instructions,
                        criteria = JevCriteria(
                            `true` = contract.trueCriterion,
                            `false` = contract.falseCriterion
                        )
                    )
                )
            )

            try {
                val response = jevApiService.getDecisions(request)
                if (!response.isSuccessful) {
                    val code = response.code()
                    val errorMsg = when (code) {
                        401 -> "HTTP_401_UNAUTHORIZED"
                        429 -> "HTTP_429_RATE_LIMIT"
                        in 500..599 -> "HTTP_SERVER_ERROR_$code"
                        else -> "HTTP_ERROR_$code"
                    }
                    return GateBatchResult(
                        batchId = batchId,
                        modelResolved = resolvedModel,
                        provider = provider,
                        inputTokens = if (totalInputTokens > 0) totalInputTokens else null,
                        outputTokens = if (totalOutputTokens > 0) totalOutputTokens else null,
                        costUsd = if (totalCost > 0.0) totalCost else null,
                        results = emptyList(),
                        error = errorMsg
                    )
                }

                val body = response.body()
                if (body == null) {
                    return GateBatchResult(
                        batchId = batchId,
                        modelResolved = resolvedModel,
                        provider = provider,
                        inputTokens = null,
                        outputTokens = null,
                        costUsd = null,
                        results = emptyList(),
                        error = "JEV_NULL_RESPONSE"
                    )
                }

                resolvedModel = body.model ?: resolvedModel
                provider = body.provider ?: provider
                body.usage?.inputTokens?.let { totalInputTokens += it }
                body.usage?.outputTokens?.let { totalOutputTokens += it }
                body.usage?.costUsd?.let { totalCost += it }

                val answers = body.answers
                if (answers.isNullOrEmpty()) {
                    return GateBatchResult(
                        batchId = batchId,
                        modelResolved = resolvedModel,
                        provider = provider,
                        inputTokens = totalInputTokens,
                        outputTokens = totalOutputTokens,
                        costUsd = totalCost,
                        results = emptyList(),
                        error = "JEV_VALIDATION_ERROR: answers is null or empty"
                    )
                }

                if (!answers.containsKey(requestedKey)) {
                    return GateBatchResult(
                        batchId = batchId,
                        modelResolved = resolvedModel,
                        provider = provider,
                        inputTokens = totalInputTokens,
                        outputTokens = totalOutputTokens,
                        costUsd = totalCost,
                        results = emptyList(),
                        error = "JEV_VALIDATION_ERROR: Missing requested answer key: $requestedKey"
                    )
                }

                for (key in answers.keys) {
                    if (key != requestedKey) {
                        return GateBatchResult(
                            batchId = batchId,
                            modelResolved = resolvedModel,
                            provider = provider,
                            inputTokens = totalInputTokens,
                            outputTokens = totalOutputTokens,
                            costUsd = totalCost,
                            results = emptyList(),
                            error = "JEV_VALIDATION_ERROR: Unexpected answer key: $key"
                        )
                    }
                }

                val answerVal = answers[requestedKey]
                if (answerVal == null) {
                    return GateBatchResult(
                        batchId = batchId,
                        modelResolved = resolvedModel,
                        provider = provider,
                        inputTokens = totalInputTokens,
                        outputTokens = totalOutputTokens,
                        costUsd = totalCost,
                        results = emptyList(),
                        error = "JEV_VALIDATION_ERROR: Answer value is null for key: $requestedKey"
                    )
                }

                if (!answerVal.type.equals("noul", ignoreCase = true)) {
                    return GateBatchResult(
                        batchId = batchId,
                        modelResolved = resolvedModel,
                        provider = provider,
                        inputTokens = totalInputTokens,
                        outputTokens = totalOutputTokens,
                        costUsd = totalCost,
                        results = emptyList(),
                        error = "JEV_VALIDATION_ERROR: Wrong answer type (expected noul, got ${answerVal.type})"
                    )
                }

                val prob = answerVal.noul
                if (prob == null || prob.isNaN() || prob.isInfinite() || prob < 0.0 || prob > 1.0) {
                    return GateBatchResult(
                        batchId = batchId,
                        modelResolved = resolvedModel,
                        provider = provider,
                        inputTokens = totalInputTokens,
                        outputTokens = totalOutputTokens,
                        costUsd = totalCost,
                        results = emptyList(),
                        error = "JEV_VALIDATION_ERROR: Invalid probability value: $prob"
                    )
                }

                results.add(
                    GateItemResult(
                        commentId = input.commentId,
                        questionKey = requestedKey,
                        probability = prob,
                        rawOutput = body.id
                    )
                )

            } catch (e: IOException) {
                return GateBatchResult(
                    batchId = batchId,
                    modelResolved = resolvedModel,
                    provider = provider,
                    inputTokens = if (totalInputTokens > 0) totalInputTokens else null,
                    outputTokens = if (totalOutputTokens > 0) totalOutputTokens else null,
                    costUsd = if (totalCost > 0.0) totalCost else null,
                    results = emptyList(),
                    error = "NETWORK_ERROR: ${e.localizedMessage ?: "Unknown network error"}"
                )
            } catch (e: Exception) {
                return GateBatchResult(
                    batchId = batchId,
                    modelResolved = resolvedModel,
                    provider = provider,
                    inputTokens = if (totalInputTokens > 0) totalInputTokens else null,
                    outputTokens = if (totalOutputTokens > 0) totalOutputTokens else null,
                    costUsd = if (totalCost > 0.0) totalCost else null,
                    results = emptyList(),
                    error = "EXCEPTION: ${e.localizedMessage ?: "Unknown exception"}"
                )
            }
        }

        return GateBatchResult(
            batchId = batchId,
            modelResolved = resolvedModel,
            provider = provider,
            inputTokens = if (totalInputTokens > 0) totalInputTokens else null,
            outputTokens = if (totalOutputTokens > 0) totalOutputTokens else null,
            costUsd = if (totalCost > 0.0) totalCost else null,
            results = results,
            error = null
        )
    }
}
