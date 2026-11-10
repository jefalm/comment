package com.example.commentanalyzer.domain.gate

import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput

class DisabledSemanticGate : SemanticGate {
    override suspend fun screen(batch: List<GateInput>, config: GateConfig): GateBatchResult {
        return GateBatchResult(
            batchId = "disabled",
            modelResolved = config.model,
            provider = null,
            inputTokens = null,
            outputTokens = null,
            costUsd = null,
            results = emptyList(),
            error = "SEMANTIC_GATE_DISABLED"
        )
    }
}
