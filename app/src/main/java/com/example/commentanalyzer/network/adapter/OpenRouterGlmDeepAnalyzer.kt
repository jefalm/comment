package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.domain.analysis.DeepAnalyzer
import com.example.commentanalyzer.domain.model.AnalysisBatchResult
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput
import com.example.commentanalyzer.domain.model.AnalysisItemResult
import com.example.commentanalyzer.network.dto.glm.GlmJsonSchema
import com.example.commentanalyzer.network.dto.glm.GlmMessage
import com.example.commentanalyzer.network.dto.glm.GlmPlugin
import com.example.commentanalyzer.network.dto.glm.GlmProvider
import com.example.commentanalyzer.network.dto.glm.GlmRequest
import com.example.commentanalyzer.network.dto.glm.GlmResponse
import com.example.commentanalyzer.network.dto.glm.GlmResponseFormat
import com.example.commentanalyzer.network.glm.GlmApiService
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.security.MessageDigest

class OpenRouterGlmDeepAnalyzer(
    private val glmApiService: GlmApiService,
    private val tokenProvider: () -> String?
) : DeepAnalyzer {

    override suspend fun analyze(
        batch: List<AnalysisInput>,
        config: AnalysisConfig
    ): AnalysisBatchResult {
        val requestedModel = MODEL
        val batchId = "glm-batch-${System.currentTimeMillis()}"
        if (batch.isEmpty()) {
            return result(batchId, requestedModel, null, emptyList(), null)
        }
        if (config.model.isNotBlank() && config.model != MODEL) {
            return result(batchId, requestedModel, null, emptyList(), "GLM_UNSUPPORTED_MODEL")
        }

        val requestedIds = batch.map { it.commentId }
        if (requestedIds.any { it.isBlank() } || requestedIds.toSet().size != requestedIds.size) {
            return result(batchId, requestedModel, null, emptyList(), "GLM_INVALID_REQUEST_IDS")
        }
        if (tokenProvider().isNullOrBlank()) {
            return result(batchId, requestedModel, null, emptyList(), "OPENROUTER_KEY_MISSING")
        }

        val request = GlmRequest(
            model = requestedModel,
            messages = listOf(
                GlmMessage(
                    role = "system",
                    content = "Analyze each supplied YouTube comment. Return exactly one result per comment, " +
                        "preserve each commentId exactly, use only the specified category and sentiment values, " +
                        "and return schemaVersion 1. Keep summaries concise and grounded in the comment."
                ),
                GlmMessage(
                    role = "user",
                    content = gson.toJson(
                        mapOf(
                            "schemaVersion" to SCHEMA_VERSION,
                            "comments" to batch.map { mapOf("commentId" to it.commentId, "text" to it.text) }
                        )
                    )
                )
            ),
            responseFormat = GlmResponseFormat(
                jsonSchema = GlmJsonSchema(
                    name = "comment_analysis_batch",
                    strict = true,
                    schema = responseSchema
                )
            ),
            provider = GlmProvider(requireParameters = true),
            plugins = listOf(GlmPlugin(id = "response-healing"))
        )

        val response = try {
            glmApiService.createChatCompletion(request)
        } catch (_: IOException) {
            return result(batchId, requestedModel, null, emptyList(), "NETWORK_ERROR")
        } catch (_: Exception) {
            return result(batchId, requestedModel, null, emptyList(), "GLM_RESPONSE_ERROR")
        }
        if (!response.isSuccessful) {
            val code = response.code()
            val retryAfter = response.headers().get("Retry-After")?.toLongOrNull()
            val error = when (code) {
                401 -> "HTTP_401_UNAUTHORIZED"
                429 -> "HTTP_429_RATE_LIMIT"
                in 500..599 -> "HTTP_SERVER_ERROR_$code"
                else -> "HTTP_ERROR_$code"
            }
            return result(batchId, requestedModel, null, emptyList(), error, retryAfterSeconds = retryAfter)
        }

        val responseBody = response.body()
            ?: return result(batchId, requestedModel, null, emptyList(), "GLM_EMPTY_RESPONSE")
        val responseText = try {
            responseBody.string()
        } catch (_: IOException) {
            return result(batchId, requestedModel, null, emptyList(), "NETWORK_ERROR")
        }
        val responseJson = try {
            JsonParser.parseString(responseText)
        } catch (_: Exception) {
            return result(batchId, requestedModel, null, emptyList(), "GLM_MALFORMED_JSON")
        }
        if (!responseJson.isJsonObject) {
            return result(batchId, requestedModel, null, emptyList(), "GLM_RESPONSE_SCHEMA_MISMATCH")
        }
        val decodedResponse = try {
            gson.fromJson(responseJson, GlmResponse::class.java)
        } catch (_: Exception) {
            return result(batchId, requestedModel, null, emptyList(), "GLM_RESPONSE_SCHEMA_MISMATCH")
        } ?: return result(batchId, requestedModel, null, emptyList(), "GLM_EMPTY_RESPONSE")

        return analyzeResponse(batchId, requestedModel, requestedIds.toSet(), decodedResponse)
    }

    private fun analyzeResponse(
        batchId: String,
        requestedModel: String,
        requestedIds: Set<String>,
        response: GlmResponse
    ): AnalysisBatchResult {
        val usage = response.usage
        if ((usage?.inputTokens != null && usage.inputTokens < 0) ||
            (usage?.outputTokens != null && usage.outputTokens < 0) ||
            (usage?.costUsd != null && (!usage.costUsd.isFinite() || usage.costUsd < 0.0))
        ) {
            return result(batchId, requestedModel, response, emptyList(), "GLM_INVALID_USAGE")
        }

        val choices = response.choices
        if (choices.isNullOrEmpty() || choices.size != 1) {
            return result(batchId, requestedModel, response, emptyList(), "GLM_MISSING_RESPONSE_CONTENT")
        }
        val choice = choices.single()
        if (choice.finishReason == "length") {
            return result(batchId, requestedModel, response, emptyList(), "GLM_OUTPUT_TRUNCATED")
        }
        val content = choice.message?.content
        if (content.isNullOrBlank()) {
            return result(batchId, requestedModel, response, emptyList(), "GLM_MISSING_RESPONSE_CONTENT")
        }

        val parsed = validateContent(content)
        if (parsed.error != null) {
            return result(batchId, requestedModel, response, emptyList(), parsed.error)
        }
        val results = parsed.results.orEmpty()
        val returnedIds = results.map { it.commentId }
        val returnedIdsSet = returnedIds.toSet()
        val duplicateCount = returnedIds.size - returnedIdsSet.size
        if (duplicateCount > 0) {
            val diagnostics = computeMismatchDiagnostics(requestedIds, returnedIds)
            val errorSummary = "expectedCount=${diagnostics.expectedCount}, returnedCount=${diagnostics.returnedCount}, missingCount=${diagnostics.missingCount}, extraCount=${diagnostics.extraCount}, duplicateCount=${diagnostics.duplicateCount}, expectedSha=${diagnostics.expectedIdsSha256}, returnedSha=${diagnostics.returnedIdsSha256}"
            return result(batchId, requestedModel, response, emptyList(), "GLM_DUPLICATE_RESULT_IDS", errorSummary = errorSummary)
        }
        if (returnedIdsSet != requestedIds) {
            val diagnostics = computeMismatchDiagnostics(requestedIds, returnedIds)
            val errorSummary = "expectedCount=${diagnostics.expectedCount}, returnedCount=${diagnostics.returnedCount}, missingCount=${diagnostics.missingCount}, extraCount=${diagnostics.extraCount}, duplicateCount=${diagnostics.duplicateCount}, expectedSha=${diagnostics.expectedIdsSha256}, returnedSha=${diagnostics.returnedIdsSha256}"
            return result(batchId, requestedModel, response, emptyList(), "GLM_RESULT_ID_SET_MISMATCH", errorSummary = errorSummary)
        }
        return result(batchId, requestedModel, response, results, null)
    }

    private data class MismatchDiagnostics(
        val expectedCount: Int,
        val returnedCount: Int,
        val missingCount: Int,
        val extraCount: Int,
        val duplicateCount: Int,
        val expectedIdsSha256: String,
        val returnedIdsSha256: String
    )

    private fun computeMismatchDiagnostics(
        requestedIds: Set<String>,
        returnedIds: List<String>
    ): MismatchDiagnostics {
        val returnedIdsSet = returnedIds.toSet()
        val missing = requestedIds - returnedIdsSet
        val extra = returnedIdsSet - requestedIds
        val duplicateCount = returnedIds.size - returnedIdsSet.size
        val expectedSorted = requestedIds.sorted().joinToString("\n")
        val returnedSorted = returnedIds.sorted().joinToString("\n")
        return MismatchDiagnostics(
            expectedCount = requestedIds.size,
            returnedCount = returnedIds.size,
            missingCount = missing.size,
            extraCount = extra.size,
            duplicateCount = duplicateCount,
            expectedIdsSha256 = sha256Hex(expectedSorted),
            returnedIdsSha256 = sha256Hex(returnedSorted)
        )
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun validateContent(content: String): ValidationResult {
        val root = try {
            JsonParser.parseString(content)
        } catch (_: Exception) {
            return ValidationResult(error = "GLM_MALFORMED_JSON")
        }
        if (!root.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val rootObject = root.asJsonObject
        if (rootObject.keySet() != ROOT_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")

        val version = rootObject.get("schemaVersion")
        if (version == null || !version.isJsonPrimitive || !version.asJsonPrimitive.isNumber ||
            version.asString != SCHEMA_VERSION.toString()
        ) {
            return ValidationResult(error = "GLM_SCHEMA_VERSION_MISMATCH")
        }
        val rawResults = rootObject.get("results")
        if (rawResults == null || !rawResults.isJsonArray) {
            return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        }

        val results = mutableListOf<AnalysisItemResult>()
        for (rawResult in rawResults.asJsonArray) {
            if (!rawResult.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = rawResult.asJsonObject
            if (item.keySet() != RESULT_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")

            val commentId = item.get("commentId").stringValue()
                ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val category = item.get("category").stringValue()
                ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val sentiment = item.get("sentiment").stringValue()
                ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val confidenceElement = item.get("confidence")
            if (confidenceElement == null || !confidenceElement.isJsonPrimitive ||
                !confidenceElement.asJsonPrimitive.isNumber
            ) {
                return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            }
            val confidence = confidenceElement.asDouble
            val summary = item.get("summary").stringValue()
                ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")

            if (commentId.isBlank() || category !in CATEGORIES || sentiment !in SENTIMENTS ||
                !confidence.isFinite() || confidence !in 0.0..1.0 ||
                summary.isBlank() || summary.length > MAX_SUMMARY_LENGTH
            ) {
                return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            }
            results += AnalysisItemResult(commentId, category, sentiment, confidence, summary)
        }
        return ValidationResult(results = results)
    }

    private fun result(
        batchId: String,
        requestedModel: String,
        response: GlmResponse?,
        results: List<AnalysisItemResult>,
        error: String?,
        retryAfterSeconds: Long? = null,
        errorSummary: String? = null
    ): AnalysisBatchResult = AnalysisBatchResult(
        batchId = batchId,
        modelResolved = response?.model?.takeIf { it.isNotBlank() } ?: requestedModel,
        provider = response?.provider,
        inputTokens = response?.usage?.inputTokens,
        outputTokens = response?.usage?.outputTokens,
        costUsd = response?.usage?.costUsd,
        results = results,
        error = error,
        modelRequested = requestedModel,
        generationId = response?.id,
        retryAfterSeconds = retryAfterSeconds,
        errorSummary = errorSummary
    )

    private data class ValidationResult(
        val results: List<AnalysisItemResult>? = null,
        val error: String? = null
    )

    private fun JsonElement?.stringValue(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    companion object {
        const val MODEL = "z-ai/glm-5.3-flash"
        private const val SCHEMA_VERSION = 1
        private const val MAX_SUMMARY_LENGTH = 500
        private val gson = Gson()
        private val CATEGORIES = setOf("feedback", "question", "criticism", "praise", "spam", "other")
        private val SENTIMENTS = setOf("positive", "neutral", "negative", "mixed")
        private val ROOT_KEYS = setOf("schemaVersion", "results")
        private val RESULT_KEYS = setOf("commentId", "category", "sentiment", "confidence", "summary")

        private val responseSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("schemaVersion", "results"),
            "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer", "enum" to listOf(SCHEMA_VERSION)),
                "results" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to listOf("commentId", "category", "sentiment", "confidence", "summary"),
                        "properties" to mapOf(
                            "commentId" to mapOf("type" to "string", "minLength" to 1),
                            "category" to mapOf("type" to "string", "enum" to CATEGORIES.toList()),
                            "sentiment" to mapOf("type" to "string", "enum" to SENTIMENTS.toList()),
                            "confidence" to mapOf("type" to "number", "minimum" to 0, "maximum" to 1),
                            "summary" to mapOf("type" to "string", "minLength" to 1, "maxLength" to MAX_SUMMARY_LENGTH)
                        )
                    )
                )
            )
        )
    }
}
