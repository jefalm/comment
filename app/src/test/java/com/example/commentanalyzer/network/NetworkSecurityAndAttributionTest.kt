package com.example.commentanalyzer.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkSecurityAndAttributionTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testDefaultHeaderOmission() {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200))
        val interceptor = OpenRouterHeaderInterceptor(
            tokenProvider = { "sk-test-token" },
            attributionConfig = OpenRouterAttributionConfig(refererUrl = null)
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("HTTP-Referer"))
        assertEquals("YouTube Comment Analyzer", recorded.getHeader("X-Title"))
        assertEquals("Bearer sk-test-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun testValidHttpsRefererInclusion() {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200))
        val interceptor = OpenRouterHeaderInterceptor(
            tokenProvider = { null },
            attributionConfig = OpenRouterAttributionConfig(refererUrl = "https://example.com/myapp")
        )
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val request = Request.Builder().url(server.url("/test")).build()
        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals("https://example.com/myapp", recorded.getHeader("HTTP-Referer"))
    }

    @Test
    fun testInvalidOrBlankRefererOmission() {
        val config1 = OpenRouterAttributionConfig(refererUrl = "   ")
        assertNull(config1.getValidatedReferer())

        val config2 = OpenRouterAttributionConfig(refererUrl = "http://insecure.com")
        assertNull(config2.getValidatedReferer())
    }

    @Test
    fun testUrlSanitizationRedactsSecrets() {
        val rawUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=xyz123&key=AIzaSyFakeApiKey12345&access_token=secret_token"
        val sanitized = SafeNetworkLogging.sanitizeUrl(rawUrl)
        assertEquals("https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=xyz123&key=REDACTED&access_token=REDACTED", sanitized)
        org.junit.Assert.assertFalse(sanitized.contains("AIzaSyFakeApiKey12345"))
        org.junit.Assert.assertFalse(sanitized.contains("secret_token"))
    }
}
