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
        assertTrue(firstPlan.size <= EvidencePlanner.MAX_EVIDENCE_ITEMS)
        val totalBytes = firstPlan.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() }
        val totalTokens = firstPlan.sumOf { (it.text.length / 4).coerceAtLeast(1).toLong() }
        assertTrue(totalBytes <= EvidencePlanner.MAX_EVIDENCE_BYTES)
        assertTrue(totalTokens <= EvidencePlanner.MAX_EVIDENCE_TOKENS)
        assertEquals(firstPlan.size, firstPlan.distinctBy { it.commentId }.size)
        assertTrue(comments.map { it.localId }.containsAll(firstPlan.map { it.commentId }))
    }

    @Test
    fun synthesisRequestContainsExactEvidenceIdsAndNoAuthoritativeContext() = runBlocking {
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
        assertEquals(131072, sent.get("max_tokens").asInt)
        assertNull(userContent.get("authoritativeContext"))
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
    fun unknownRepresentativeCommentIdFailsClosed() = runBlocking {
        val jsonWithBadRepId = """{
            "schemaVersion": 3,
            "executiveSummary": "Executive summary.",
            "themes": [{"title": "Theme 1", "takeaway": "Takeaway", "representativeCommentId": "unknown-rep-id", "citedCommentIds": ["c-01"]}],
            "debates": [],
            "questionsAndRequests": [],
            "blindSpotsAndCorrections": [],
            "insiderPerspectives": [],
            "recommendedFollowUps": []
        }"""
        server.enqueue(response(jsonWithBadRepId))
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

    @Test
    fun portugueseTargetInstructsSynthesisInBrazilianPortugueseAndMaintainsContract() = runBlocking {
        server.enqueue(response(validSynthesisJson("c-01")))
        val context = SynthesisContext(
            importId = "imp-1",
            configVersion = 1L,
            totalRelevantComments = 1,
            categoryDistribution = mapOf("praise" to 1),
            sentimentDistribution = mapOf("positive" to 1),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "Ótimo vídeo sobre inteligência artificial!", "praise", "positive", 10, 0.95, 0.95
                )
            ),
            targetLanguage = "pt-BR"
        )

        val result = adapter().synthesize(context)
        assertNull(result.error)
        assertNotNull(result.synthesis)

        val recorded = server.takeRequest()
        val sent = JsonParser.parseString(recorded.body!!.readUtf8()).asJsonObject
        val messages = sent.getAsJsonArray("messages")
        val systemMessage = messages.map { it.asJsonObject }.first { it.get("role").asString == "system" }
        val systemContent = systemMessage.get("content").asString
        assertTrue(systemContent.contains("Brazilian Portuguese"))

        val userMessage = messages.map { it.asJsonObject }.first { it.get("role").asString == "user" }
        val userContent = JsonParser.parseString(userMessage.get("content").asString).asJsonObject
        assertEquals("pt-BR", userContent.get("targetLanguage").asString)
        assertEquals(3, userContent.get("schemaVersion").asInt)
        assertNotNull(userContent.get("evidenceComments"))
    }

    @Test
    fun englishTargetInstructsSynthesisInEnglishAndMaintainsContract() = runBlocking {
        server.enqueue(response(validSynthesisJson("c-01")))
        val context = SynthesisContext(
            importId = "imp-1",
            configVersion = 1L,
            totalRelevantComments = 1,
            categoryDistribution = mapOf("praise" to 1),
            sentimentDistribution = mapOf("positive" to 1),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "Great video about AI developments!", "praise", "positive", 10, 0.95, 0.95
                )
            ),
            targetLanguage = "en"
        )

        val result = adapter().synthesize(context)
        assertNull(result.error)
        assertNotNull(result.synthesis)

        val recorded = server.takeRequest()
        val sent = JsonParser.parseString(recorded.body!!.readUtf8()).asJsonObject
        val messages = sent.getAsJsonArray("messages")
        val systemMessage = messages.map { it.asJsonObject }.first { it.get("role").asString == "system" }
        val systemContent = systemMessage.get("content").asString
        assertTrue(systemContent.contains("in English"))

        val userMessage = messages.map { it.asJsonObject }.first { it.get("role").asString == "user" }
        val userContent = JsonParser.parseString(userMessage.get("content").asString).asJsonObject
        assertEquals("en", userContent.get("targetLanguage").asString)
        assertEquals(3, userContent.get("schemaVersion").asInt)
        assertNotNull(userContent.get("evidenceComments"))
    }

    @Test
    fun synthesisSystemInstructionExplicitlyForbidsIdentifiersInProse() = runBlocking {
        server.enqueue(response(validSynthesisJson("c-01")))
        val context = SynthesisContext(
            importId = "imp-1",
            configVersion = 1L,
            totalRelevantComments = 1,
            categoryDistribution = mapOf("praise" to 1),
            sentimentDistribution = mapOf("positive" to 1),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "Great video!", "praise", "positive", 10, 0.9, 0.9
                )
            )
        )

        val result = adapter().synthesize(context)
        assertNull(result.error)
        assertNotNull(result.synthesis)

        val recorded = server.takeRequest()
        val sent = JsonParser.parseString(recorded.body!!.readUtf8()).asJsonObject
        val messages = sent.getAsJsonArray("messages")
        val systemMessage = messages.map { it.asJsonObject }.first { it.get("role").asString == "system" }
        val systemContent = systemMessage.get("content").asString
        assertTrue(systemContent.contains("Do not include comment IDs, evidence IDs, UUIDs, bracketed citation IDs, or other internal identifiers in human-readable prose."))
        assertTrue(systemContent.contains("Keep evidence references only in the dedicated structured citation/evidence fields."))
    }

    @Test
    fun accidentalCommentIdsInProseAreSanitizedWhileStructuredIdsArePreserved() = runBlocking {
        val jsonWithIdsInProse = """{
            "schemaVersion": 3,
            "executiveSummary": "Several users praised the feature [c-01] while others raised concerns [c-02].",
            "themes": [{"title": "Theme (c-01)", "takeaway": "Takeaway referring to c-02 directly.", "representativeCommentId": "c-01", "citedCommentIds": ["c-01", "c-02"]}],
            "debates": [{"topic": "Topic [c-01]", "sideASummary": "Side A [c-01]", "sideBSummary": "Side B [c-02]", "citedCommentIds": ["c-01", "c-02"]}],
            "questionsAndRequests": [{"questionOrRequest": "Question [c-01]", "citedCommentIds": ["c-01"]}],
            "blindSpotsAndCorrections": [{"claimOrCorrection": "Claim [c-02]", "citedCommentIds": ["c-02"]}],
            "insiderPerspectives": [{"perspective": "Perspective [c-01]", "citedCommentIds": ["c-01"]}],
            "recommendedFollowUps": [{"action": "Action [c-01]", "rationale": "Rationale [c-02]", "citedCommentIds": ["c-01"]}]
        }"""
        server.enqueue(response(jsonWithIdsInProse))
        val context = SynthesisContext(
            importId = "imp-1",
            configVersion = 1L,
            totalRelevantComments = 2,
            categoryDistribution = mapOf("praise" to 1, "criticism" to 1),
            sentimentDistribution = mapOf("positive" to 1, "negative" to 1),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "Praise", "praise", "positive", 5, 0.9, 0.9
                ),
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-02", "Criticism", "criticism", "negative", 3, 0.9, 0.9
                )
            )
        )

        val result = adapter().synthesize(context)
        assertNull(result.error)
        val synthesis = result.synthesis
        assertNotNull(synthesis)

        // Prose fields must not contain comment IDs
        assertEquals("Several users praised the feature while others raised concerns.", synthesis!!.executiveSummary)
        assertEquals("Theme", synthesis.themesV3[0].title)
        assertEquals("Takeaway referring to directly.", synthesis.themesV3[0].takeaway)
        assertEquals("Topic", synthesis.debates[0].topic)
        assertEquals("Question", synthesis.questionsAndRequests[0].questionOrRequest)
        assertEquals("Claim", synthesis.blindSpotsAndCorrections[0].claimOrCorrection)
        assertEquals("Perspective", synthesis.insiderPerspectives[0].perspective)
        assertEquals("Action", synthesis.recommendedFollowUps[0].action)

        // Structured citation/evidence IDs remain intact
        assertEquals(listOf("c-01", "c-02"), synthesis.themesV3[0].citedCommentIds)
        assertEquals("c-01", synthesis.themesV3[0].representativeCommentId)
    }

    @Test
    fun smallInputRequestsFullMaxTokens131072() = runBlocking {
        server.enqueue(response(validSynthesisJson("c-01")))
        val context = SynthesisContext(
            importId = "imp-1",
            configVersion = 1L,
            totalRelevantComments = 1,
            categoryDistribution = mapOf("praise" to 1),
            sentimentDistribution = mapOf("positive" to 1),
            evidenceItems = listOf(
                com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput(
                    "c-01", "Small comment text", "praise", "positive", 1, 0.9, 0.9
                )
            )
        )
        val result = adapter().synthesize(context)
        assertNull(result.error)
        val recorded = server.takeRequest()
        val sent = JsonParser.parseString(recorded.body!!.readUtf8()).asJsonObject
        assertEquals(131072, sent.get("max_tokens").asInt)
        assertEquals(131072, OpenRouterGlmSynthesisAdapter.calculateMaxTokens(500))
    }

    @Test
    fun largerInputReducesMaxTokensOnlyWhenRequiredByTotalContext() {
        assertEquals(131072, OpenRouterGlmSynthesisAdapter.calculateMaxTokens(500_000))
        assertEquals(131072, OpenRouterGlmSynthesisAdapter.calculateMaxTokens(917_504))
        assertEquals(98576, OpenRouterGlmSynthesisAdapter.calculateMaxTokens(950_000))
    }

    @Test
    fun inputAndMaxTokensNeverExceedsTotalContext() {
        val testInputSizes = listOf(0, 1, 100, 10_000, 500_000, 917_504, 950_000, 1_000_000, 1_048_576, 1_100_000)
        for (inputTokens in testInputSizes) {
            val maxTokens = OpenRouterGlmSynthesisAdapter.calculateMaxTokens(inputTokens)
            if (inputTokens <= OpenRouterGlmSynthesisAdapter.MAX_CONTEXT_TOKENS) {
                assertTrue(
                    "input ($inputTokens) + maxTokens ($maxTokens) exceeds context cap",
                    inputTokens + maxTokens <= OpenRouterGlmSynthesisAdapter.MAX_CONTEXT_TOKENS
                )
            } else {
                assertEquals(0, maxTokens)
            }
        }
    }

    @Test
    fun maxTokensNeverExceedsMaxOutputTokens131072() {
        val testInputSizes = listOf(0, 1, 10, 100, 1000, 100_000, 500_000, 1_000_000)
        for (inputTokens in testInputSizes) {
            val maxTokens = OpenRouterGlmSynthesisAdapter.calculateMaxTokens(inputTokens)
            assertTrue(
                "maxTokens ($maxTokens) exceeds MAX_OUTPUT_TOKENS (131072)",
                maxTokens <= OpenRouterGlmSynthesisAdapter.MAX_OUTPUT_TOKENS
            )
        }
    }

    @Test
    fun noLegacyArtificialCapRemains() {
        val legacyCaps = setOf(8192, 16384, 32768, 65536)
        val maxTokensForSmallInput = OpenRouterGlmSynthesisAdapter.calculateMaxTokens(200)
        assertFalse(legacyCaps.contains(maxTokensForSmallInput))
        assertEquals(131072, maxTokensForSmallInput)
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
        val repId = citedIds.firstOrNull() ?: "c-01"
        val citedJson = citedIds.joinToString(",") { gson.toJson(it) }
        return """{
            "schemaVersion": 3,
            "executiveSummary": "Executive summary of comments.",
            "themes": [{"title": "Theme 1", "takeaway": "Takeaway 1", "representativeCommentId": ${gson.toJson(repId)}, "citedCommentIds": [$citedJson]}],
            "debates": [{"topic": "Topic 1", "sideASummary": "Side A", "sideBSummary": "Side B", "citedCommentIds": [$citedJson]}],
            "questionsAndRequests": [{"questionOrRequest": "Question 1", "citedCommentIds": [$citedJson]}],
            "blindSpotsAndCorrections": [{"claimOrCorrection": "Claim 1", "citedCommentIds": [$citedJson]}],
            "insiderPerspectives": [{"perspective": "Perspective 1", "citedCommentIds": [$citedJson]}],
            "recommendedFollowUps": [{"action": "Action 1", "rationale": "Rationale 1", "citedCommentIds": [$citedJson]}]
        }"""
    }
}
