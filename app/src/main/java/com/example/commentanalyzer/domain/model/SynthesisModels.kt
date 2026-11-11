package com.example.commentanalyzer.domain.model

data class SynthesisTheme(
    val title: String,
    val description: String,
    val citedCommentIds: List<String>
)

data class SynthesisAgreement(
    val topic: String,
    val description: String,
    val citedCommentIds: List<String>
)

data class SynthesisContrastingPerspective(
    val perspectiveA: String,
    val perspectiveB: String,
    val description: String,
    val citedCommentIds: List<String>
)

data class SynthesisNotableInsight(
    val insight: String,
    val citedCommentIds: List<String>
)

data class SynthesisActionableFollowUp(
    val action: String,
    val rationale: String,
    val citedCommentIds: List<String>
)

data class SynthesisResult(
    val schemaVersion: Int,
    val executiveSummary: String,
    val themes: List<SynthesisTheme>,
    val agreements: List<SynthesisAgreement>,
    val contrastingPerspectives: List<SynthesisContrastingPerspective>,
    val notableInsights: List<SynthesisNotableInsight>,
    val actionableFollowUps: List<SynthesisActionableFollowUp>
)

data class SynthesisBatchResult(
    val synthesisId: String,
    val importId: String,
    val configVersion: Long,
    val modelResolved: String,
    val provider: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
    val synthesis: SynthesisResult?,
    val error: String?,
    val modelRequested: String?,
    val generationId: String?
)
