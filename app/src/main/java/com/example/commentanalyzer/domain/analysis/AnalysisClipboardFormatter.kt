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

    // Schema v3 formatting:
    if (synthesis.themesV3.isNotEmpty()) {
        val themeItems = synthesis.themesV3.mapIndexed { index, theme ->
            val sb = StringBuilder()
            sb.append("${index + 1}. ${theme.title.trim()}\n${theme.takeaway.trim()}")
            sb.append("\nSupported by ${theme.evidenceCount} evidence comments")
            sb.toString()
        }
        blocks.add("THEMES\n\n${themeItems.joinToString("\n\n")}")
    }

    if (synthesis.debates.isNotEmpty()) {
        val debateItems = synthesis.debates.mapIndexed { index, debate ->
            "${index + 1}. ${debate.topic.trim()}\nSide A: ${debate.sideASummary.trim()}\nSide B: ${debate.sideBSummary.trim()}\nSupported by ${debate.evidenceCount} evidence comments"
        }
        blocks.add("DEBATES\n\n${debateItems.joinToString("\n\n")}")
    }

    if (synthesis.blindSpotsAndCorrections.isNotEmpty()) {
        val bItems = synthesis.blindSpotsAndCorrections.mapIndexed { index, b ->
            "${index + 1}. ${b.claimOrCorrection.trim()}\nSupported by ${b.evidenceCount} evidence comments"
        }
        blocks.add("BLIND SPOTS & CORRECTIONS\n\n${bItems.joinToString("\n\n")}")
    }

    if (synthesis.insiderPerspectives.isNotEmpty()) {
        val iItems = synthesis.insiderPerspectives.mapIndexed { index, i ->
            "${index + 1}. ${i.perspective.trim()}\nSupported by ${i.evidenceCount} evidence comments"
        }
        blocks.add("INSIDER PERSPECTIVES\n\n${iItems.joinToString("\n\n")}")
    }

    // Schema v2 legacy fallback:
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
