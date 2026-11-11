package com.example.commentanalyzer.domain.model

data class GateQuestionContract(
    val questionKey: String = "comment_quality_gate",
    val instructions: String = "Evaluate whether this YouTube comment is constructive and acceptable for analysis.",
    val trueCriterion: String = "Acceptable, constructive, or relevant comment.",
    val falseCriterion: String = "Spam, toxic, incoherent, or irrelevant comment."
)

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
    val passThreshold: Double,
    val questionContract: GateQuestionContract = GateQuestionContract()
)

data class GateItemResult(
    val commentId: String,
    val questionKey: String,
    val probability: Double,
    val rawOutput: String?,
    val category: String? = null,
    val categoryConfidence: Double? = null,
    val sentiment: String? = null,
    val sentimentConfidence: Double? = null
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
