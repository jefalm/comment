package com.example.commentanalyzer.data.local

import androidx.room.withTransaction
import com.example.commentanalyzer.data.local.entity.AnalysisResultEntity
import com.example.commentanalyzer.data.local.entity.BatchItemStatus
import com.example.commentanalyzer.data.local.entity.BatchStatus
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchItemEntity
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ResultStage
import com.example.commentanalyzer.domain.filter.FilterOutcome
import com.example.commentanalyzer.domain.filter.FilterResult
import com.example.commentanalyzer.domain.model.AnalysisBatchResult
import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.retry.RetryDisposition
import com.example.commentanalyzer.domain.retry.RetryPolicy

class AnalysisPersistenceRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun createBatch(
        batch: ProcessingBatchEntity,
        items: List<ProcessingBatchItemEntity>
    ) = database.withTransaction {
        insertBatch(batch, items)
    }

    suspend fun createBatchIfAbsent(
        batch: ProcessingBatchEntity,
        items: List<ProcessingBatchItemEntity>
    ): Boolean = database.withTransaction {
        val dao = database.processingBatchDao()
        val existing = dao.getBatchById(batch.batchId)
        if (existing != null) {
            check(
                existing.importId == batch.importId &&
                    existing.stage == batch.stage &&
                    existing.processingConfigVersion == batch.processingConfigVersion &&
                    existing.requestFingerprintSha256 == batch.requestFingerprintSha256 &&
                    dao.getItemsForBatch(batch.batchId).map { it.commentId } == items.map { it.commentId }
            ) { "Stable batch ID conflicts with different request data" }
            return@withTransaction false
        }
        insertBatch(batch, items)
        true
    }

    suspend fun persistLocalFilterResults(
        importId: String,
        configVersion: Long,
        decisions: Map<String, FilterResult>
    ) = database.withTransaction {
        require(importId.isNotBlank())
        val commentDao = database.commentDao()
        val comments = commentDao.getCommentsByIds(decisions.keys.toList()).associateBy { it.localId }
        require(comments.size == decisions.size)
        val now = clock()
        val resultRows = mutableListOf<AnalysisResultEntity>()
        val updatedComments = decisions.toSortedMap().map { (commentId, decision) ->
            val comment = requireNotNull(comments[commentId])
            require(comment.importId == importId && comment.processingConfigVersion == configVersion)
            check(comment.processingState == ProcessingState.IMPORTED &&
                comment.currentStage == ProcessingStage.INGESTION) {
                "Only imported comments can receive local-filter results"
            }
            val (state, stage) = when (decision.outcome) {
                FilterOutcome.PASS -> ProcessingState.READY_JEV to ProcessingStage.JEV_GATE
                FilterOutcome.REJECT -> ProcessingState.LOCAL_REJECTED to ProcessingStage.LOCAL_FILTER
                FilterOutcome.REVIEW -> ProcessingState.MANUAL_REVIEW to ProcessingStage.LOCAL_FILTER
            }
            resultRows += AnalysisResultEntity(
                resultId = "local-filter:$configVersion:$commentId",
                commentId = commentId,
                stage = ResultStage.LOCAL_FILTER,
                processingConfigVersion = configVersion,
                modelRequested = LOCAL_FILTER_MODEL,
                modelResolved = LOCAL_FILTER_MODEL,
                provider = null,
                inputTokens = null,
                outputTokens = null,
                costUsd = null,
                passed = decision.outcome == FilterOutcome.PASS,
                probability = null,
                category = null,
                sentiment = null,
                relevanceScore = null,
                summary = decision.ruleIds.joinToString(","),
                rawPayloadJson = null,
                createdAt = now
            )
            comment.copy(
                processingState = state,
                currentStage = stage,
                nextAttemptAt = null,
                lastErrorCode = null,
                lastErrorSummary = null,
                updatedAt = now
            )
        }
        database.analysisResultDao().insertResults(resultRows)
        check(commentDao.updateComments(updatedComments) == updatedComments.size)
    }

    suspend fun markStageCommentsPermanentFailure(
        importId: String,
        configVersion: Long,
        stage: ProcessingStage,
        commentIds: List<String>,
        errorCode: String
    ) = database.withTransaction {
        require(stage == ProcessingStage.JEV_GATE || stage == ProcessingStage.GLM_ANALYSIS)
        require(errorCode.matches(ERROR_CODE))
        val comments = database.commentDao().getCommentsByIds(commentIds)
        require(comments.size == commentIds.size)
        val expectedState = if (stage == ProcessingStage.JEV_GATE) {
            ProcessingState.READY_JEV
        } else {
            ProcessingState.READY_GLM
        }
        check(comments.all {
            it.importId == importId &&
                it.processingConfigVersion == configVersion &&
                it.currentStage == stage &&
                it.processingState == expectedState
        })
        val now = clock()
        check(database.commentDao().updateComments(comments.map {
            it.copy(
                processingState = ProcessingState.FAILED_PERMANENT,
                lastErrorCode = errorCode,
                lastErrorSummary = errorCode,
                updatedAt = now
            )
        }) == comments.size)
    }

    private suspend fun insertBatch(
        batch: ProcessingBatchEntity,
        items: List<ProcessingBatchItemEntity>
    ) {
        require(batch.batchId.isNotBlank())
        require(!batch.importId.isNullOrBlank())
        require(batch.stage == ProcessingStage.JEV_GATE || batch.stage == ProcessingStage.GLM_ANALYSIS)
        require(batch.status == BatchStatus.PLANNED)
        require(batch.attemptCount == 0)
        require(batch.modelRequested.matches(MODEL_ID))
        requireNoCredentialMaterial(batch.modelRequested)
        batch.modelResolved?.let(::requireNoCredentialMaterial)
        batch.provider?.let(::requireNoCredentialMaterial)
        batch.remoteRequestId?.let(::requireSafeRemoteRequestId)
        require(batch.requestItemCount == items.size && items.isNotEmpty())
        require(items.all {
            it.batchId == batch.batchId &&
                it.status == BatchItemStatus.PLANNED &&
                it.analysisResultId == null
        })
        items.mapNotNull { it.questionKey }.forEach(::requireNoCredentialMaterial)
        require(items.map { it.ordinal }.sorted() == items.indices.toList())
        require(items.map { it.commentId }.toSet().size == items.size)

        val comments = database.commentDao().getCommentsByIds(items.map { it.commentId })
        require(comments.size == items.size)
        require(comments.all {
            it.importId == batch.importId &&
                it.processingConfigVersion == batch.processingConfigVersion
        })

        database.processingBatchDao().insertBatch(batch)
        database.processingBatchDao().insertBatchItems(items)
    }

    suspend fun markBatchInFlight(batchId: String): Boolean = database.withTransaction {
        val dao = database.processingBatchDao()
        val batch = requireNotNull(dao.getBatchById(batchId)) { "Unknown processing batch: $batchId" }
        val now = clock()
        if (batch.status == BatchStatus.IN_FLIGHT) {
            if (batch.leaseExpiresAt != null && batch.leaseExpiresAt > now) return@withTransaction false
            val retry = RetryPolicy.evaluate(503, batch.attemptCount)
            transitionBatchFailure(
                batch = batch,
                items = dao.getItemsForBatch(batchId),
                errorCode = "WORKER_INTERRUPTED",
                retryable = retry.disposition == RetryDisposition.RETRYABLE,
                nextAttemptAt = now + retry.delayMillis
            )
            return@withTransaction false
        }
        check(batch.status == BatchStatus.PLANNED || batch.status == BatchStatus.RETRY_WAIT) {
            "Cannot start batch in status ${batch.status}"
        }
        val items = dao.getItemsForBatch(batchId)
        check(items.isNotEmpty() && items.all {
            it.status == BatchItemStatus.PLANNED || it.status == BatchItemStatus.RETRY_WAIT
        }) { "Batch items are not ready to send" }

        if (batch.nextAttemptAt != null && batch.nextAttemptAt > now) return@withTransaction false
        val comments = database.commentDao().getCommentsByIds(items.map { it.commentId })
        val expectedState = if (batch.stage == ProcessingStage.JEV_GATE) {
            ProcessingState.READY_JEV
        } else {
            ProcessingState.READY_GLM
        }
        val inProgressState = if (batch.stage == ProcessingStage.JEV_GATE) {
            ProcessingState.JEV_IN_PROGRESS
        } else {
            ProcessingState.GLM_IN_PROGRESS
        }
        check(comments.size == items.size && comments.all {
            it.importId == batch.importId &&
                it.processingConfigVersion == batch.processingConfigVersion &&
                it.currentStage == batch.stage &&
                (it.processingState == expectedState || it.processingState == ProcessingState.RETRY_WAIT)
        }) { "Batch comments are not eligible for this stage" }
        check(database.commentDao().updateComments(comments.map {
            it.copy(
                processingState = inProgressState,
                nextAttemptAt = null,
                leaseOwner = batch.batchId,
                leaseExpiresAt = now + BATCH_LEASE_MILLIS,
                lastErrorCode = null,
                lastErrorSummary = null,
                updatedAt = now
            )
        }) == comments.size)
        check(dao.updateBatch(
            batch.copy(
                status = BatchStatus.IN_FLIGHT,
                attemptCount = batch.attemptCount + 1,
                startedAt = batch.startedAt ?: now,
                updatedAt = now,
                leaseOwner = batch.batchId,
                leaseExpiresAt = now + BATCH_LEASE_MILLIS,
                nextAttemptAt = null,
                lastErrorCode = null,
                lastErrorSummary = null
            )
        ) == 1)
        check(dao.updateBatchItems(items.map {
            it.copy(
                status = BatchItemStatus.SENT,
                attemptCount = it.attemptCount + 1,
                nextAttemptAt = null,
                lastErrorCode = null,
                updatedAt = now
            )
        }) == items.size)
        true
    }

    suspend fun persistValidatedJevBatch(
        batchId: String,
        response: GateBatchResult,
        rejectThreshold: Double = 0.3,
        passThreshold: Double = 0.7
    ): Boolean = database.withTransaction {
        require(rejectThreshold.isFinite() && passThreshold.isFinite())
        require(rejectThreshold in 0.0..1.0 && passThreshold in 0.0..1.0)
        require(rejectThreshold < passThreshold)
        val batch = requireInFlightBatch(batchId, ProcessingStage.JEV_GATE)
            ?: return@withTransaction false
        check(response.error == null) { "JEV response failed adapter validation: ${response.error}" }
        requireSafeMetadata(response.modelResolved, response.provider, response.inputTokens, response.outputTokens, response.costUsd)

        val items = requireSentItems(batchId)
        val itemByCommentId = requireExactResponseIds(items, response.results.map { it.commentId })
        val byCommentId = response.results.associateBy { it.commentId }
        val resultRows = items.map { item ->
            val validated = requireNotNull(byCommentId[item.commentId])
            check(validated.questionKey.isNotBlank() && validated.questionKey == item.questionKey) {
                "JEV response question does not match the requested item"
            }
            require(validated.probability.isFinite() && validated.probability in 0.0..1.0)
            resultEntity(
                batch = batch,
                item = itemByCommentId.getValue(item.commentId),
                stage = ResultStage.JEV_GATE,
                modelResolved = response.modelResolved,
                provider = response.provider,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens,
                costUsd = response.costUsd,
                passed = validated.probability >= passThreshold,
                probability = validated.probability
            )
        }
        val comments = database.commentDao().getCommentsByIds(items.map { it.commentId })
        check(comments.size == items.size && comments.all {
            it.processingState == ProcessingState.JEV_IN_PROGRESS &&
                it.currentStage == ProcessingStage.JEV_GATE
        }) { "JEV comments are not in progress" }
        val commentUpdates = comments.map { comment ->
            val probability = requireNotNull(byCommentId[comment.localId]).probability
            val (state, stage) = when {
                probability >= passThreshold -> ProcessingState.READY_GLM to ProcessingStage.GLM_ANALYSIS
                probability <= rejectThreshold -> ProcessingState.JEV_REJECTED to ProcessingStage.JEV_GATE
                else -> ProcessingState.MANUAL_REVIEW to ProcessingStage.JEV_GATE
            }
            comment.copy(
                processingState = state,
                currentStage = stage,
                leaseOwner = null,
                leaseExpiresAt = null,
                nextAttemptAt = null,
                updatedAt = clock()
            )
        }
        persistSuccess(
            batch = batch,
            items = items,
            results = resultRows,
            commentUpdates = commentUpdates,
            modelResolved = response.modelResolved,
            provider = response.provider,
            inputTokens = response.inputTokens,
            outputTokens = response.outputTokens,
            costUsd = response.costUsd,
            remoteRequestId = response.batchId,
            responseSchemaVersion = null
        )
    }

    suspend fun persistValidatedGlmBatch(
        batchId: String,
        response: AnalysisBatchResult
    ): Boolean = database.withTransaction {
        val batch = requireInFlightBatch(batchId, ProcessingStage.GLM_ANALYSIS)
            ?: return@withTransaction false
        check(response.error == null) { "GLM response failed adapter validation: ${response.error}" }
        check(response.modelRequested == null || response.modelRequested == batch.modelRequested) {
            "GLM requested model does not match the persisted batch"
        }
        requireSafeMetadata(response.modelResolved, response.provider, response.inputTokens, response.outputTokens, response.costUsd)

        val items = requireSentItems(batchId)
        val itemByCommentId = requireExactResponseIds(items, response.results.map { it.commentId })
        val byCommentId = response.results.associateBy { it.commentId }
        val resultRows = items.map { item ->
            val validated = requireNotNull(byCommentId[item.commentId])
            require(validated.category in GLM_CATEGORIES)
            require(validated.sentiment in GLM_SENTIMENTS)
            require(validated.confidence.isFinite() && validated.confidence in 0.0..1.0)
            require(validated.summary.isNotBlank() && validated.summary.length <= MAX_SUMMARY_LENGTH)
            requireNoCredentialMaterial(validated.summary)
            resultEntity(
                batch = batch,
                item = itemByCommentId.getValue(item.commentId),
                stage = ResultStage.GLM_ANALYSIS,
                modelResolved = response.modelResolved,
                provider = response.provider,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens,
                costUsd = response.costUsd,
                confidence = validated.confidence,
                category = validated.category,
                sentiment = validated.sentiment,
                summary = validated.summary
            )
        }
        val comments = database.commentDao().getCommentsByIds(items.map { it.commentId })
        check(comments.size == items.size && comments.all {
            it.processingState == ProcessingState.GLM_IN_PROGRESS &&
                it.currentStage == ProcessingStage.GLM_ANALYSIS
        }) { "GLM comments are not in progress" }
        val commentUpdates = comments.map { comment ->
            val validated = requireNotNull(byCommentId[comment.localId])
            comment.copy(
                processingState = ProcessingState.COMPLETED,
                currentStage = ProcessingStage.DONE,
                category = validated.category,
                sentiment = validated.sentiment,
                relevanceScore = validated.confidence,
                leaseOwner = null,
                leaseExpiresAt = null,
                nextAttemptAt = null,
                updatedAt = clock()
            )
        }
        persistSuccess(
            batch = batch,
            items = items,
            results = resultRows,
            commentUpdates = commentUpdates,
            modelResolved = response.modelResolved,
            provider = response.provider,
            inputTokens = response.inputTokens,
            outputTokens = response.outputTokens,
            costUsd = response.costUsd,
            remoteRequestId = response.generationId,
            responseSchemaVersion = GLM_SCHEMA_VERSION
        )
    }

    suspend fun recordBatchFailure(
        batchId: String,
        errorCode: String,
        retryable: Boolean,
        nextAttemptAt: Long? = null
    ) = database.withTransaction {
        require(errorCode.matches(ERROR_CODE))
        val dao = database.processingBatchDao()
        val batch = requireNotNull(dao.getBatchById(batchId)) { "Unknown processing batch: $batchId" }
        check(batch.status == BatchStatus.IN_FLIGHT) { "Only an in-flight batch can fail" }
        val items = dao.getItemsForBatch(batchId)
        check(items.isNotEmpty() && items.all { it.status == BatchItemStatus.SENT }) {
            "Batch items are not all in flight"
        }
        transitionBatchFailure(batch, items, errorCode, retryable, nextAttemptAt)
    }

    private suspend fun transitionBatchFailure(
        batch: ProcessingBatchEntity,
        items: List<ProcessingBatchItemEntity>,
        errorCode: String,
        retryable: Boolean,
        nextAttemptAt: Long?
    ) {
        require(errorCode.matches(ERROR_CODE))
        check(items.isNotEmpty() && items.all { it.status == BatchItemStatus.SENT })
        val now = clock()
        val dao = database.processingBatchDao()
        val nextStatus = if (retryable) BatchStatus.RETRY_WAIT else BatchStatus.FAILED_PERMANENT
        val nextItemStatus = if (retryable) BatchItemStatus.RETRY_WAIT else BatchItemStatus.FAILED_PERMANENT
        val comments = database.commentDao().getCommentsByIds(items.map { it.commentId })
        val inProgressState = if (batch.stage == ProcessingStage.JEV_GATE) {
            ProcessingState.JEV_IN_PROGRESS
        } else {
            ProcessingState.GLM_IN_PROGRESS
        }
        check(comments.size == items.size && comments.all { it.processingState == inProgressState })
        check(database.commentDao().updateComments(comments.map {
            it.copy(
                processingState = if (retryable) ProcessingState.RETRY_WAIT else ProcessingState.FAILED_PERMANENT,
                nextAttemptAt = nextAttemptAt.takeIf { retryable },
                leaseOwner = null,
                leaseExpiresAt = null,
                lastErrorCode = errorCode,
                lastErrorSummary = errorCode,
                updatedAt = now
            )
        }) == comments.size)
        check(dao.updateBatch(
            batch.copy(
                status = nextStatus,
                failedAt = now,
                nextAttemptAt = nextAttemptAt.takeIf { retryable },
                updatedAt = now,
                leaseOwner = null,
                leaseExpiresAt = null,
                lastErrorCode = errorCode,
                lastErrorSummary = errorCode
            )
        ) == 1)
        check(dao.updateBatchItems(items.map {
            it.copy(
                status = nextItemStatus,
                nextAttemptAt = nextAttemptAt.takeIf { retryable },
                lastErrorCode = errorCode,
                updatedAt = now
            )
        }) == items.size)
    }

    private suspend fun requireInFlightBatch(
        batchId: String,
        expectedStage: ProcessingStage
    ): ProcessingBatchEntity? {
        val batch = requireNotNull(database.processingBatchDao().getBatchById(batchId)) {
            "Unknown processing batch: $batchId"
        }
        check(batch.stage == expectedStage) { "Batch stage does not match the validated response" }
        if (batch.status == BatchStatus.SUCCEEDED) return null
        check(batch.status == BatchStatus.IN_FLIGHT) { "Batch is not in flight" }
        return batch
    }

    private suspend fun requireSentItems(batchId: String): List<ProcessingBatchItemEntity> {
        val items = database.processingBatchDao().getItemsForBatch(batchId)
        check(items.isNotEmpty() && items.all {
            it.status == BatchItemStatus.SENT && it.analysisResultId == null
        }) { "Batch items are not all ready for validated results" }
        return items
    }

    private fun requireExactResponseIds(
        items: List<ProcessingBatchItemEntity>,
        responseIds: List<String>
    ): Map<String, ProcessingBatchItemEntity> {
        val itemByCommentId = items.associateBy { it.commentId }
        require(responseIds.size == items.size)
        require(responseIds.toSet().size == responseIds.size)
        require(responseIds.toSet() == itemByCommentId.keys) {
            "Validated response item IDs do not match the batch"
        }
        return itemByCommentId
    }

    private suspend fun persistSuccess(
        batch: ProcessingBatchEntity,
        items: List<ProcessingBatchItemEntity>,
        results: List<AnalysisResultEntity>,
        commentUpdates: List<CommentEntity>,
        modelResolved: String,
        provider: String?,
        inputTokens: Long?,
        outputTokens: Long?,
        costUsd: Double?,
        remoteRequestId: String?,
        responseSchemaVersion: Int?
    ): Boolean {
        check(results.size == items.size)
        requireSafeRemoteRequestId(remoteRequestId)
        val dao = database.processingBatchDao()
        val resultDao = database.analysisResultDao()
        val now = clock()
        resultDao.insertResults(results)
        check(database.commentDao().updateComments(commentUpdates) == commentUpdates.size)
        check(dao.updateBatchItems(items.mapIndexed { index, item ->
            item.copy(
                status = BatchItemStatus.SUCCEEDED,
                analysisResultId = results[index].resultId,
                updatedAt = now
            )
        }) == items.size)
        check(dao.updateBatch(
            batch.copy(
                status = BatchStatus.SUCCEEDED,
                modelResolved = modelResolved,
                provider = provider,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                costUsd = costUsd,
                remoteRequestId = remoteRequestId,
                responseSchemaVersion = responseSchemaVersion,
                completedAt = now,
                updatedAt = now,
                leaseOwner = null,
                leaseExpiresAt = null,
                nextAttemptAt = null,
                lastErrorCode = null,
                lastErrorSummary = null
            )
        ) == 1)
        return true
    }

    private fun resultEntity(
        batch: ProcessingBatchEntity,
        item: ProcessingBatchItemEntity,
        stage: ResultStage,
        modelResolved: String,
        provider: String?,
        inputTokens: Long?,
        outputTokens: Long?,
        costUsd: Double?,
        probability: Double? = null,
        passed: Boolean? = null,
        confidence: Double? = null,
        category: String? = null,
        sentiment: String? = null,
        summary: String? = null
    ) = AnalysisResultEntity(
        resultId = "analysis:${item.batchItemId}",
        commentId = item.commentId,
        stage = stage,
        processingConfigVersion = batch.processingConfigVersion,
        modelRequested = batch.modelRequested,
        modelResolved = modelResolved,
        provider = provider,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        costUsd = costUsd,
        passed = passed,
        probability = probability,
        category = category,
        sentiment = sentiment,
        relevanceScore = null,
        summary = summary,
        rawPayloadJson = null,
        createdAt = clock(),
        batchId = batch.batchId,
        confidence = confidence
    )

    private fun requireSafeMetadata(
        modelResolved: String,
        provider: String?,
        inputTokens: Long?,
        outputTokens: Long?,
        costUsd: Double?
    ) {
        require(modelResolved.matches(MODEL_ID))
        require(provider == null || provider.matches(PROVIDER_ID))
        requireNoCredentialMaterial(modelResolved)
        provider?.let(::requireNoCredentialMaterial)
        require(inputTokens == null || inputTokens >= 0)
        require(outputTokens == null || outputTokens >= 0)
        require(costUsd == null || costUsd.isFinite() && costUsd >= 0.0)
    }

    private fun requireSafeRemoteRequestId(remoteRequestId: String?) {
        if (remoteRequestId == null) return
        requireNoCredentialMaterial(remoteRequestId)
    }

    private fun requireNoCredentialMaterial(value: String) {
        require(
            !AUTHORIZATION_HEADER.containsMatchIn(value) &&
                !BEARER_CREDENTIAL.containsMatchIn(value) &&
                !OPENROUTER_API_KEY.containsMatchIn(value)
        ) { "Persistence metadata contains credential-like content" }
    }

    private companion object {
        val MODEL_ID = Regex("[A-Za-z0-9._/-]{1,128}")
        val PROVIDER_ID = Regex("[A-Za-z0-9._-]{1,128}")
        val AUTHORIZATION_HEADER = Regex("(?i)\\bauthorization\\b\\s*[\"']?\\s*[:=]")
        val BEARER_CREDENTIAL = Regex("(?i)\\bbearer\\b\\s+\\S+")
        val OPENROUTER_API_KEY = Regex("(?i)sk-or-v1-")
        val ERROR_CODE = Regex("[A-Z0-9][A-Z0-9_]{0,63}")
        val GLM_CATEGORIES = setOf("feedback", "question", "criticism", "praise", "spam", "other")
        val GLM_SENTIMENTS = setOf("positive", "neutral", "negative", "mixed")
        const val MAX_SUMMARY_LENGTH = 500
        const val GLM_SCHEMA_VERSION = 1
        const val LOCAL_FILTER_MODEL = "local-filter/v1"
        const val BATCH_LEASE_MILLIS = 15 * 60 * 1000L
    }
}
