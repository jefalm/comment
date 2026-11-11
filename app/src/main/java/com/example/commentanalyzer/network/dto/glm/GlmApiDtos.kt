package com.example.commentanalyzer.network.dto.glm

import com.google.gson.annotations.SerializedName

data class GlmRequest(
    val model: String = "z-ai/glm-5.3-flash",
    val messages: List<GlmMessage>,
    val temperature: Double = 0.0,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    @SerializedName("response_format")
    val responseFormat: GlmResponseFormat? = null,
    val provider: GlmProvider = GlmProvider(),
    val reasoning: GlmReasoning = GlmReasoning()
)

data class GlmMessage(
    val role: String,
    val content: String
)

data class GlmResponseFormat(
    val type: String = "json_schema",
    @SerializedName("json_schema")
    val jsonSchema: GlmJsonSchema? = null
)

data class GlmJsonSchema(
    val name: String,
    val strict: Boolean,
    val schema: Map<String, Any>
)

data class GlmProvider(
    @SerializedName("require_parameters")
    val requireParameters: Boolean = true
)

data class GlmReasoning(
    val effort: String = "low",
    val exclude: Boolean = true
)

data class GlmResponse(
    val id: String?,
    val model: String?,
    val provider: String?,
    val usage: GlmUsage?,
    val choices: List<GlmChoice>?
)

data class GlmUsage(
    @SerializedName("input_tokens")
    val inputTokens: Long?,
    @SerializedName("output_tokens")
    val outputTokens: Long?,
    @SerializedName("cost")
    val costUsd: Double?
)

data class GlmChoice(
    val message: GlmMessage?
)
