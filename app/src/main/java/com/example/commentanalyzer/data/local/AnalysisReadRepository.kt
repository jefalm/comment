package com.example.commentanalyzer.data.local

import com.example.commentanalyzer.data.local.dao.AnalysisDistributionCount
import com.example.commentanalyzer.data.local.dao.AnalysisProcessingCounts
import com.example.commentanalyzer.data.local.dao.AnalysisUsageTotals
import com.example.commentanalyzer.data.local.dao.CommentDao
import com.example.commentanalyzer.data.local.dao.ImportDao
import com.example.commentanalyzer.data.local.dao.ProcessingBatchDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class AnalysisImportOption(
    val importId: String,
    val title: String
)

data class AnalysisSummary(
    val processing: AnalysisProcessingCounts,
    val categories: List<AnalysisDistributionCount>,
    val sentiments: List<AnalysisDistributionCount>,
    val usage: AnalysisUsageTotals
)

class AnalysisReadRepository(
    private val commentDao: CommentDao,
    private val importDao: ImportDao,
    private val processingBatchDao: ProcessingBatchDao
) {
    fun observeImports(): Flow<List<AnalysisImportOption>> =
        importDao.observeRecentImportsWithComments().map { imports ->
            imports.map { import ->
                AnalysisImportOption(
                    importId = import.importId,
                    title = import.videoTitle?.takeIf(String::isNotBlank)
                        ?: import.videoId?.takeIf(String::isNotBlank)
                        ?: "Imported comments"
                )
            }
        }

    fun observeConfigVersions(importId: String): Flow<List<Long>> =
        commentDao.observeAnalysisConfigVersions(importId)

    fun observeSummary(importId: String, configVersion: Long): Flow<AnalysisSummary> =
        combine(
            commentDao.observeAnalysisProcessingCounts(importId, configVersion),
            commentDao.observeAnalysisCategoryCounts(importId, configVersion),
            commentDao.observeAnalysisSentimentCounts(importId, configVersion),
            processingBatchDao.observeAnalysisUsageTotals(importId, configVersion)
        ) { processing, categories, sentiments, usage ->
            AnalysisSummary(processing, categories, sentiments, usage)
        }
}
