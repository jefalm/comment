package com.example.commentanalyzer.domain.model

data class AnalysisInput(
    val commentId: String,
    val text: String
)

data class AnalysisConfig(
    val configVersion: Long,
    val model: String
)

data class AnalysisItemResult(
    val commentId: String,
    val category: String,
    val sentiment: String,
    val confidence: Double,
    val summary: String
)

data class AnalysisBatchResult(
    val batchId: String,
    val modelResolved: String,
    val provider: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
    val results: List<AnalysisItemResult>,
    val error: String?,
    val modelRequested: String? = null,
    val generationId: String? = null,
    val retryAfterSeconds: Long? = null,
    val errorSummary: String? = null
)
