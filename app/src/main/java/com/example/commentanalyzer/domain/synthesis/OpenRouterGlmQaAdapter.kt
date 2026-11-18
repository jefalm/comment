package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
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

data class QaAnswerResult(
    val answer: String,
    val citedCommentIds: List<String>,
    val insufficientEvidence: Boolean,
    val error: String? = null
)

class OpenRouterGlmQaAdapter(
    private val glmApiService: GlmApiService,
    private val tokenProvider: () -> String?
) {
    suspend fun answerQuestion(
        question: String,
        evidenceComments: List<CommentEntity>
    ): QaAnswerResult {
        if (evidenceComments.isEmpty()) {
            return QaAnswerResult(
                answer = INSUFFICIENT_EVIDENCE_MESSAGE,
                citedCommentIds = emptyList(),
                insufficientEvidence = true
            )
        }
        if (tokenProvider().isNullOrBlank()) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "OPENROUTER_KEY_MISSING"
            )
        }

        val allowlist = evidenceComments.map { it.localId }.toSet()

        val promptPayload = mapOf(
            "question" to question,
            "evidenceComments" to evidenceComments.map {
                mapOf(
                    "commentId" to it.localId,
                    "text" to it.originalText
                )
            }
        )

        val messages = listOf(
            GlmMessage(
                role = "system",
                content = "You are a bounded question-answering assistant for video comments. " +
                    "Answer the user's question strictly and exclusively based on the provided evidence comments. " +
                    "Every cited comment ID in citedCommentIds must come from the provided evidence comments. " +
                    "Do not include comment IDs, bracketed citations, or internal IDs in the answer prose. " +
                    "If the provided comments do not contain enough evidence to answer the question, set insufficientEvidence to true and set answer to '$INSUFFICIENT_EVIDENCE_MESSAGE'. " +
                    "Never speculate or make up facts or quotes."
            ),
            GlmMessage(
                role = "user",
                content = gson.toJson(promptPayload)
            )
        )

        val request = GlmRequest(
            model = MODEL,
            messages = messages,
            maxTokens = 2048,
            responseFormat = GlmResponseFormat(
                jsonSchema = GlmJsonSchema(
                    name = "comment_qa_response",
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
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "NETWORK_ERROR"
            )
        } catch (_: Exception) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_RESPONSE_ERROR"
            )
        }

        if (!response.isSuccessful) {
            val code = response.code()
            val error = when (code) {
                401 -> "HTTP_401_UNAUTHORIZED"
                429 -> "HTTP_429_RATE_LIMIT"
                in 500..599 -> "HTTP_SERVER_ERROR_$code"
                else -> "HTTP_ERROR_$code"
            }
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = error
            )
        }

        val responseBody = response.body()
            ?: return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_EMPTY_RESPONSE"
            )

        val responseText = try {
            responseBody.string()
        } catch (_: IOException) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_RESPONSE_READ_ERROR"
            )
        }

        val responseJson = try {
            JsonParser.parseString(responseText)
        } catch (_: Exception) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_MALFORMED_JSON"
            )
        }
        if (!responseJson.isJsonObject) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_RESPONSE_SCHEMA_MISMATCH"
            )
        }

        val decodedResponse = try {
            gson.fromJson(responseJson, GlmResponse::class.java)
        } catch (_: Exception) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_RESPONSE_SCHEMA_MISMATCH"
            )
        } ?: return QaAnswerResult(
            answer = "",
            citedCommentIds = emptyList(),
            insufficientEvidence = false,
            error = "GLM_EMPTY_RESPONSE"
        )

        return validateAndParseQaResponse(decodedResponse, allowlist)
    }

    private fun validateAndParseQaResponse(
        response: GlmResponse,
        allowlist: Set<String>
    ): QaAnswerResult {
        val choices = response.choices
        if (choices.isNullOrEmpty() || choices.size != 1) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_MISSING_RESPONSE_CONTENT"
            )
        }
        val choice = choices.single()
        if (choice.finishReason == "length") {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_OUTPUT_TRUNCATED"
            )
        }
        val content = choice.message?.content
        if (content.isNullOrBlank()) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_MISSING_RESPONSE_CONTENT"
            )
        }

        val root = try {
            JsonParser.parseString(content)
        } catch (_: Exception) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_MALFORMED_JSON"
            )
        }

        if (!root.isJsonObject) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_SCHEMA_MISMATCH"
            )
        }

        val obj = root.asJsonObject
        if (obj.keySet() != ROOT_KEYS) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_SCHEMA_MISMATCH"
            )
        }

        val insufficientEvidenceElement = obj.get("insufficientEvidence")
        if (insufficientEvidenceElement == null ||
            !insufficientEvidenceElement.isJsonPrimitive ||
            !insufficientEvidenceElement.asJsonPrimitive.isBoolean
        ) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_SCHEMA_MISMATCH"
            )
        }
        val insufficientEvidence = insufficientEvidenceElement.asBoolean

        if (insufficientEvidence) {
            return QaAnswerResult(
                answer = INSUFFICIENT_EVIDENCE_MESSAGE,
                citedCommentIds = emptyList(),
                insufficientEvidence = true
            )
        }

        val rawAnswer = obj.get("answer").stringValue()
            ?: return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_SCHEMA_MISMATCH"
            )

        val rawCited = obj.get("citedCommentIds")
        if (rawCited == null || !rawCited.isJsonArray) {
            return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_SCHEMA_MISMATCH"
            )
        }

        val citedIds = mutableListOf<String>()
        for (item in rawCited.asJsonArray) {
            val id = item.stringValue() ?: return QaAnswerResult(
                answer = "",
                citedCommentIds = emptyList(),
                insufficientEvidence = false,
                error = "GLM_SCHEMA_MISMATCH"
            )
            if (id !in allowlist) {
                // Fail closed on unknown ID
                return QaAnswerResult(
                    answer = INSUFFICIENT_EVIDENCE_MESSAGE,
                    citedCommentIds = emptyList(),
                    insufficientEvidence = true,
                    error = "QA_UNKNOWN_EVIDENCE_ID"
                )
            }
            citedIds.add(id)
        }

        val cleanAnswer = sanitizeProse(rawAnswer, allowlist)
        return QaAnswerResult(
            answer = cleanAnswer,
            citedCommentIds = citedIds,
            insufficientEvidence = false
        )
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

    private fun JsonElement?.stringValue(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    companion object {
        const val MODEL = "z-ai/glm-5.3-flash"
        const val INSUFFICIENT_EVIDENCE_MESSAGE = "Not enough evidence in the comments."
        private val gson = Gson()

        private val ROOT_KEYS = setOf("answer", "citedCommentIds", "insufficientEvidence")

        private val responseSchema: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to ROOT_KEYS.toList(),
            "properties" to mapOf(
                "answer" to mapOf("type" to "string"),
                "citedCommentIds" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string")
                ),
                "insufficientEvidence" to mapOf("type" to "boolean")
            )
        )
    }
}
