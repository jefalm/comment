package com.example.commentanalyzer.domain.gate

import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import com.example.commentanalyzer.domain.model.GateItemResult

class FakeSemanticGate(
    private val defaultProbability: Double = 0.85
) : SemanticGate {
    override suspend fun screen(batch: List<GateInput>, config: GateConfig): GateBatchResult {
        val results = batch.map { input ->
            GateItemResult(
                commentId = input.commentId,
                questionKey = "q_${input.commentId}",
                probability = defaultProbability,
                rawOutput = "{\"probability\":$defaultProbability}"
            )
        }
        return GateBatchResult(
            batchId = "fake-batch-${System.currentTimeMillis()}",
            modelResolved = config.model,
            provider = "fake-provider",
            inputTokens = batch.size * 20L,
            outputTokens = batch.size * 10L,
            costUsd = 0.0001,
            results = results,
            error = null
        )
    }
}
