package com.example.commentanalyzer.domain.analysis

import com.example.commentanalyzer.domain.model.AnalysisBatchResult
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput
import com.example.commentanalyzer.domain.model.AnalysisItemResult

class FakeDeepAnalyzer : DeepAnalyzer {
    override suspend fun analyze(batch: List<AnalysisInput>, config: AnalysisConfig): AnalysisBatchResult {
        val results = batch.map { input ->
            AnalysisItemResult(
                commentId = input.commentId,
                category = "feedback",
                sentiment = "positive",
                confidence = 0.95,
                summary = "Fake analysis summary for ${input.commentId}"
            )
        }
        return AnalysisBatchResult(
            batchId = "fake-analysis-${System.currentTimeMillis()}",
            modelResolved = config.model,
            provider = "fake-provider",
            inputTokens = batch.size * 50L,
            outputTokens = batch.size * 30L,
            costUsd = 0.0002,
            results = results,
            error = null
        )
    }
}
