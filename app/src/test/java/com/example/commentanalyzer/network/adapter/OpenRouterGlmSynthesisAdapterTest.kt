package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.domain.analysis.SynthesisContext
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.synthesis.EvidencePlanner
import com.example.commentanalyzer.network.glm.GlmApiService
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterGlmSynthesisAdapterTest {
    private val server = MockWebServer()
    private val gson = Gson()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun evidenceSelectionIsDeterministicAndBounded() {
        val comments = (1..50).map { i ->
            CommentEntity(
                localId = "c-%02d".format(i),
                sourceType = SourceType.YOUTUBE,
                sourceScopeKey = "video-1",
                sourceCommentId = "sc-$i",
                parentSourceCommentId = null,
                videoId = "video-1",
                importId = "import-1",
                authorDisplayName = "User $i",
                originalText = "Comment text number $i with some detailed commentary.",
                normalizedText = "comment text number $i",
                publishedAtEpochMillis = 1000L * i,
                publishedAtPrecision = null,
                fingerprintVersion = 1,
                fingerprintSha256 = "fp-$i",
                processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                category = if (i % 3 == 0) "praise" else if (i % 3 == 1) "criticism" else "feedback",
                sentiment = if (i % 2 == 0) "positive" else "negative",
                relevanceScore = 1.0,
                processingConfigVersion = 1L,
                leaseOwner = null,
                leaseExpiresAt = null,
                nextAttemptAt = null,
                attemptCountLocal = 0,
                attemptCountJev = 0,
                attemptCountGlm = 0,
                lastErrorCode = null,
                lastErrorSummary = null,
                createdAt = 1000L,
                updatedAt = 1000L,
                likeCount = i * 10
            )
        }

        val firstPlan = EvidencePlanner.planEvidence(comments)
        val secondPlan = EvidencePlanner.planEvidence(comments)

        assertEquals(firstPlan.map { it.commentId }, secondPlan.map { it.commentId })
        assertTrue(firstPlan.size <= 30)
    }

    @Test
    fun synthesisRequestContainsAppComputedDistributionsAndExactEvidenceIds() = runBlocking {
        server.enqueue(response(validSynthesisJson("c-01", "c-02")))

        val comments = listOf(
            CommentEntity(
                localId = "c-01", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                parentSourceCommentId = null, videoId = "v1", importId = "imp-1", authorDisplayName = "U1",
                originalText = "Great video", normalizedText = "great video", publishedAtEpochMillis = 100L,
                publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "a",
                processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 1L,
                leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                createdAt = 100L, updatedAt = 100L, likeCount = 5
            ),
            CommentEntity(
                localId = "c-02", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "2",
                parentSourceCommentId = null, videoId = "v1", importId = "imp-1", authorDisplayName = "U2",
                originalText = "Bad audio", normalizedText = "bad audio", publishedAtEpochMillis = 200L,
                publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "b",
                processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                category = "criticism", sentiment = "negative", relevanceScore = 1.0, processingConfigVersion = 1L,
                leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                createdAt = 100L, updatedAt = 100L, likeCount = 1
            )
        )

        val evidence = EvidencePlanner.planEvidence(comments)
        val context = SynthesisContext(
            importId = "imp-1",
            configVersion = 1L,
            totalRelevantComments = 2,
            categoryDistribution = mapOf("praise" to 1, "criticism" to 1),
            sentimentDistribution = mapOf("positive" to 1, "negative" to 1),
            evidenceItems = evidence
        )

        val result = adapter().synthesize(context)
        val recorded = server.takeRequest()
        val sent = JsonParser.parseString(recorded.body!!.readUtf8()).asJsonObject
        val messages = sent.getAsJsonArray("messages")
        val userMessage = messages.map { it.asJsonObject }.first { it.get("role").asString == "user" }
        val userContent = JsonParser.parseString(userMessage.get("content").asString).asJsonObject

        assertNull(result.error)
        assertNotNull(result.synthesis)
        assertEquals(2, userContent.getAsJsonObject("authoritativeContext").get("totalRelevantComments").asInt)
        val evidenceArray = userContent.getAsJsonArray("evidenceComments")
        assertEquals(2, evidenceArray.size())
        val sentEvidenceIds = evidenceArray.map { it.asJsonObject.get("commentId").asString }
        assertEquals(listOf("c-01", "c-02"), sentEvidenceIds)
    }

    @Test
    fun unknownEvidenceIdFailsClosed() = runBlocking {
        server.enqueue(response(validSynthesisJson("c-01", "unknown-id")))
        val context = SynthesisContext(
            importId = "imp-1", configVersion = 1L, totalRelevantComments = 1,
            categoryDistribution = emptyMap(), sentimentDistribution = emptyMap(),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "text", "praise", "positive", 1, 0.9, 0.9
                )
            )
        )
        val result = adapter().synthesize(context)
        assertEquals("GLM_UNKNOWN_EVIDENCE_ID", result.error)
        assertNull(result.synthesis)
    }

    @Test
    fun malformedSynthesisFailsClosed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{malformed"))
        val context = SynthesisContext(
            importId = "imp-1", configVersion = 1L, totalRelevantComments = 1,
            categoryDistribution = emptyMap(), sentimentDistribution = emptyMap(),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "text", "praise", "positive", 1, 0.9, 0.9
                )
            )
        )
        val result = adapter().synthesize(context)
        assertEquals("GLM_MALFORMED_JSON", result.error)
        assertNull(result.synthesis)
    }

    @Test
    fun truncatedSynthesisFailsClosed() = runBlocking {
        server.enqueue(response(validSynthesisJson("c-01"), finishReason = "length"))
        val context = SynthesisContext(
            importId = "imp-1", configVersion = 1L, totalRelevantComments = 1,
            categoryDistribution = emptyMap(), sentimentDistribution = emptyMap(),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "text", "praise", "positive", 1, 0.9, 0.9
                )
            )
        )
        val result = adapter().synthesize(context)
        assertEquals("GLM_OUTPUT_TRUNCATED", result.error)
        assertNull(result.synthesis)
    }

    private fun adapter(): OpenRouterGlmSynthesisAdapter {
        val tokenProvider = { "sk-test-key" }
        return OpenRouterGlmSynthesisAdapter(
            glmApiService = GlmApiService.create(
                baseUrl = server.url("/").toString(),
                tokenProvider = tokenProvider
            ),
            tokenProvider = tokenProvider
        )
    }

    private fun response(content: String?, finishReason: String = "stop") = MockResponse()
        .setResponseCode(200)
        .setBody(
            """{"id":"gen-synth","model":"z-ai/glm-5.3-flash:free","provider":"test","usage":{"input_tokens":20,"output_tokens":15,"cost":0.002},"choices":[{"finish_reason":${gson.toJson(finishReason)},"message":{"content":${content?.let(gson::toJson) ?: "null"}}}]}"""
        )

    private fun validSynthesisJson(vararg citedIds: String): String {
        val citedJson = citedIds.joinToString(",") { gson.toJson(it) }
        return """{
            "schemaVersion": 2,
            "executiveSummary": "Executive summary of comments.",
            "themes": [{"title": "Theme 1", "description": "Desc", "citedCommentIds": [$citedJson]}],
            "agreements": [{"topic": "Topic 1", "description": "Desc", "citedCommentIds": [$citedJson]}],
            "contrastingPerspectives": [{"perspectiveA": "A", "perspectiveB": "B", "description": "Desc", "citedCommentIds": [$citedJson]}],
            "notableInsights": [{"insight": "Insight", "citedCommentIds": [$citedJson]}],
            "actionableFollowUps": [{"action": "Action", "rationale": "Rationale", "citedCommentIds": [$citedJson]}]
        }"""
    }
}
