package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.domain.analysis.SynthesisContext
import com.example.commentanalyzer.domain.analysis.SynthesisDeepAnalyzer
import com.example.commentanalyzer.domain.model.SynthesisActionableFollowUp
import com.example.commentanalyzer.domain.model.SynthesisAgreement
import com.example.commentanalyzer.domain.model.SynthesisBatchResult
import com.example.commentanalyzer.domain.model.SynthesisContrastingPerspective
import com.example.commentanalyzer.domain.model.SynthesisNotableInsight
import com.example.commentanalyzer.domain.model.SynthesisResult
import com.example.commentanalyzer.domain.model.SynthesisTheme
import com.example.commentanalyzer.network.dto.glm.GlmJsonSchema
import com.example.commentanalyzer.network.dto.glm.GlmMessage
import com.example.commentanalyzer.network.dto.glm.GlmPlugin
import com.example.commentanalyzer.network.dto.glm.GlmProvider
import com.example.commentanalyzer.network.dto.glm.GlmReasoning
import com.example.commentanalyzer.network.dto.glm.GlmRequest
import com.example.commentanalyzer.network.dto.glm.GlmResponse
import com.example.commentanalyzer.network.dto.glm.GlmResponseFormat
import com.example.commentanalyzer.network.glm.GlmApiService
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException

