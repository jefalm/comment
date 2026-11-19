package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.model.SynthesisBlindSpotV3
import com.example.commentanalyzer.domain.model.SynthesisDebateV3
import com.example.commentanalyzer.domain.model.SynthesisInsiderV3
import com.example.commentanalyzer.domain.model.SynthesisResult
import com.example.commentanalyzer.domain.model.SynthesisThemeV3
import com.example.commentanalyzer.network.glm.GlmApiService
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenRouterGlmQaAdapterTest {

    private lateinit var server: MockWebServer
    private lateinit var service: GlmApiService
    private lateinit var adapter: OpenRouterGlmQaAdapter
    private val gson = Gson()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = GlmApiService.create(
            baseUrl = server.url("/").toString(),
            tokenProvider = { "test-token" }
        )
        adapter = OpenRouterGlmQaAdapter(
            glmApiService = service,
            tokenProvider = { "test-token" }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleComment(id: String, text: String): CommentEntity = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE,
        sourceScopeKey = "video-1",
        sourceCommentId = id,
        parentSourceCommentId = null,
        videoId = "video-1",
        importId = "import-1",
        authorDisplayName = "User $id",
        originalText = text,
        normalizedText = text.lowercase(),
        publishedAtEpochMillis = 1000L,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fp-$id",
        processingState = ProcessingState.READY_GLM,
        currentStage = ProcessingStage.GLM_ANALYSIS,
        category = "feedback",
        sentiment = "neutral",
        relevanceScore = 1.0,
        processingConfigVersion = 3L,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        attemptCountLocal = 0,
        attemptCountJev = 0,
        attemptCountGlm = 0,
        lastErrorCode = null,
        lastErrorSummary = null,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun mockGlmResponse(content: String): MockResponse {
        val payload = mapOf(
            "id" to "gen-123",
            "model" to "z-ai/glm-5.3-flash",
            "choices" to listOf(
                mapOf(
                    "message" to mapOf(
                        "role" to "assistant",
                        "content" to content
                    ),
                    "finish_reason" to "stop"
                )
            ),
            "usage" to mapOf(
                "input_tokens" to 100,
                "output_tokens" to 50,
                "cost" to 0.001
            )
        )
        return MockResponse()
            .setResponseCode(200)
            .setBody(gson.toJson(payload))
            .addHeader("Content-Type", "application/json")
    }

    @Test
    fun validGroundedResponse() = runBlocking {
        val comments = listOf(
            sampleComment("c-1", "The camera lens scratches easily"),
            sampleComment("c-2", "Battery drains in 4 hours")
        )

        val content = gson.toJson(
            mapOf(
                "answer" to "Multiple users report that the camera lens scratches easily.",
                "citedCommentIds" to listOf("c-1"),
                "insufficientEvidence" to false
            )
        )
        server.enqueue(mockGlmResponse(content))

        val result = adapter.answerQuestion("Does the lens scratch?", comments)

        assertEquals("Multiple users report that the camera lens scratches easily.", result.answer)
        assertEquals(listOf("c-1"), result.citedCommentIds)
        assertFalse(result.insufficientEvidence)
    }

    @Test
    fun unknownCitedIdFailsClosed() = runBlocking {
        val comments = listOf(
            sampleComment("c-1", "Audio is clear")
        )

        val content = gson.toJson(
            mapOf(
                "answer" to "The audio quality is excellent.",
                "citedCommentIds" to listOf("c-unknown-999"),
                "insufficientEvidence" to false
            )
        )
        server.enqueue(mockGlmResponse(content))

        val result = adapter.answerQuestion("How is the audio?", comments)

        assertEquals(OpenRouterGlmQaAdapter.INSUFFICIENT_EVIDENCE_MESSAGE, result.answer)
        assertTrue(result.citedCommentIds.isEmpty())
        assertTrue(result.insufficientEvidence)
        assertEquals("QA_UNKNOWN_EVIDENCE_ID", result.error)
    }

    @Test
    fun insufficientEvidenceNormalized() = runBlocking {
        val comments = listOf(
            sampleComment("c-1", "Nice video")
        )

        val content = gson.toJson(
            mapOf(
                "answer" to "I cannot find any details about waterproofing in the comments.",
                "citedCommentIds" to emptyList<String>(),
                "insufficientEvidence" to true
            )
        )
        server.enqueue(mockGlmResponse(content))

        val result = adapter.answerQuestion("Is it waterproof?", comments)

        assertEquals(OpenRouterGlmQaAdapter.INSUFFICIENT_EVIDENCE_MESSAGE, result.answer)
        assertTrue(result.citedCommentIds.isEmpty())
        assertTrue(result.insufficientEvidence)
    }

    @Test
    fun exactSchemaEnforcementRejectsExtraOrMissingKeys() = runBlocking {
        val comments = listOf(
            sampleComment("c-1", "Nice video")
        )

        // Missing citedCommentIds key
        val content = gson.toJson(
            mapOf(
                "answer" to "Great video",
                "insufficientEvidence" to false
            )
        )
        server.enqueue(mockGlmResponse(content))

        val result = adapter.answerQuestion("Summary?", comments)

        assertEquals("GLM_SCHEMA_MISMATCH", result.error)
    }

    @Test
    fun pinnedReplyFormatterProducesNaturalProseWithoutInternalIds() {
        val synthesis = SynthesisResult(
            schemaVersion = 3,
            executiveSummary = "Audience highly engaged on camera performance [c-1] and battery life (c-2).",
            blindSpotsAndCorrections = listOf(
                SynthesisBlindSpotV3(
                    claimOrCorrection = "The phone actually does support wireless charging [c-2].",
                    citedCommentIds = listOf("c-2")
                )
            )
        )

        val result = PinnedReplyFormatter.format(synthesis, videoTitle = "Phone Review 2026")

        assertFalse(result.text.contains("c-1"))
        assertFalse(result.text.contains("c-2"))
        assertFalse(result.text.contains("["))
        assertFalse(result.text.contains("]"))
        assertTrue(result.text.contains("Phone Review 2026"))
        assertTrue(result.text.contains("Clarifications & corrections from the community:"))
        assertEquals(listOf("c-2"), result.supportingEvidenceCommentIds)
    }

    @Test
    fun followUpBriefFormatterIncludesRequiredV3SectionsAndHydratedQuote() {
        val synthesis = SynthesisResult(
            schemaVersion = 3,
            executiveSummary = "Overall strong reception with debate on price.",
            themesV3 = listOf(
                SynthesisThemeV3(
                    title = "Build Quality",
                    takeaway = "Viewers love the titanium finish.",
                    representativeCommentId = "c-1",
                    representativeQuote = "The titanium feel is by far the best upgrade.",
                    representativeAuthor = "TechFan",
                    citedCommentIds = listOf("c-1", "c-2")
                )
            ),
            debates = listOf(
                SynthesisDebateV3(
                    topic = "Price vs Value",
                    sideASummary = "Priced reasonably for flagship hardware",
                    sideBSummary = "Too expensive compared to competitors",
                    citedCommentIds = listOf("c-3")
                )
            ),
            blindSpotsAndCorrections = listOf(
                SynthesisBlindSpotV3(
                    claimOrCorrection = "The base storage is 256GB, not 128GB.",
                    citedCommentIds = listOf("c-5")
                )
            ),
            insiderPerspectives = listOf(
                SynthesisInsiderV3(
                    perspective = "Former technician confirms sensor is unchanged.",
                    citedCommentIds = listOf("c-6")
                )
            )
        )

        val markdown = FollowUpBriefFormatter.format(
            videoTitle = "Ultimate Phone Review",
            channelTitle = "Tech Channel",
            videoId = "abcdef12345",
            synthesis = synthesis
        )

        assertTrue(markdown.contains("## Executive Summary"))
        assertTrue(markdown.contains("## Themes"))
        assertTrue(markdown.contains("## Debates"))
        assertTrue(markdown.contains("## Blind Spots & Corrections"))
        assertTrue(markdown.contains("## Insider Perspectives"))
    }
}
