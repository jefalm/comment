package com.example.commentanalyzer.network.dto.jev

data class JevRequest(
    val model: String = "typesafe/jev-1.14",
    val state: String,
    val questions: Map<String, JevQuestionItem>
)

data class JevQuestionItem(
    val type: String = "noul",
    val instructions: String,
    val criteria: Any
)

data class JevCriteria(
    val `true`: String,
    val `false`: String
)

data class JevDecisionResponse(
    val id: String?,
    val model: String?,
    val provider: String?,
    val usage: JevUsage?,
    val answers: Map<String, JevAnswerValue>?
)

data class JevAnswerValue(
    val type: String?,
    val noul: Double? = null,
    val choice: String? = null,
    val confidence: Double? = null,
    val probabilities: Map<String, Double>? = null
)

data class JevUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?
)