class OpenRouterGlmSynthesisAdapter(
    private val glmApiService: GlmApiService,
    private val tokenProvider: () -> String?
) : SynthesisDeepAnalyzer {

    override suspend fun synthesize(context: SynthesisContext): SynthesisBatchResult {
        val requestedModel = MODEL
        val synthesisId = "glm-synth-${System.currentTimeMillis()}"
        if (context.evidenceItems.isEmpty()) {
            return result(synthesisId, requestedModel, null, null, "GLM_EMPTY_EVIDENCE")
        }
        if (tokenProvider().isNullOrBlank()) {
            return result(synthesisId, requestedModel, null, null, "OPENROUTER_KEY_MISSING")
        }

        val evidenceIds = context.evidenceItems.map { it.commentId }.toSet()

        val promptPayload = mapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "authoritativeContext" to mapOf(
                "totalRelevantComments" to context.totalRelevantComments,
                "categoryDistribution" to context.categoryDistribution,
                "sentimentDistribution" to context.sentimentDistribution
            ),
            "evidenceComments" to context.evidenceItems.map {
                mapOf(
                    "commentId" to it.commentId,
                    "text" to it.text,
                    "category" to it.category,
                    "sentiment" to it.sentiment,
                    "likeCount" to it.likeCount,
                    "categoryConfidence" to it.categoryConfidence,
                    "sentimentConfidence" to it.sentimentConfidence
                )
            }
        )

        val request = GlmRequest(
            model = requestedModel,
            messages = listOf(
                GlmMessage(
                    role = "system",
                    content = "You are a bounded cross-comment synthesis engine. JEV owns per-comment classifications. " +
                        "Do not recalculate totals. Use only the provided authoritative category and sentiment distributions, " +
                        "and cite comment IDs strictly from the supplied evidence comments. Return schemaVersion 2."
                ),
                GlmMessage(
                    role = "user",
                    content = gson.toJson(promptPayload)
                )
            ),
            responseFormat = GlmResponseFormat(
                jsonSchema = GlmJsonSchema(
                    name = "cross_comment_synthesis",
                    strict = true,
                    schema = responseSchema
                )
            ),
            provider = GlmProvider(requireParameters = true),
            reasoning = GlmReasoning(effort = "low", exclude = true),
            plugins = listOf(GlmPlugin(id = "response-healing"))
        )

        val response = try {
            glmApiService.createChatCompletion(request)
        } catch (_: IOException) {
            return result(synthesisId, requestedModel, null, null, "NETWORK_ERROR")
        } catch (_: Exception) {
            return result(synthesisId, requestedModel, null, null, "GLM_RESPONSE_ERROR")
        }

        if (!response.isSuccessful) {
            val code = response.code()
            val error = when (code) {
                401 -> "HTTP_401_UNAUTHORIZED"
                429 -> "HTTP_429_RATE_LIMIT"
                in 500..599 -> "HTTP_SERVER_ERROR_$code"
                else -> "HTTP_ERROR_$code"
            }
            return result(synthesisId, requestedModel, null, null, error)
        }

        val responseBody = response.body()
            ?: return result(synthesisId, requestedModel, null, null, "GLM_EMPTY_RESPONSE")
        val responseText = try {
            responseBody.string()
        } catch (_: IOException) {
            return result(synthesisId, requestedModel, null, null, "GLM_RESPONSE_READ_ERROR")
        }

        val responseJson = try {
            JsonParser.parseString(responseText)
        } catch (_: Exception) {
            return result(synthesisId, requestedModel, null, null, "GLM_MALFORMED_JSON")
        }
        if (!responseJson.isJsonObject) {
            return result(synthesisId, requestedModel, null, null, "GLM_RESPONSE_SCHEMA_MISMATCH")
        }
        val decodedResponse = try {
            gson.fromJson(responseJson, GlmResponse::class.java)
        } catch (_: Exception) {
            return result(synthesisId, requestedModel, null, null, "GLM_RESPONSE_SCHEMA_MISMATCH")
        } ?: return result(synthesisId, requestedModel, null, null, "GLM_EMPTY_RESPONSE")

        return analyzeSynthesisResponse(synthesisId, requestedModel, evidenceIds, decodedResponse)
    }

    private fun analyzeSynthesisResponse(
        synthesisId: String,
        requestedModel: String,
        evidenceIds: Set<String>,
        response: GlmResponse
    ): SynthesisBatchResult {
        val usage = response.usage
        if ((usage?.inputTokens != null && usage.inputTokens < 0) ||
            (usage?.outputTokens != null && usage.outputTokens < 0) ||
            (usage?.costUsd != null && (!usage.costUsd.isFinite() || usage.costUsd < 0.0))
        ) {
            return result(synthesisId, requestedModel, response, null, "GLM_INVALID_USAGE")
        }

        val choices = response.choices
        if (choices.isNullOrEmpty() || choices.size != 1) {
            return result(synthesisId, requestedModel, response, null, "GLM_MISSING_RESPONSE_CONTENT")
        }
        val choice = choices.single()
        if (choice.finishReason == "length") {
            return result(synthesisId, requestedModel, response, null, "GLM_OUTPUT_TRUNCATED")
        }
        val content = choice.message?.content
        if (content.isNullOrBlank()) {
            return result(synthesisId, requestedModel, response, null, "GLM_MISSING_RESPONSE_CONTENT")
        }

        val validation = validateSynthesisContent(content, evidenceIds)
        if (validation.error != null) {
            return result(synthesisId, requestedModel, response, null, validation.error)
        }

        return result(synthesisId, requestedModel, response, validation.synthesis, null)
    }

    private fun validateSynthesisContent(content: String, evidenceIds: Set<String>): ValidationResult {
        val root = try {
            JsonParser.parseString(content)
        } catch (_: Exception) {
            return ValidationResult(error = "GLM_MALFORMED_JSON")
        }
        if (!root.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val obj = root.asJsonObject
        if (obj.keySet() != ROOT_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")

        val version = obj.get("schemaVersion")
        if (version == null || !version.isJsonPrimitive || !version.asJsonPrimitive.isNumber ||
            version.asInt != SCHEMA_VERSION
        ) {
            return ValidationResult(error = "GLM_SCHEMA_VERSION_MISMATCH")
        }

        val execSummary = obj.get("executiveSummary").stringValue()
            ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        if (execSummary.isBlank()) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")

        // Themes
        val rawThemes = obj.get("themes")
        if (rawThemes == null || !rawThemes.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val themes = mutableListOf<SynthesisTheme>()
        for (el in rawThemes.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != THEME_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val title = item.get("title").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val desc = item.get("description").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            themes += SynthesisTheme(title, desc, cited)
        }

        // Agreements
        val rawAgreements = obj.get("agreements")
        if (rawAgreements == null || !rawAgreements.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val agreements = mutableListOf<SynthesisAgreement>()
        for (el in rawAgreements.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != AGREEMENT_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val topic = item.get("topic").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val desc = item.get("description").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            agreements += SynthesisAgreement(topic, desc, cited)
        }

        // Contrasting Perspectives
        val rawContrasting = obj.get("contrastingPerspectives")
        if (rawContrasting == null || !rawContrasting.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val contrasting = mutableListOf<SynthesisContrastingPerspective>()
        for (el in rawContrasting.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != CONTRASTING_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val pA = item.get("perspectiveA").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val pB = item.get("perspectiveB").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val desc = item.get("description").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            contrasting += SynthesisContrastingPerspective(pA, pB, desc, cited)
        }

        // Notable Insights
        val rawInsights = obj.get("notableInsights")
        if (rawInsights == null || !rawInsights.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val insights = mutableListOf<SynthesisNotableInsight>()
        for (el in rawInsights.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != INSIGHT_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val ins = item.get("insight").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            insights += SynthesisNotableInsight(ins, cited)
        }

        // Actionable Follow Ups
        val rawFollowUps = obj.get("actionableFollowUps")
        if (rawFollowUps == null || !rawFollowUps.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val followUps = mutableListOf<SynthesisActionableFollowUp>()
        for (el in rawFollowUps.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != FOLLOWUP_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val action = item.get("action").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val rationale = item.get("rationale").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            followUps += SynthesisActionableFollowUp(action, rationale, cited)
        }

        val synthesisResult = SynthesisResult(
            schemaVersion = SCHEMA_VERSION,
            executiveSummary = execSummary,
            themes = themes,
            agreements = agreements,
            contrastingPerspectives = contrasting,
            notableInsights = insights,
            actionableFollowUps = followUps
        )
        return ValidationResult(synthesis = synthesisResult)
    }

    private fun parseCitedIds(element: JsonElement?, evidenceIds: Set<String>): List<String>? {
        if (element == null || !element.isJsonArray) return null
        val list = mutableListOf<String>()
        for (idEl in element.asJsonArray) {
            val id = idEl.stringValue() ?: return null
            if (id !in evidenceIds) return null
            list += id
        }
        return list
    }

    private fun result(
        synthesisId: String,
        requestedModel: String,
        response: GlmResponse?,
        synthesis: SynthesisResult?,
        error: String?
    ): SynthesisBatchResult = SynthesisBatchResult(
        synthesisId = synthesisId,
        importId = "",
        configVersion = 0L,
        modelResolved = response?.model?.takeIf { it.isNotBlank() } ?: requestedModel,
        provider = response?.provider,
        inputTokens = response?.usage?.inputTokens,
        outputTokens = response?.usage?.outputTokens,
        costUsd = response?.usage?.costUsd,
        synthesis = synthesis,
        error = error,
        modelRequested = requestedModel,
        generationId = response?.id
    )

    private data class ValidationResult(
        val synthesis: SynthesisResult? = null,
        val error: String? = null
    )

    private fun JsonElement?.stringValue(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    companion object {
        const val MODEL = "z-ai/glm-5.3-flash"
        private const val SCHEMA_VERSION = 2
        private val gson = Gson()

        private val ROOT_KEYS = setOf(
            "schemaVersion",
            "executiveSummary",
            "themes",
            "agreements",
            "contrastingPerspectives",
            "notableInsights",
            "actionableFollowUps"
        )
        private val THEME_KEYS = setOf("title", "description", "citedCommentIds")
        private val AGREEMENT_KEYS = setOf("topic", "description", "citedCommentIds")
        private val CONTRASTING_KEYS = setOf("perspectiveA", "perspectiveB", "description", "citedCommentIds")
        private val INSIGHT_KEYS = setOf("insight", "citedCommentIds")
        private val FOLLOWUP_KEYS = setOf("action", "rationale", "citedCommentIds")

        private val responseSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to ROOT_KEYS.toList(),
            "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer", "enum" to listOf(SCHEMA_VERSION)),
                "executiveSummary" to mapOf("type" to "string", "minLength" to 1),
                "themes" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to THEME_KEYS.toList(),
                        "properties" to mapOf(
                            "title" to mapOf("type" to "string", "minLength" to 1),
                            "description" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "agreements" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to AGREEMENT_KEYS.toList(),
                        "properties" to mapOf(
                            "topic" to mapOf("type" to "string", "minLength" to 1),
                            "description" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "contrastingPerspectives" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to CONTRASTING_KEYS.toList(),
                        "properties" to mapOf(
                            "perspectiveA" to mapOf("type" to "string", "minLength" to 1),
                            "perspectiveB" to mapOf("type" to "string", "minLength" to 1),
                            "description" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "notableInsights" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to INSIGHT_KEYS.toList(),
                        "properties" to mapOf(
                            "insight" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "actionableFollowUps" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to FOLLOWUP_KEYS.toList(),
                        "properties" to mapOf(
                            "action" to mapOf("type" to "string", "minLength" to 1),
                            "rationale" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                )
            )
        )
    }
}
