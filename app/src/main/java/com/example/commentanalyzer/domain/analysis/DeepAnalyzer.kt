package com.example.commentanalyzer.domain.analysis

import com.example.commentanalyzer.domain.model.AnalysisBatchResult
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput

interface DeepAnalyzer {
    suspend fun analyze(batch: List<AnalysisInput>, config: AnalysisConfig): AnalysisBatchResult
}
