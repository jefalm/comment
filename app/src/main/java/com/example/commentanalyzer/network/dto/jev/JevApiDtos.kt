package com.example.commentanalyzer.network.dto.jev

data class JevRequest(
    val model: String = "typesafe/jev-1.14",
    val states: List<JevStateItem>
)

data class JevStateItem(
    val id: String,
    val text: String
)

data class JevResponse(
    val model: String,
    val provider: String?,
    val usage: JevUsage?,
    val results: List<JevResultItem>
)

data class JevUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?
)

data class JevResultItem(
    val id: String,
    val answers: Map<String, Double>
)
