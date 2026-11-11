package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.domain.batching.BatchPlanner
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput
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

class OpenRouterGlmDeepAnalyzerTest {
    private val server = MockWebServer()
    private val gson = Gson()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validResponsePreservesIdsMetadataAndStrictRequestContract() = runBlocking {
        server.enqueue(response(results("c1", "c2")))

        val result = analyzer().analyze(inputs(), config())
        val recorded = server.takeRequest()
        val sent = JsonParser.parseString(recorded.body!!.readUtf8()).asJsonObject
        val format = sent.getAsJsonObject("response_format")
        val schemaEnvelope = format.getAsJsonObject("json_schema")

        assertNull(result.error)
        assertEquals("z-ai/glm-5.3-flash", sent.get("model").asString)
        assertEquals("z-ai/glm-5.3-flash", result.modelRequested)
        assertEquals("z-ai/glm-5.3-flash:free", result.modelResolved)
        assertEquals("test-provider", result.provider)
        assertEquals("gen-123", result.generationId)
        assertEquals(11L, result.inputTokens)
        assertEquals(7L, result.outputTokens)
        assertEquals(0.001, result.costUsd!!, 0.000001)
        assertEquals(listOf("c1", "c2"), result.results.map { it.commentId })
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("HTTP-Referer"))
        assertTrue(format.get("type").asString == "json_schema")
        assertEquals("comment_analysis_batch", schemaEnvelope.get("name").asString)
        assertTrue(schemaEnvelope.get("strict").asBoolean)
        assertFalse(schemaEnvelope.getAsJsonObject("schema").get("additionalProperties").asBoolean)
        assertTrue(sent.getAsJsonObject("provider").get("require_parameters").asBoolean)
        assertEquals(
            "response-healing",
            sent.getAsJsonArray("plugins").get(0).asJsonObject.get("id").asString
        )
        assertEquals("low", sent.getAsJsonObject("reasoning").get("effort").asString)
        assertTrue(sent.getAsJsonObject("reasoning").get("exclude").asBoolean)
    }

    @Test
    fun fiftyFourCommentsArePlannedAsTwentyTwentyAndFourteen() {
        val items = (1..54).map { index ->
            BatchPlanner.Item("comment-%02d".format(index), "Short text")
        }

        val batches = BatchPlanner.planBatches(items, BatchPlanner.GLM_LIMITS)

        assertEquals(listOf(20, 20, 14), batches.map { it.size })
    }

    @Test
    fun responseHealingDoesNotBypassStrictSchemaOrExactIdValidation() = runBlocking {
        server.enqueue(response(results("c1", "other")))
        assertRejected(analyzer().analyze(inputs(), config()), "GLM_RESULT_ID_SET_MISMATCH")

        server.enqueue(response("""{"schemaVersion":1,"results":[]}"""))
        assertRejected(analyzer().analyze(inputs(), config()), "GLM_RESULT_ID_SET_MISMATCH")

        server.enqueue(response("""{"schemaVersion":1,"results":[],"extra":true}"""))
        assertRejected(analyzer().analyze(inputs(), config()), "GLM_SCHEMA_MISMATCH")
    }

    @Test
    fun lengthFinishReasonFailsAsTruncatedWithoutPartialResults() = runBlocking {
        server.enqueue(response(results("c1", "c2"), finishReason = "length"))

        val result = analyzer().analyze(inputs(), config())

        assertEquals("GLM_OUTPUT_TRUNCATED", result.error)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun malformedJsonRejectsWholeBatch() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{invalid-envelope"))
        assertRejected(analyzer().analyze(inputs(), config()), "GLM_MALFORMED_JSON")

        server.enqueue(response("{invalid-content"))
        assertRejected(analyzer().analyze(inputs(), config()), "GLM_MALFORMED_JSON")
    }

    @Test
    fun missingResponseContentRejectsWholeBatch() = runBlocking {
        server.enqueue(response(content = null))
        assertRejected(analyzer().analyze(inputs(), config()), "GLM_MISSING_RESPONSE_CONTENT")
    }

    @Test
    fun schemaTypeMismatchRejectsWholeBatch() =
        assertFailure("""{"schemaVersion":1,"results":"not-an-array"}""", "GLM_SCHEMA_MISMATCH")

    @Test
    fun missingSchemaFieldRejectsWholeBatch() =
        assertFailure("""{"schemaVersion":1,"results":[{"commentId":"c1"}]}""", "GLM_SCHEMA_MISMATCH")

    @Test
    fun additionalSchemaFieldRejectsWholeBatch() =
        assertFailure("""{"schemaVersion":1,"results":[],"extra":true}""", "GLM_SCHEMA_MISMATCH")

    @Test
    fun unsupportedSchemaVersionRejectsWholeBatch() =
        assertFailure(results("c1", "c2", schemaVersion = 2), "GLM_SCHEMA_VERSION_MISMATCH")

    @Test
    fun invalidEnumsRejectWholeBatch() =
        assertFailure(results("c1", "c2", category = "unknown"), "GLM_SCHEMA_MISMATCH")

    @Test
    fun nonFiniteConfidenceRejectsWholeBatch() =
        assertFailure(results("c1", "c2", confidence = "1e999"), "GLM_SCHEMA_MISMATCH")

    @Test
    fun outOfRangeConfidenceRejectsWholeBatch() =
        assertFailure(results("c1", "c2", confidence = "1.1"), "GLM_SCHEMA_MISMATCH")

    @Test
    fun duplicateReturnedIdsRejectWholeBatch() =
        assertFailure(results("c1", "c1"), "GLM_DUPLICATE_RESULT_IDS")

    @Test
    fun missingRequestedIdRejectsWholeBatch() =
        assertFailure(results("c1"), "GLM_RESULT_ID_SET_MISMATCH")

    @Test
    fun unexpectedReturnedIdRejectsWholeBatch() =
        assertFailure(results("c1", "other"), "GLM_RESULT_ID_SET_MISMATCH")

    @Test
    fun unauthorizedResponseIsRejected() = assertHttpFailure(401, "HTTP_401_UNAUTHORIZED")

    @Test
    fun rateLimitResponseIsRejected() = assertHttpFailure(429, "HTTP_429_RATE_LIMIT")

    @Test
    fun serverErrorResponseIsRejected() = assertHttpFailure(503, "HTTP_SERVER_ERROR_503")

    private fun assertFailure(content: String, expectedError: String = "GLM_MALFORMED_JSON") = runBlocking {
        server.enqueue(response(content))
        assertRejected(analyzer().analyze(inputs(), config()), expectedError)
    }

    private fun assertHttpFailure(status: Int, expectedError: String) = runBlocking {
        server.enqueue(MockResponse().setResponseCode(status))
        assertRejected(analyzer().analyze(inputs(), config()), expectedError)
    }

    private fun assertRejected(
        result: com.example.commentanalyzer.domain.model.AnalysisBatchResult,
        expectedError: String
    ) {
        assertEquals(expectedError, result.error)
        assertTrue(result.results.isEmpty())
    }

    private fun analyzer(token: String? = "sk-test-key"): OpenRouterGlmDeepAnalyzer {
        val tokenProvider = { token }
        return OpenRouterGlmDeepAnalyzer(
            glmApiService = GlmApiService.create(
                baseUrl = server.url("/").toString(),
                tokenProvider = tokenProvider
            ),
            tokenProvider = tokenProvider
        )
    }

    private fun inputs() = listOf(
        AnalysisInput("c1", "First comment body."),
        AnalysisInput("c2", "Second comment body.")
    )

    private fun config() = AnalysisConfig(1L, "z-ai/glm-5.3-flash")

    private fun response(content: String?, finishReason: String = "stop") = MockResponse()
        .setResponseCode(200)
        .setBody(
            """{"id":"gen-123","model":"z-ai/glm-5.3-flash:free","provider":"test-provider","usage":{"input_tokens":11,"output_tokens":7,"cost":0.001},"choices":[{"finish_reason":${gson.toJson(finishReason)},"message":{"content":${content?.let(gson::toJson) ?: "null"}}}]}"""
        )

    private fun results(
        firstId: String,
        secondId: String? = null,
        schemaVersion: Int = 1,
        category: String = "feedback",
        confidence: String = "0.9"
    ): String {
        val items = listOfNotNull(firstId, secondId).joinToString(",") { id ->
            """{"commentId":${gson.toJson(id)},"category":${gson.toJson(category)},"sentiment":"positive","confidence":$confidence,"summary":"Useful comment."}"""
        }
        return """{"schemaVersion":$schemaVersion,"results":[$items]}"""
    }
}