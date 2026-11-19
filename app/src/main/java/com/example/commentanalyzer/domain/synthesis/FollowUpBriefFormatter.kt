package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.domain.model.SynthesisResult

object FollowUpBriefFormatter {

    fun format(
        videoTitle: String?,
        channelTitle: String?,
        videoId: String?,
        synthesis: SynthesisResult
    ): String {
        val blocks = mutableListOf<String>()

        val title = videoTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Audience Intelligence Brief"
        blocks.add("# Follow-Up Brief: $title")

        val metaLines = mutableListOf<String>()
        if (!channelTitle.isNullOrBlank()) {
            metaLines.add("**Channel:** ${channelTitle.trim()}")
        }
        if (!videoId.isNullOrBlank()) {
            metaLines.add("**Video URL:** https://www.youtube.com/watch?v=${videoId.trim()}")
        }
        if (metaLines.isNotEmpty()) {
            blocks.add(metaLines.joinToString(" · "))
        }

        // 1. Executive Summary
        blocks.add("## Executive Summary\n\n${synthesis.executiveSummary.trim()}")

        // 2. Themes
        if (synthesis.themesV3.isNotEmpty()) {
            val themeItems = synthesis.themesV3.mapIndexed { index, theme ->
                val sb = StringBuilder()
                sb.append("${index + 1}. **${theme.title.trim()}**\n   ${theme.takeaway.trim()}")
                sb.append("\n   *(Supported by ${theme.evidenceCount} comments)*")
                sb.toString()
            }
            blocks.add("## Themes\n\n${themeItems.joinToString("\n\n")}")
        }

        // 3. Debates
        if (synthesis.debates.isNotEmpty()) {
            val debateItems = synthesis.debates.mapIndexed { index, debate ->
                "${index + 1}. **${debate.topic.trim()}**\n   - **Side A:** ${debate.sideASummary.trim()}\n   - **Side B:** ${debate.sideBSummary.trim()}\n   *(Supported by ${debate.evidenceCount} comments)*"
            }
            blocks.add("## Debates\n\n${debateItems.joinToString("\n\n")}")
        }

        // 4. Blind Spots & Corrections
        if (synthesis.blindSpotsAndCorrections.isNotEmpty()) {
            val bItems = synthesis.blindSpotsAndCorrections.mapIndexed { index, b ->
                "${index + 1}. **${b.claimOrCorrection.trim()}** *(Supported by ${b.evidenceCount} comments)*"
            }
            blocks.add("## Blind Spots & Corrections\n\n${bItems.joinToString("\n\n")}")
        }

        // 5. Insider Perspectives
        if (synthesis.insiderPerspectives.isNotEmpty()) {
            val iItems = synthesis.insiderPerspectives.mapIndexed { index, i ->
                "${index + 1}. **${i.perspective.trim()}** *(Supported by ${i.evidenceCount} comments)*"
            }
            blocks.add("## Insider Perspectives\n\n${iItems.joinToString("\n\n")}")
        }

        return blocks.joinToString("\n\n")
    }
}
