package com.example.commentanalyzer.domain.analysis

import com.example.commentanalyzer.domain.model.SynthesisResult

fun formatAnalysisForClipboard(
    videoTitle: String?,
    publisher: String?,
    videoId: String?,
    synthesis: SynthesisResult
): String {
    val blocks = mutableListOf<String>()

    val metadataLines = mutableListOf<String>()
    metadataLines.add("VIDEO ANALYSIS")

    val cleanTitle = videoTitle?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanTitle != null) {
        metadataLines.add("")
        metadataLines.add("Title")
        metadataLines.add(cleanTitle)
    }

    val cleanPublisher = publisher?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanPublisher != null) {
        metadataLines.add("")
        metadataLines.add("Publisher")
        metadataLines.add(cleanPublisher)
    }

    val cleanVideoId = videoId?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanVideoId != null) {
        metadataLines.add("")
        metadataLines.add("YouTube")
        metadataLines.add("https://www.youtube.com/watch?v=$cleanVideoId")
    }

    blocks.add(metadataLines.joinToString("\n"))

    blocks.add("EXECUTIVE SUMMARY\n\n${synthesis.executiveSummary.trim()}")

    if (synthesis.themes.isNotEmpty()) {
        val themeItems = synthesis.themes.mapIndexed { index, theme ->
            "${index + 1}. ${theme.title.trim()}\n${theme.description.trim()}"
        }
        blocks.add("KEY THEMES\n\n${themeItems.joinToString("\n\n")}")
    }

    if (synthesis.agreements.isNotEmpty()) {
        val agreementItems = synthesis.agreements.mapIndexed { index, agreement ->
            "${index + 1}. ${agreement.topic.trim()}\n${agreement.description.trim()}"
        }
        blocks.add("AGREEMENTS\n\n${agreementItems.joinToString("\n\n")}")
    }

    if (synthesis.contrastingPerspectives.isNotEmpty()) {
        val perspectiveItems = synthesis.contrastingPerspectives.mapIndexed { index, p ->
            "${index + 1}.\nPerspective A: ${p.perspectiveA.trim()}\nPerspective B: ${p.perspectiveB.trim()}\n${p.description.trim()}"
        }
        blocks.add("CONTRASTING PERSPECTIVES\n\n${perspectiveItems.joinToString("\n\n")}")
    }

    if (synthesis.notableInsights.isNotEmpty()) {
        val insightItems = synthesis.notableInsights.mapIndexed { index, insight ->
            "${index + 1}. ${insight.insight.trim()}"
        }
        blocks.add("NOTABLE INSIGHTS\n\n${insightItems.joinToString("\n\n")}")
    }

    return blocks.joinToString("\n\n")
}
