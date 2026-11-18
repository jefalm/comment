package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.domain.analysis.SynthesisContext
import com.example.commentanalyzer.domain.analysis.SynthesisDeepAnalyzer
import com.example.commentanalyzer.domain.model.SynthesisBatchResult
import com.example.commentanalyzer.domain.model.SynthesisBlindSpotV3
import com.example.commentanalyzer.domain.model.SynthesisDebateV3
import com.example.commentanalyzer.domain.model.SynthesisFollowUpV3
import com.example.commentanalyzer.domain.model.SynthesisInsiderV3
import com.example.commentanalyzer.domain.model.SynthesisQuestionV3
import com.example.commentanalyzer.domain.model.SynthesisResult
import com.example.commentanalyzer.domain.model.SynthesisThemeV3
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

        val targetLanguage = context.targetLanguage
        val languageInstruction = when (targetLanguage) {
            "pt-BR" -> "Write every human-readable synthesis field (executiveSummary, themes[].title, themes[].takeaway, " +
                "debates[].topic, debates[].sideASummary, debates[].sideBSummary, " +
                "questionsAndRequests[].questionOrRequest, blindSpotsAndCorrections[].claimOrCorrection, " +
                "insiderPerspectives[].perspective, recommendedFollowUps[].action, recommendedFollowUps[].rationale) " +
                "in Brazilian Portuguese. Keep JSON keys, IDs, schema values, and machine-readable structure unchanged."
            else -> "Write every human-readable synthesis field (executiveSummary, themes[].title, themes[].takeaway, " +
                "debates[].topic, debates[].sideASummary, debates[].sideBSummary, " +
                "questionsAndRequests[].questionOrRequest, blindSpotsAndCorrections[].claimOrCorrection, " +
                "insiderPerspectives[].perspective, recommendedFollowUps[].action, recommendedFollowUps[].rationale) " +
                "in English. Keep JSON keys, IDs, schema values, and machine-readable structure unchanged."
        }

        val promptPayload = mapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "targetLanguage" to targetLanguage,
            "evidenceComments" to context.evidenceItems.map {
                val item = mutableMapOf<String, Any>(
                    "commentId" to it.commentId,
                    "text" to it.text
                )
                it.likeCount?.let { likes -> item["likeCount"] = likes }
                item
            }
        )

        val messages = listOf(
            GlmMessage(
                role = "system",
                content = "You are a bounded cross-comment synthesis engine. " +
                    "Cite comment IDs strictly from the supplied evidence comments. " +
                    "Do not include comment IDs, evidence IDs, UUIDs, bracketed citation IDs, or other internal identifiers in human-readable prose. " +
                    "Keep evidence references only in the dedicated structured citation/evidence fields. " +
                    "For themes, select an exact representativeCommentId from the cited evidence comments; never generate quote text or fake quotes. " +
                    "$languageInstruction Return schemaVersion 3."
            ),
            GlmMessage(
                role = "user",
                content = gson.toJson(promptPayload)
            )
        )

        val estimatedInputTokens = estimateInputTokens(messages)
        val maxTokens = calculateMaxTokens(estimatedInputTokens)

        val request = GlmRequest(
            model = requestedModel,
            messages = messages,
            maxTokens = maxTokens,
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
        val themes = mutableListOf<SynthesisThemeV3>()
        for (el in rawThemes.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != THEME_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val title = item.get("title").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val takeaway = item.get("takeaway").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val repId = item.get("representativeCommentId").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            if (repId !in evidenceIds) return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            themes += SynthesisThemeV3(
                title = sanitizeProse(title, evidenceIds),
                takeaway = sanitizeProse(takeaway, evidenceIds),
                representativeCommentId = repId,
                citedCommentIds = cited
            )
        }

        // Debates
        val rawDebates = obj.get("debates")
        if (rawDebates == null || !rawDebates.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val debates = mutableListOf<SynthesisDebateV3>()
        for (el in rawDebates.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != DEBATE_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val topic = item.get("topic").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val sideA = item.get("sideASummary").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val sideB = item.get("sideBSummary").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            debates += SynthesisDebateV3(
                topic = sanitizeProse(topic, evidenceIds),
                sideASummary = sanitizeProse(sideA, evidenceIds),
                sideBSummary = sanitizeProse(sideB, evidenceIds),
                citedCommentIds = cited
            )
        }

        // Questions & Requests
        val rawQuestions = obj.get("questionsAndRequests")
        if (rawQuestions == null || !rawQuestions.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val questions = mutableListOf<SynthesisQuestionV3>()
        for (el in rawQuestions.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != QUESTION_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val q = item.get("questionOrRequest").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            questions += SynthesisQuestionV3(
                questionOrRequest = sanitizeProse(q, evidenceIds),
                citedCommentIds = cited
            )
        }

        // Blind Spots & Corrections
        val rawBlindSpots = obj.get("blindSpotsAndCorrections")
        if (rawBlindSpots == null || !rawBlindSpots.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val blindSpots = mutableListOf<SynthesisBlindSpotV3>()
        for (el in rawBlindSpots.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != BLIND_SPOT_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val claim = item.get("claimOrCorrection").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            blindSpots += SynthesisBlindSpotV3(
                claimOrCorrection = sanitizeProse(claim, evidenceIds),
                citedCommentIds = cited
            )
        }

        // Insider Perspectives
        val rawInsiders = obj.get("insiderPerspectives")
        if (rawInsiders == null || !rawInsiders.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val insiders = mutableListOf<SynthesisInsiderV3>()
        for (el in rawInsiders.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != INSIDER_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val p = item.get("perspective").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            insiders += SynthesisInsiderV3(
                perspective = sanitizeProse(p, evidenceIds),
                citedCommentIds = cited
            )
        }

        // Recommended Follow-Ups
        val rawFollowUps = obj.get("recommendedFollowUps")
        if (rawFollowUps == null || !rawFollowUps.isJsonArray) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
        val followUps = mutableListOf<SynthesisFollowUpV3>()
        for (el in rawFollowUps.asJsonArray) {
            if (!el.isJsonObject) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val item = el.asJsonObject
            if (item.keySet() != FOLLOW_UP_KEYS) return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val action = item.get("action").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val rationale = item.get("rationale").stringValue() ?: return ValidationResult(error = "GLM_SCHEMA_MISMATCH")
            val cited = parseCitedIds(item.get("citedCommentIds"), evidenceIds) ?: return ValidationResult(error = "GLM_UNKNOWN_EVIDENCE_ID")
            followUps += SynthesisFollowUpV3(
                action = sanitizeProse(action, evidenceIds),
                rationale = sanitizeProse(rationale, evidenceIds),
                citedCommentIds = cited
            )
        }

        val synthesisResult = SynthesisResult(
            schemaVersion = SCHEMA_VERSION,
            executiveSummary = sanitizeProse(execSummary, evidenceIds),
            themesV3 = themes,
            debates = debates,
            questionsAndRequests = questions,
            blindSpotsAndCorrections = blindSpots,
            insiderPerspectives = insiders,
            recommendedFollowUps = followUps
        )
        return ValidationResult(synthesis = synthesisResult)
    }

    private fun sanitizeProse(text: String, evidenceIds: Set<String>): String {
        if (text.isBlank() || evidenceIds.isEmpty()) return text
        var result = text
        for (id in evidenceIds) {
            if (id.isNotEmpty() && result.contains(id)) {
                result = result.replace(Regex("""\s*\[\s*${Regex.escape(id)}\s*]"""), "")
                result = result.replace(Regex("""\s*\(\s*${Regex.escape(id)}\s*\)"""), "")
                result = result.replace(id, "")
            }
        }
        return result.replace(Regex("""\s+([.,;:!?])"""), "$1")
            .replace(Regex(""" {2,}"""), " ")
            .trim()
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
        const val MAX_CONTEXT_TOKENS = 1_048_576
        const val MAX_OUTPUT_TOKENS = 131_072
        const val SCHEMA_VERSION = 3
        private val gson = Gson()

        fun estimateInputTokens(messages: List<GlmMessage>): Int {
            val totalChars = messages.sumOf { it.content.length }
            return (totalChars / 4).coerceAtLeast(1)
        }

        fun calculateMaxTokens(estimatedInputTokens: Int): Int {
            val availableOutput = MAX_CONTEXT_TOKENS - estimatedInputTokens
            return minOf(MAX_OUTPUT_TOKENS, availableOutput.coerceAtLeast(0))
        }

        private val ROOT_KEYS = setOf(
            "schemaVersion",
            "executiveSummary",
            "themes",
            "debates",
            "questionsAndRequests",
            "blindSpotsAndCorrections",
            "insiderPerspectives",
            "recommendedFollowUps"
        )
        private val THEME_KEYS = setOf("title", "takeaway", "representativeCommentId", "citedCommentIds")
        private val DEBATE_KEYS = setOf("topic", "sideASummary", "sideBSummary", "citedCommentIds")
        private val QUESTION_KEYS = setOf("questionOrRequest", "citedCommentIds")
        private val BLIND_SPOT_KEYS = setOf("claimOrCorrection", "citedCommentIds")
        private val INSIDER_KEYS = setOf("perspective", "citedCommentIds")
        private val FOLLOW_UP_KEYS = setOf("action", "rationale", "citedCommentIds")

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
                            "takeaway" to mapOf("type" to "string", "minLength" to 1),
                            "representativeCommentId" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "debates" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to DEBATE_KEYS.toList(),
                        "properties" to mapOf(
                            "topic" to mapOf("type" to "string", "minLength" to 1),
                            "sideASummary" to mapOf("type" to "string", "minLength" to 1),
                            "sideBSummary" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "questionsAndRequests" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to QUESTION_KEYS.toList(),
                        "properties" to mapOf(
                            "questionOrRequest" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "blindSpotsAndCorrections" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to BLIND_SPOT_KEYS.toList(),
                        "properties" to mapOf(
                            "claimOrCorrection" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "insiderPerspectives" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to INSIDER_KEYS.toList(),
                        "properties" to mapOf(
                            "perspective" to mapOf("type" to "string", "minLength" to 1),
                            "citedCommentIds" to mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1))
                        )
                    )
                ),
                "recommendedFollowUps" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to FOLLOW_UP_KEYS.toList(),
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
