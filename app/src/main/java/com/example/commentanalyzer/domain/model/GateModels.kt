package com.example.commentanalyzer.domain.model

data class GateInput(
    val commentId: String,
    val text: String,
    val author: String?,
    val videoId: String?
)

data class GateConfig(
    val configVersion: Long,
    val model: String,
    val rejectThreshold: Double,
    val passThreshold: Double
)

data class GateItemResult(
    val commentId: String,
    val questionKey: String,
    val probability: Double,
    val rawOutput: String?
)

data class GateBatchResult(
    val batchId: String,
    val modelResolved: String,
    val provider: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
    val results: List<GateItemResult>,
    val error: String?
)
