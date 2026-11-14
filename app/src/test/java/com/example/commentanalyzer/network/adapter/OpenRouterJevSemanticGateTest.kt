package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import com.example.commentanalyzer.network.dto.jev.JevAnswerValue
import com.example.commentanalyzer.network.dto.jev.JevDecisionResponse
import com.example.commentanalyzer.network.dto.jev.JevUsage
import com.example.commentanalyzer.network.jev.JevApiService
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterJevSemanticGateTest {

    private val server = MockWebServer()
    private val gson = Gson()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createGate(): OpenRouterJevSemanticGate {
        val tokenProvider = { "sk-test-openrouter-key" }
        val service = JevApiService.create(
            baseUrl = server.url("/").toString(),
            tokenProvider = tokenProvider
        )

        return OpenRouterJevSemanticGate(
            jevApiService = service,
            tokenProvider = tokenProvider
        )
    }

    @Test
    fun testValidNoulDecision() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-123",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = JevUsage(10L, 5L, 0.0001),
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.85)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Great video!", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNull(result.error)
        assertEquals("typesafe/jev-1.14", result.modelResolved)
        assertEquals("test-provider", result.provider)
        assertEquals(1, result.results.size)
        assertEquals("c1", result.results[0].commentId)
        assertEquals("comment_quality_gate", result.results[0].questionKey)
        assertEquals(0.85, result.results[0].probability, 0.0001)
        assertEquals("gen-123", result.results[0].rawOutput)

        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test-openrouter-key", recorded.getHeader("Authorization"))
        assertEquals("YouTube Comment Analyzer", recorded.getHeader("X-Title"))
    }

    @Test
    fun testMissingRequestedAnswerKey() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-123",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "wrong_key" to JevAnswerValue(type = "noul", noul = 0.5)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Missing requested answer key"))
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun testUnexpectedAnswerKey() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-123",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.5),
                "extra_key" to JevAnswerValue(type = "noul", noul = 0.1)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Unexpected answer key"))
    }

    @Test
    fun testWrongAnswerType() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-123",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "text", noul = null)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Wrong answer type"))
    }

    @Test
    fun testInvalidOrOutOfRangeProbability() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-123",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 1.2)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Invalid probability value"))
    }

    @Test
    fun testHttp401Unauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertEquals("HTTP_401_UNAUTHORIZED", result.error)
    }

    @Test
    fun testHttp429RateLimit() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Rate limit exceeded"))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertEquals("HTTP_429_RATE_LIMIT", result.error)
    }

    @Test
    fun testHttp429RateLimitWithRetryAfterHeader() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "120")
                .setBody("Rate limit exceeded")
        )

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertEquals("HTTP_429_RATE_LIMIT", result.error)
        assertEquals(120L, result.retryAfterSeconds)
    }

    @Test
    fun testHttp5xxServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertEquals("HTTP_SERVER_ERROR_503", result.error)
    }

    @Test
    fun testMissingOpenRouterKey() = runBlocking {
        val tokenProvider = { null as String? }
        val service = JevApiService.create(
            baseUrl = server.url("/").toString(),
            tokenProvider = tokenProvider
        )

        val gate = OpenRouterJevSemanticGate(
            jevApiService = service,
            tokenProvider = tokenProvider
        )
        val inputs = listOf(GateInput("c1", "Test comment", null, null))
        val config = GateConfig(1L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertEquals("OPENROUTER_KEY_MISSING", result.error)
    }

    @Test
    fun testV2ValidNoulAndChoicesInOneRequest() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-v2-1",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = JevUsage(15L, 8L, 0.0002),
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.9),
                "comment_category" to JevAnswerValue(type = "choice", choice = "feedback", confidence = 0.85),
                "comment_sentiment" to JevAnswerValue(type = "choice", choice = "positive", confidence = 0.92)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Awesome video, keep doing these tutorials!", null, null))
        val config = GateConfig(2L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNull(result.error)
        assertEquals(1, result.results.size)
        val item = result.results[0]
        assertEquals("c1", item.commentId)
        assertEquals("comment_quality_gate", item.questionKey)
        assertEquals(0.9, item.probability, 0.0001)
        assertEquals("feedback", item.category)
        assertEquals(0.85, item.categoryConfidence!!, 0.0001)
        assertEquals("positive", item.sentiment)
        assertEquals(0.92, item.sentimentConfidence!!, 0.0001)

        val recorded = server.takeRequest()
        val requestJson = recorded.body.readUtf8()
        assertTrue(requestJson.contains("comment_quality_gate"))
        assertTrue(requestJson.contains("comment_category"))
        assertTrue(requestJson.contains("comment_sentiment"))
    }

    @Test
    fun testV2InvalidCategoryFailsClosed() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-v2-2",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.9),
                "comment_category" to JevAnswerValue(type = "choice", choice = "unsupported_cat", confidence = 0.85),
                "comment_sentiment" to JevAnswerValue(type = "choice", choice = "positive", confidence = 0.92)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Awesome video!", null, null))
        val config = GateConfig(2L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Invalid category value"))
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun testV2InvalidSentimentFailsClosed() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-v2-3",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.9),
                "comment_category" to JevAnswerValue(type = "choice", choice = "praise", confidence = 0.85),
                "comment_sentiment" to JevAnswerValue(type = "choice", choice = "mixed", confidence = 0.92)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Awesome video!", null, null))
        val config = GateConfig(2L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Invalid sentiment value"))
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun testV2MissingRequiredAnswerFailsClosed() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-v2-4",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.9),
                "comment_category" to JevAnswerValue(type = "choice", choice = "praise", confidence = 0.85)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Awesome video!", null, null))
        val config = GateConfig(2L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Answers keys"))
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun testV2InvalidConfidenceFailsClosed() = runBlocking {
        val responseBody = JevDecisionResponse(
            id = "gen-v2-5",
            model = "typesafe/jev-1.14",
            provider = "test-provider",
            usage = null,
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.9),
                "comment_category" to JevAnswerValue(type = "choice", choice = "praise", confidence = 1.5),
                "comment_sentiment" to JevAnswerValue(type = "choice", choice = "positive", confidence = 0.9)
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val gate = createGate()
        val inputs = listOf(GateInput("c1", "Awesome video!", null, null))
        val config = GateConfig(2L, "typesafe/jev-1.14", 0.3, 0.7)

        val result = gate.screen(inputs, config)

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Invalid category confidence"))
        assertTrue(result.results.isEmpty())
    }
}
