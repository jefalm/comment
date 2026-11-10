package com.example.commentanalyzer.domain.gate

import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import com.example.commentanalyzer.domain.model.GateItemResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticGateContractTest {

    private class FakeSemanticGate : SemanticGate {
        override suspend fun screen(batch: List<GateInput>, config: GateConfig): GateBatchResult {
            val results = batch.map { input ->
                GateItemResult(
                    commentId = input.commentId,
                    questionKey = "q_${input.commentId}",
                    probability = 0.85,
                    rawOutput = null
                )
            }
            return GateBatchResult(
                batchId = "test-batch-1",
                modelResolved = config.model,
                provider = "fake-provider",
                inputTokens = 100,
                outputTokens = 50,
                costUsd = 0.001,
                results = results,
                error = null
            )
        }
    }

    @Test
    fun testFakeSemanticGateContract() = runBlocking {
        val gate = FakeSemanticGate()
        val input = GateInput(commentId = "c1", text = "Great video!", author = "Alice", videoId = "v1")
        val config = GateConfig(configVersion = 1L, model = "typesafe/jev-1.14", rejectThreshold = 0.3, passThreshold = 0.7)

        val result = gate.screen(listOf(input), config)

        assertEquals("test-batch-1", result.batchId)
        assertEquals(1, result.results.size)
        assertEquals("c1", result.results[0].commentId)
        assertEquals(0.85, result.results[0].probability, 0.0001)
    }
}
