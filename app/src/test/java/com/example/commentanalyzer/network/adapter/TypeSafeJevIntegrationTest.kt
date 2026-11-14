package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.data.local.RemoteProcessingReadiness
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeSafeJevIntegrationTest {

    private val typeSafeServer = MockWebServer()
    private val gson = Gson()

    @After
    fun tearDown() {
        typeSafeServer.shutdown()
    }

    @Test
    fun testTypeSafeJevContractAndSeparation() = runBlocking {
        // 1. TypeSafe JEV request path is /v1/systemone
        // 2. TypeSafe JEV request model is jev-1.14.0
        // 3. Authorization uses the dedicated TypeSafe credential
        // 4. No OpenRouter attribution headers appear on the TypeSafe request
        // 9. Valid response preserves original commentId/result association
        val responseBody = JevDecisionResponse(
            id = "sys-123",
            model = "jev-1.14.0",
            provider = "typesafe",
            usage = JevUsage(12L, 4L, 0.00015),
            answers = mapOf(
                "comment_quality_gate" to JevAnswerValue(type = "noul", noul = 0.88),
                "comment_category" to JevAnswerValue(type = "choice", choice = "praise"),
                "comment_sentiment" to JevAnswerValue(type = "choice", choice = "positive")
            )
        )
        typeSafeServer.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(responseBody)))

        val typeSafeKey = "typesafe-secret-key-abc"
        val jevService = JevApiService.create(
            baseUrl = typeSafeServer.url("/").toString(),
            tokenProvider = { typeSafeKey }
        )
        val gate = TypeSafeJevSemanticGate(
            jevApiService = jevService,
            tokenProvider = { typeSafeKey }
        )

        val inputs = listOf(GateInput("comment-1", "This is very helpful!", "author1", "vid1"))
        val config = GateConfig(
            configVersion = 2L,
            model = "jev-1.14.0",
            rejectThreshold = 0.3,
            passThreshold = 0.7
        )

        val result = gate.screen(inputs, config)

        assertNull(result.error)
        assertEquals(1, result.results.size)
        assertEquals("comment-1", result.results[0].commentId)
        assertEquals(0.88, result.results[0].probability, 0.0001)

        val recorded = typeSafeServer.takeRequest()
        assertEquals("/v1/systemone", recorded.path)
        assertEquals("Bearer $typeSafeKey", recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("X-Title"))
        assertNull(recorded.getHeader("HTTP-Referer"))

        val requestJson = recorded.body.readUtf8()
        assertTrue(requestJson.contains("\"model\":\"jev-1.14.0\""))
        assertTrue(requestJson.contains("comment_quality_gate"))
        assertTrue(requestJson.contains("This is very helpful!"))
    }

    @Test
    fun testProductionBootstrapConfigResolvesToCanonicalModel() {
        val defaultConfig = com.example.commentanalyzer.data.local.ProcessingConfigBootstrap.createDefaultV2Config()
        assertEquals("jev-1.14.0", defaultConfig.jevModelRequested)
    }

    @Test
    fun testMissingTypeSafeKeyBlocksJevSafely() = runBlocking {
        // 5. missing TypeSafe key -> TYPESAFE_KEY_MISSING without making a request
        val gate = TypeSafeJevSemanticGate(
            jevApiService = JevApiService.create(
                baseUrl = typeSafeServer.url("/").toString(),
                tokenProvider = { null }
            ),
            tokenProvider = { null }
        )

        val result = gate.screen(
            listOf(GateInput("c1", "text", null, null)),
            GateConfig(1L, "jev-1.14.0", 0.3, 0.7)
        )

        assertEquals("TYPESAFE_KEY_MISSING", result.error)
        assertTrue(result.results.isEmpty())
        assertEquals(0, typeSafeServer.requestCount)
    }

    @Test
    fun test401FailsClosed() = runBlocking {
        // 6. 401 -> HTTP_401_UNAUTHORIZED
        typeSafeServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val gate = TypeSafeJevSemanticGate(
            jevApiService = JevApiService.create(
                baseUrl = typeSafeServer.url("/").toString(),
                tokenProvider = { "invalid-key" }
            ),
            tokenProvider = { "invalid-key" }
        )

        val result = gate.screen(
            listOf(GateInput("c1", "text", null, null)),
            GateConfig(1L, "jev-1.14.0", 0.3, 0.7)
        )

        assertEquals("HTTP_401_UNAUTHORIZED", result.error)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun test429AndRetryAfterRemainRetryable() = runBlocking {
        // 7. 429 preserves Retry-After
        typeSafeServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "45")
                .setBody("Rate limited")
        )

        val gate = TypeSafeJevSemanticGate(
            jevApiService = JevApiService.create(
                baseUrl = typeSafeServer.url("/").toString(),
                tokenProvider = { "some-key" }
            ),
            tokenProvider = { "some-key" }
        )

        val result = gate.screen(
            listOf(GateInput("c1", "text", null, null)),
            GateConfig(1L, "jev-1.14.0", 0.3, 0.7)
        )

        assertEquals("HTTP_429_RATE_LIMIT", result.error)
        assertEquals(45L, result.retryAfterSeconds)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun test5xxRemainsSafeError() = runBlocking {
        // 8. 5xx remains a safe error
        typeSafeServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val gate = TypeSafeJevSemanticGate(
            jevApiService = JevApiService.create(
                baseUrl = typeSafeServer.url("/").toString(),
                tokenProvider = { "some-key" }
            ),
            tokenProvider = { "some-key" }
        )

        val result = gate.screen(
            listOf(GateInput("c1", "text", null, null)),
            GateConfig(1L, "jev-1.14.0", 0.3, 0.7)
        )

        assertEquals("HTTP_SERVER_ERROR_500", result.error)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun testNoErrorExposesCredentialMaterial() = runBlocking {
        // 10. credential value never appears in returned error text
        val secretKey = "super-secret-credential-value"
        typeSafeServer.enqueue(MockResponse().setResponseCode(401).setBody("Token $secretKey was rejected"))

        val gate = TypeSafeJevSemanticGate(
            jevApiService = JevApiService.create(
                baseUrl = typeSafeServer.url("/").toString(),
                tokenProvider = { secretKey }
            ),
            tokenProvider = { secretKey }
        )

        val result = gate.screen(
            listOf(GateInput("c1", "text", null, null)),
            GateConfig(1L, "jev-1.14.0", 0.3, 0.7)
        )

        assertNotNull(result.error)
        assertFalse(result.error!!.contains(secretKey))
        assertFalse(result.error!!.contains("super-secret"))
    }

    @Test
    fun testReadinessSemantics() {
        // 11. readiness semantics:
        // - TypeSafe + consent -> JEV ready
        // - OpenRouter + consent -> GLM ready
        // - each provider can be independently unavailable
        val bothReady = RemoteProcessingReadiness(
            hasOpenRouterKey = true,
            hasTypeSafeKey = true,
            remoteProcessingConsent = true
        )
        assertTrue(bothReady.isReady)
        assertTrue(bothReady.isJevReady)
        assertTrue(bothReady.isGlmReady)

        val onlyTypeSafe = RemoteProcessingReadiness(
            hasOpenRouterKey = false,
            hasTypeSafeKey = true,
            remoteProcessingConsent = true
        )
        assertFalse(onlyTypeSafe.isReady)
        assertTrue(onlyTypeSafe.isJevReady)
        assertFalse(onlyTypeSafe.isGlmReady)

        val onlyOpenRouter = RemoteProcessingReadiness(
            hasOpenRouterKey = true,
            hasTypeSafeKey = false,
            remoteProcessingConsent = true
        )
        assertFalse(onlyOpenRouter.isReady)
        assertFalse(onlyOpenRouter.isJevReady)
        assertTrue(onlyOpenRouter.isGlmReady)

        val noConsent = RemoteProcessingReadiness(
            hasOpenRouterKey = true,
            hasTypeSafeKey = true,
            remoteProcessingConsent = false
        )
        assertFalse(noConsent.isReady)
        assertFalse(noConsent.isJevReady)
        assertFalse(noConsent.isGlmReady)
    }
}
