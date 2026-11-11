package com.example.commentanalyzer.network.adapter

import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import com.example.commentanalyzer.network.dto.jev.JevAnswerValue
import com.example.commentanalyzer.network.dto.jev.JevDecisionResponse
import com.example.commentanalyzer.network.jev.JevApiService
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class OpenRouterJevSemanticGateConcurrencyTest {
    @Test
    fun limitsConcurrencyToFourAndKeepsResultsAssociatedWithInputs() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val service = object : JevApiService {
            override suspend fun getDecisions(
                request: com.example.commentanalyzer.network.dto.jev.JevRequest
            ): Response<JevDecisionResponse> {
                val nowActive = active.incrementAndGet()
                maxActive.updateAndGet { previous -> maxOf(previous, nowActive) }
                try {
                    delay(20)
                    val index = request.state.substringAfter("text-").toInt()
                    return Response.success(
                        JevDecisionResponse(
                            id = "response-$index",
                            model = request.model,
                            provider = "fake",
                            usage = null,
                            answers = mapOf(
                                request.questions.keys.single() to JevAnswerValue(
                                    type = "noul",
                                    noul = index / 20.0
                                )
                            )
                        )
                    )
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        val inputs = (0 until 17).map { GateInput("comment-$it", "text-$it", null, null) }

        val result = gate(service).screen(inputs, gateConfig())

        assertEquals(4, maxActive.get())
        assertTrue(maxActive.get() <= OpenRouterJevSemanticGate.MAX_CONCURRENT_JEV_REQUESTS)
        assertEquals(inputs.map { it.commentId }, result.results.map { it.commentId })
        assertEquals((0 until inputs.size).map { it / 20.0 }, result.results.map { it.probability })
        assertEquals(null, result.error)
    }

    @Test
    fun rateLimitFailsWholeBatchWithoutAdapterRetries() = runBlocking {
        val calls = AtomicInteger()
        val service = object : JevApiService {
            override suspend fun getDecisions(
                request: com.example.commentanalyzer.network.dto.jev.JevRequest
            ): Response<JevDecisionResponse> {
                calls.incrementAndGet()
                if (request.state == "text-0") {
                    return Response.error(429, ResponseBody.create(null, "rate limited"))
                }
                return Response.success(
                    JevDecisionResponse(
                        id = "ok",
                        model = request.model,
                        provider = "fake",
                        usage = null,
                        answers = mapOf(
                            request.questions.keys.single() to JevAnswerValue("noul", 0.9)
                        )
                    )
                )
            }
        }
        val inputs = (0 until 7).map { GateInput("comment-$it", "text-$it", null, null) }

        val result = gate(service).screen(inputs, gateConfig())

        assertEquals(7, calls.get())
        assertEquals("HTTP_429_RATE_LIMIT", result.error)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun cancellationPropagatesToInFlightJevCall() = runBlocking {
        val service = object : JevApiService {
            override suspend fun getDecisions(
                request: com.example.commentanalyzer.network.dto.jev.JevRequest
            ): Response<JevDecisionResponse> {
                awaitCancellation()
            }
        }
        val job = launch {
            gate(service).screen(
                listOf(GateInput("comment-1", "comment text", null, null)),
                gateConfig()
            )
        }

        delay(10)
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }

    private fun gate(service: JevApiService) =
        OpenRouterJevSemanticGate(service) { "test-token" }

    private fun gateConfig() = GateConfig(
        configVersion = 1,
        model = "typesafe/jev-1.14",
        rejectThreshold = 0.3,
        passThreshold = 0.7
    )
}
