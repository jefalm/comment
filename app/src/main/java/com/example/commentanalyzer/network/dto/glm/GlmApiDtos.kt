package com.example.commentanalyzer.network.dto.glm

data class GlmRequest(
    val model: String = "z-ai/glm-5.3-flash",
    val messages: List<GlmMessage>,
    val temperature: Double = 0.0,
    val maxTokens: Int = 4096,
    val responseFormat: GlmResponseFormat? = null
)

data class GlmMessage(
    val role: String,
    val content: String
)

data class GlmResponseFormat(
    val type: String = "json_schema",
    val jsonSchema: Map<String, Any>? = null
)

data class GlmResponse(
    val model: String,
    val provider: String?,
    val usage: GlmUsage?,
    val choices: List<GlmChoice>?
)

data class GlmUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?
)

data class GlmChoice(
    val message: GlmMessage?
)
