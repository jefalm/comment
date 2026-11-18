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

        val questions = synthesis.questionsAndRequests.take(2)
        questions.forEach { supportingIds.addAll(it.citedCommentIds) }

        val blindSpots = synthesis.blindSpotsAndCorrections.take(2)
        blindSpots.forEach { supportingIds.addAll(it.citedCommentIds) }

        val followUps = synthesis.recommendedFollowUps.take(1)
        followUps.forEach { supportingIds.addAll(it.citedCommentIds) }

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

        if (questions.isNotEmpty()) {
            lines.add("Top questions & requested topics:")
            questions.forEach { q ->
                lines.add("• ${stripCitationArtifacts(q.questionOrRequest)}")
            }
            lines.add("")
        }

        if (blindSpots.isNotEmpty()) {
            lines.add("Clarifications & corrections from the community:")
            blindSpots.forEach { b ->
                lines.add("• ${stripCitationArtifacts(b.claimOrCorrection)}")
            }
            lines.add("")
        }

        if (followUps.isNotEmpty()) {
            lines.add("Next steps & what's coming next:")
            followUps.forEach { f ->
                lines.add("• ${stripCitationArtifacts(f.action)} (${stripCitationArtifacts(f.rationale)})")
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
