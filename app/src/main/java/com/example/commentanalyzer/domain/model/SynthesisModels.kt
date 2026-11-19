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

data class SynthesisThemeV3(
    val title: String,
    val takeaway: String,
    val representativeCommentId: String,
    val citedCommentIds: List<String>,
    val representativeQuote: String? = null,
    val representativeAuthor: String? = null
) {
    val evidenceCount: Int get() = citedCommentIds.distinct().size
}

data class SynthesisDebateV3(
    val topic: String,
    val sideASummary: String,
    val sideBSummary: String,
    val citedCommentIds: List<String>
) {
    val evidenceCount: Int get() = citedCommentIds.distinct().size
}

data class SynthesisBlindSpotV3(
    val claimOrCorrection: String,
    val citedCommentIds: List<String>
) {
    val evidenceCount: Int get() = citedCommentIds.distinct().size
}

data class SynthesisInsiderV3(
    val perspective: String,
    val citedCommentIds: List<String>
) {
    val evidenceCount: Int get() = citedCommentIds.distinct().size
}

data class SynthesisResult(
    val schemaVersion: Int,
    val executiveSummary: String,
    val themes: List<SynthesisTheme> = emptyList(),
    val agreements: List<SynthesisAgreement> = emptyList(),
    val contrastingPerspectives: List<SynthesisContrastingPerspective> = emptyList(),
    val notableInsights: List<SynthesisNotableInsight> = emptyList(),
    val themesV3: List<SynthesisThemeV3> = emptyList(),
    val debates: List<SynthesisDebateV3> = emptyList(),
    val blindSpotsAndCorrections: List<SynthesisBlindSpotV3> = emptyList(),
    val insiderPerspectives: List<SynthesisInsiderV3> = emptyList()
) {
    fun allCitedCommentIds(): Set<String> {
        val ids = mutableSetOf<String>()
        themesV3.forEach { ids.addAll(it.citedCommentIds) }
        debates.forEach { ids.addAll(it.citedCommentIds) }
        blindSpotsAndCorrections.forEach { ids.addAll(it.citedCommentIds) }
        insiderPerspectives.forEach { ids.addAll(it.citedCommentIds) }
        themes.forEach { ids.addAll(it.citedCommentIds) }
        agreements.forEach { ids.addAll(it.citedCommentIds) }
        contrastingPerspectives.forEach { ids.addAll(it.citedCommentIds) }
        notableInsights.forEach { ids.addAll(it.citedCommentIds) }
        return ids
    }
}

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
