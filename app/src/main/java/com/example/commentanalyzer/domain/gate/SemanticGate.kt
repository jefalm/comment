package com.example.commentanalyzer.domain.gate

import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput

interface SemanticGate {
    suspend fun screen(batch: List<GateInput>, config: GateConfig): GateBatchResult
}
