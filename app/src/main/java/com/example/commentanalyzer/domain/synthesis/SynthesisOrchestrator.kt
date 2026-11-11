package com.example.commentanalyzer.domain.synthesis

import androidx.room.withTransaction
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.entity.CommentSynthesisEntity
import com.example.commentanalyzer.domain.analysis.SynthesisContext
import com.example.commentanalyzer.domain.analysis.SynthesisDeepAnalyzer
import com.example.commentanalyzer.domain.model.SynthesisBatchResult
import com.google.gson.Gson

class SynthesisOrchestrator(
    private val database: AppDatabase,
    private val synthesisAnalyzer: SynthesisDeepAnalyzer,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val gson = Gson()

    suspend fun synthesizeImport(
        importId: String,
        configVersion: Long,
        forceRerun: Boolean = false
    ): SynthesisBatchResult {
        require(importId.isNotBlank())
        val dao = database.commentSynthesisDao()

        if (!forceRerun) {
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

        val evidence = EvidencePlanner.planEvidence(comments)
        val context = SynthesisContext(
            importId = importId,
            configVersion = configVersion,
            totalRelevantComments = totalRelevantComments,
            categoryDistribution = categoryDist,
            sentimentDistribution = sentimentDist,
            evidenceItems = evidence
        )

        val batchResult = synthesisAnalyzer.synthesize(context)
        if (batchResult.error != null || batchResult.synthesis == null) {
            return batchResult.copy(importId = importId, configVersion = configVersion)
        }

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
            payloadJson = gson.toJson(batchResult.synthesis),
            createdAt = clock()
        )

        database.withTransaction {
            dao.insertSynthesis(entity)
        }

        return batchResult.copy(
            synthesisId = synthesisId,
            importId = importId,
            configVersion = configVersion
        )
    }
}
