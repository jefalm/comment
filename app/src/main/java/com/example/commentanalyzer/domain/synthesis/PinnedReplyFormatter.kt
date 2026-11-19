package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.domain.model.SynthesisResult

data class PinnedReplyResult(
    val text: String,
    val supportingEvidenceCommentIds: List<String>
)

object PinnedReplyFormatter {

    fun format(
        synthesis: SynthesisResult,
        videoTitle: String? = null
    ): PinnedReplyResult {
        val supportingIds = mutableListOf<String>()

        val blindSpots = synthesis.blindSpotsAndCorrections.take(2)
        blindSpots.forEach { supportingIds.addAll(it.citedCommentIds) }

        val lines = mutableListOf<String>()

        val greeting = if (!videoTitle.isNullOrBlank()) {
            "Thank you everyone for the great comments and discussion on \"$videoTitle\"!"
        } else {
            "Thank you everyone for the great comments and discussion on this video!"
        }
        lines.add(greeting)
        lines.add("")

        if (synthesis.executiveSummary.isNotBlank()) {
            val cleanSummary = stripCitationArtifacts(synthesis.executiveSummary)
            lines.add("Quick recap:")
            lines.add(cleanSummary)
            lines.add("")
        }

        if (blindSpots.isNotEmpty()) {
            lines.add("Clarifications & corrections from the community:")
            blindSpots.forEach { b ->
                lines.add("• ${stripCitationArtifacts(b.claimOrCorrection)}")
            }
            lines.add("")
        }

        lines.add("Keep the feedback and questions coming!")

        val draftText = lines.joinToString("\n").trim()

        return PinnedReplyResult(
            text = draftText,
            supportingEvidenceCommentIds = supportingIds.distinct()
        )
    }

    private fun stripCitationArtifacts(text: String): String {
        return text
            .replace(Regex("""\[\s*c-[^]]+\s*]"""), "")
            .replace(Regex("""\(\s*c-[^)]+\s*\)"""), "")
            .replace(Regex("""\[\s*[a-f0-9-]{8,}\s*]"""), "")
            .replace(Regex("""\s+([.,;:!?])"""), "$1")
            .replace(Regex(""" {2,}"""), " ")
            .trim()
    }
}
