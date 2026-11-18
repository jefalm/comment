package com.example.commentanalyzer.domain.synthesis

import androidx.room.withTransaction
import com.example.commentanalyzer.data.local.AnalysisPersistenceRepository
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.entity.CommentSynthesisEntity
import com.example.commentanalyzer.domain.analysis.SynthesisContext
import com.example.commentanalyzer.domain.analysis.SynthesisDeepAnalyzer
import com.example.commentanalyzer.domain.model.SynthesisBatchResult
import com.google.gson.Gson

class SynthesisOrchestrator(
    private val database: AppDatabase,
    private val synthesisAnalyzer: SynthesisDeepAnalyzer,
    private val persistence: AnalysisPersistenceRepository = AnalysisPersistenceRepository(database),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val gson = Gson()

    suspend fun synthesizeImport(
        importId: String,
        configVersion: Long,
        forceRerun: Boolean = false,
        isCalibrationRun: Boolean = false
    ): SynthesisBatchResult {
        require(importId.isNotBlank())
        val dao = database.commentSynthesisDao()

        if (!forceRerun && !isCalibrationRun) {
            val existing = dao.getSynthesis(importId, configVersion)
            if (existing != null) {
                val parsedSynthesis = try {
                    gson.fromJson(existing.payloadJson, com.example.commentanalyzer.domain.model.SynthesisResult::class.java)
                } catch (_: Exception) {
                    null
                }
                if (parsedSynthesis != null) {
                    return SynthesisBatchResult(
                        synthesisId = existing.synthesisId,
                        importId = existing.importId,
                        configVersion = existing.configVersion,
                        modelResolved = existing.modelResolved ?: existing.modelRequested,
                        provider = existing.provider,
                        inputTokens = existing.inputTokens,
                        outputTokens = existing.outputTokens,
                        costUsd = existing.costUsd,
                        synthesis = parsedSynthesis,
                        error = null,
                        modelRequested = existing.modelRequested,
                        generationId = null
                    )
                }
            }
        }

        // Config-scoped exact population from DAO
        val comments = database.commentDao().getCommentsForSynthesis(importId, configVersion)
        if (comments.isEmpty()) {
            return SynthesisBatchResult(
                synthesisId = "synthesis:$importId:$configVersion",
                importId = importId,
                configVersion = configVersion,
                modelResolved = "z-ai/glm-5.3-flash",
                provider = null,
                inputTokens = null,
                outputTokens = null,
                costUsd = null,
                synthesis = null,
                error = "GLM_EMPTY_IMPORT",
                modelRequested = "z-ai/glm-5.3-flash",
                generationId = null
            )
        }

        val totalRelevantComments = comments.size
        val categoryDist = comments.groupingBy { it.category ?: "unlabeled" }.eachCount()
        val sentimentDist = comments.groupingBy { it.sentiment ?: "unlabeled" }.eachCount()

        val evidence = EvidencePlanner.planEvidence(comments, isCalibrationRun)

        // Wire exposure telemetry for the exact import/config run
        val candidateIds = comments.map { it.localId }
        val selectedIds = evidence.map { it.commentId }
        val evidencePositions = evidence.mapIndexed { index, item -> item.commentId to index }.toMap()
        persistence.updateEvidenceTelemetry(candidateIds, selectedIds, evidencePositions)

        val detectedLanguage = SynthesisLanguageDetector.detectLanguage(
            evidence.map { it.text }.ifEmpty { comments.map { it.originalText } }
        )
        val context = SynthesisContext(
            importId = importId,
            configVersion = configVersion,
            totalRelevantComments = totalRelevantComments,
            categoryDistribution = categoryDist,
            sentimentDistribution = sentimentDist,
            evidenceItems = evidence,
            targetLanguage = detectedLanguage.code
        )

        val batchResult = synthesisAnalyzer.synthesize(context)
        if (batchResult.error != null || batchResult.synthesis == null) {
            return batchResult.copy(importId = importId, configVersion = configVersion)
        }

        val commentsMap = comments.associateBy { it.localId }
        val rawSynthesis = batchResult.synthesis
        val hydratedThemesV3 = mutableListOf<com.example.commentanalyzer.domain.model.SynthesisThemeV3>()
        for (theme in rawSynthesis.themesV3) {
            val repComment = commentsMap[theme.representativeCommentId]
            val quote = repComment?.originalText?.takeIf(String::isNotBlank)
            if (quote == null) {
                return batchResult.copy(
                    importId = importId,
                    configVersion = configVersion,
                    synthesis = null,
                    error = "SYNTHESIS_REPRESENTATIVE_COMMENT_NOT_FOUND"
                )
            }
            hydratedThemesV3 += theme.copy(
                representativeQuote = quote,
                representativeAuthor = repComment.authorDisplayName
            )
        }
        val hydratedSynthesis = rawSynthesis.copy(themesV3 = hydratedThemesV3)

        val synthesisId = "synthesis:$importId:$configVersion"
        val entity = CommentSynthesisEntity(
            synthesisId = synthesisId,
            importId = importId,
            configVersion = configVersion,
            modelRequested = batchResult.modelRequested ?: "z-ai/glm-5.3-flash",
            modelResolved = batchResult.modelResolved,
            provider = batchResult.provider,
            inputTokens = batchResult.inputTokens,
            outputTokens = batchResult.outputTokens,
            costUsd = batchResult.costUsd,
            payloadJson = gson.toJson(hydratedSynthesis),
            createdAt = clock()
        )

        database.withTransaction {
            dao.insertSynthesis(entity)
        }

        return batchResult.copy(
            synthesisId = synthesisId,
            importId = importId,
            configVersion = configVersion,
            synthesis = hydratedSynthesis
        )
    }
}
