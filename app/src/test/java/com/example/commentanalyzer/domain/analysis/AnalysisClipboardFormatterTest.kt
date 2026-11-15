package com.example.commentanalyzer.domain.analysis

import com.example.commentanalyzer.domain.model.SynthesisAgreement
import com.example.commentanalyzer.domain.model.SynthesisContrastingPerspective
import com.example.commentanalyzer.domain.model.SynthesisNotableInsight
import com.example.commentanalyzer.domain.model.SynthesisResult
import com.example.commentanalyzer.domain.model.SynthesisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisClipboardFormatterTest {

    private val sampleSynthesis = SynthesisResult(
        schemaVersion = 1,
        executiveSummary = "This is a great discussion about AI advancements.",
        themes = listOf(
            SynthesisTheme(
                title = "Model Capabilities",
                description = "Users are impressed by the speed and reasoning capabilities.",
                citedCommentIds = listOf("c101", "c102")
            ),
            SynthesisTheme(
                title = "Pricing Concerns",
                description = "Some users find the token pricing slightly high.",
                citedCommentIds = listOf("c103")
            )
        ),
        agreements = listOf(
            SynthesisAgreement(
                topic = "Tool accuracy",
                description = "Everyone agrees tool calling is accurate.",
                citedCommentIds = listOf("c104")
            )
        ),
        contrastingPerspectives = listOf(
            SynthesisContrastingPerspective(
                perspectiveA = "Prefer local LLMs for privacy",
                perspectiveB = "Prefer cloud APIs for speed",
                description = "Debate between privacy-first workflows and cloud compute convenience.",
                citedCommentIds = listOf("c105", "c106")
            )
        ),
        notableInsights = listOf(
            SynthesisNotableInsight(
                insight = "Batching requests significantly cuts down latency.",
                citedCommentIds = listOf("c107")
            ),
            SynthesisNotableInsight(
                insight = "Prompt engineering still matters for structured output.",
                citedCommentIds = listOf("c108")
            )
        )
    )

    @Test
    fun titleIsIncluded() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Android Compose Mastery",
            publisher = null,
            videoId = null,
            synthesis = sampleSynthesis
        )
        assertTrue(result.contains("Title\nAndroid Compose Mastery"))
    }

    @Test
    fun publisherIsIncluded() {
        val result = formatAnalysisForClipboard(
            videoTitle = null,
            publisher = "Tech Channel",
            videoId = null,
            synthesis = sampleSynthesis
        )
        assertTrue(result.contains("Publisher\nTech Channel"))
    }

    @Test
    fun canonicalYouTubeLinkIsCorrect() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Some Video",
            publisher = "Creator",
            videoId = "dQw4w9WgXcQ",
            synthesis = sampleSynthesis
        )
        assertTrue(result.contains("YouTube\nhttps://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun executiveSummaryIsIncluded() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Title",
            publisher = "Pub",
            videoId = "vid123",
            synthesis = sampleSynthesis
        )
        assertTrue(result.contains("EXECUTIVE SUMMARY\n\nThis is a great discussion about AI advancements."))
    }

    @Test
    fun keyThemesAreOrderedAndNumbered() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Title",
            publisher = "Pub",
            videoId = "vid123",
            synthesis = sampleSynthesis
        )
        val expectedThemes = "KEY THEMES\n\n1. Model Capabilities\nUsers are impressed by the speed and reasoning capabilities.\n\n2. Pricing Concerns\nSome users find the token pricing slightly high."
        assertTrue(result.contains(expectedThemes))
    }

    @Test
    fun agreementsAreOrderedAndNumbered() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Title",
            publisher = "Pub",
            videoId = "vid123",
            synthesis = sampleSynthesis
        )
        val expectedAgreements = "AGREEMENTS\n\n1. Tool accuracy\nEveryone agrees tool calling is accurate."
        assertTrue(result.contains(expectedAgreements))
    }

    @Test
    fun contrastingPerspectivesAreClearlyFormatted() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Title",
            publisher = "Pub",
            videoId = "vid123",
            synthesis = sampleSynthesis
        )
        val expectedPerspectives = "CONTRASTING PERSPECTIVES\n\n1.\nPerspective A: Prefer local LLMs for privacy\nPerspective B: Prefer cloud APIs for speed\nDebate between privacy-first workflows and cloud compute convenience."
        assertTrue(result.contains(expectedPerspectives))
    }

    @Test
    fun notableInsightsAreOrderedAndNumbered() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Title",
            publisher = "Pub",
            videoId = "vid123",
            synthesis = sampleSynthesis
        )
        val expectedInsights = "NOTABLE INSIGHTS\n\n1. Batching requests significantly cuts down latency.\n\n2. Prompt engineering still matters for structured output."
        assertTrue(result.contains(expectedInsights))
    }

    @Test
    fun internalCommentAndCitationIdsAreNotCopied() {
        val result = formatAnalysisForClipboard(
            videoTitle = "Android Dev",
            publisher = "Google",
            videoId = "abc12345678",
            synthesis = sampleSynthesis
        )
        assertFalse(result.contains("c101"))
        assertFalse(result.contains("c102"))
        assertFalse(result.contains("c103"))
        assertFalse(result.contains("c104"))
        assertFalse(result.contains("c105"))
        assertFalse(result.contains("c106"))
        assertFalse(result.contains("c107"))
        assertFalse(result.contains("c108"))
        assertFalse(result.contains("citedCommentIds"))
        assertFalse(result.contains("schemaVersion"))
        assertFalse(result.contains("View evidence"))
    }

    @Test
    fun missingTitleAndPublisherAreOmittedCleanly() {
        val result = formatAnalysisForClipboard(
            videoTitle = null,
            publisher = "",
            videoId = "xyz98765432",
            synthesis = sampleSynthesis
        )
        assertFalse(result.contains("Title"))
        assertFalse(result.contains("Publisher"))
        assertTrue(result.startsWith("VIDEO ANALYSIS\n\nYouTube\nhttps://www.youtube.com/watch?v=xyz98765432\n\nEXECUTIVE SUMMARY"))
    }

    @Test
    fun exactClipboardOutputMatchesFormatSpecification() {
        val expected = """
VIDEO ANALYSIS

Title
Android Compose Mastery

Publisher
Google Developers

YouTube
https://www.youtube.com/watch?v=abc12345678

EXECUTIVE SUMMARY

This is a great discussion about AI advancements.

KEY THEMES

1. Model Capabilities
Users are impressed by the speed and reasoning capabilities.

2. Pricing Concerns
Some users find the token pricing slightly high.

AGREEMENTS

1. Tool accuracy
Everyone agrees tool calling is accurate.

CONTRASTING PERSPECTIVES

1.
Perspective A: Prefer local LLMs for privacy
Perspective B: Prefer cloud APIs for speed
Debate between privacy-first workflows and cloud compute convenience.

NOTABLE INSIGHTS

1. Batching requests significantly cuts down latency.

2. Prompt engineering still matters for structured output.
        """.trimIndent()

        val actual = formatAnalysisForClipboard(
            videoTitle = "Android Compose Mastery",
            publisher = "Google Developers",
            videoId = "abc12345678",
            synthesis = sampleSynthesis
        )

        assertEquals(expected, actual)
    }
}
