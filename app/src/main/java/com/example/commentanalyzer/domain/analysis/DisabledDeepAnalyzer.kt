package com.example.commentanalyzer.domain.analysis

import com.example.commentanalyzer.domain.model.AnalysisBatchResult
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput

class DisabledDeepAnalyzer : DeepAnalyzer {
    override suspend fun analyze(batch: List<AnalysisInput>, config: AnalysisConfig): AnalysisBatchResult {
        return AnalysisBatchResult(
            batchId = "disabled",
            modelResolved = config.model,
            provider = null,
            inputTokens = null,
            outputTokens = null,
            costUsd = null,
            results = emptyList(),
            error = "DEEP_ANALYZER_DISABLED"
        )
    }
}
