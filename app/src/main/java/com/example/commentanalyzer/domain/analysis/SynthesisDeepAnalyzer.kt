package com.example.commentanalyzer.domain.analysis

import com.example.commentanalyzer.domain.model.SynthesisBatchResult
import com.example.commentanalyzer.domain.model.SynthesisResult

data class SynthesisEvidenceInput(
    val commentId: String,
    val text: String,
    val category: String?,
    val sentiment: String?,
    val likeCount: Int?,
    val sentimentConfidence: Double?,
    val categoryConfidence: Double?
)

data class SynthesisContext(
    val importId: String,
    val configVersion: Long,
    val totalRelevantComments: Int,
    val categoryDistribution: Map<String, Int>,
    val sentimentDistribution: Map<String, Int>,
    val evidenceItems: List<SynthesisEvidenceInput>
)

interface SynthesisDeepAnalyzer {
    suspend fun synthesize(context: SynthesisContext): SynthesisBatchResult
}
