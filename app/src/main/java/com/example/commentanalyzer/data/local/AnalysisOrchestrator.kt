package com.example.commentanalyzer.data.local

import com.example.commentanalyzer.data.local.entity.BatchItemStatus
import com.example.commentanalyzer.data.local.entity.BatchStatus
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchItemEntity
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.analysis.DeepAnalyzer
import com.example.commentanalyzer.domain.batching.BatchPlanner
import com.example.commentanalyzer.domain.batching.BatchLimits
import com.example.commentanalyzer.domain.filter.LocalFilter
import com.example.commentanalyzer.domain.gate.SemanticGate
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import com.example.commentanalyzer.domain.retry.RetryDisposition
import com.example.commentanalyzer.domain.retry.RetryPolicy
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

data class RemoteProcessingReadiness(
    val hasOpenRouterKey: Boolean,
    val remoteProcessingConsent: Boolean
) {
    val isReady: Boolean
        get() = hasOpenRouterKey && remoteProcessingConsent
}

data class AnalysisRunResult(
    val locallyProcessedComments: Int,
    val remoteProcessingBlocked: Boolean,
    val attemptedJevBatches: Int,
    val attemptedGlmBatches: Int
)

class AnalysisOrchestrator(
    private val database: AppDatabase,
    private val persistence: AnalysisPersistenceRepository,
    private val semanticGate: SemanticGate,
    private val deepAnalyzer: DeepAnalyzer,
    private val readiness: suspend () -> RemoteProcessingReadiness,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val telemetryRecorder: TelemetryRecorder? = null
) {
    suspend fun run(
        importId: String,
        config: ProcessingConfigEntity
    ): AnalysisRunResult {
        require(importId.isNotBlank())
        require(config.jevModelRequested == JEV_MODEL)
        require(config.glmModelRequested == GLM_MODEL)
        require(config.jevRejectThreshold.isFinite() && config.jevPassThreshold.isFinite())
        require(config.jevRejectThreshold in 0.0..1.0 && config.jevPassThreshold in 0.0..1.0)
        require(config.jevRejectThreshold < config.jevPassThreshold)

        val startRunTime = clock()
        val imported = database.commentDao().getImportedCommentsForAnalysis(
            importId = importId,
            configVersion = config.configVersion,
            limit = MAX_COMMENTS_PER_RUN
        )
        if (imported.isNotEmpty()) {
            telemetryRecorder?.recordEvent(TelemetryEvent.Discovered(imported.size))
            val evaluations = imported.associate { it.localId to LocalFilter.evaluate(it.normalizedText) }
            val rejected = evaluations.values.count { it.outcome == com.example.commentanalyzer.domain.filter.FilterOutcome.REJECT }
            val reviewed = evaluations.values.count { it.outcome == com.example.commentanalyzer.domain.filter.FilterOutcome.REVIEW }
            val passed = evaluations.values.count { it.outcome == com.example.commentanalyzer.domain.filter.FilterOutcome.PASS }
            telemetryRecorder?.recordEvent(TelemetryEvent.LocalFilter(rejected, reviewed, passed))
            persistence.persistLocalFilterResults(
                importId,
                config.configVersion,
                evaluations
            )
        }

        if (!readiness().isReady) {
            val totalMs = clock() - startRunTime
            telemetryRecorder?.recordEvent(TelemetryEvent.Timing(0L, 0L, 0L, totalMs, totalMs))
            return AnalysisRunResult(imported.size, true, 0, 0)
        }

        val jevCount = processStage(
            importId = importId,
            config = config,
            stage = ProcessingStage.JEV_GATE,
            state = ProcessingState.READY_JEV
        )
        val glmCount = processStage(
            importId = importId,
            config = config,
            stage = ProcessingStage.GLM_ANALYSIS,
            state = ProcessingState.READY_GLM
        )
        val totalMs = clock() - startRunTime
        telemetryRecorder?.recordEvent(TelemetryEvent.Timing(0L, 0L, 0L, totalMs, totalMs))
        return AnalysisRunResult(imported.size, false, jevCount, glmCount)
    }

    private suspend fun processStage(
        importId: String,
        config: ProcessingConfigEntity,
        stage: ProcessingStage,
        state: ProcessingState
    ): Int {
        val now = clock()
        val batchDao = database.processingBatchDao()
        var attempted = 0
        val existing = batchDao.getRunnableBatches(
            importId,
            config.configVersion,
            stage,
            now,
            MAX_COMMENTS_PER_RUN
        )
        for (batch in existing) {
            if (processBatch(batch, config)) attempted++
        }

        val comments = database.commentDao().getCommentsForAnalysisStage(
            importId = importId,
            configVersion = config.configVersion,
            state = state,
            stage = stage,
            now = clock(),
            limit = MAX_COMMENTS_PER_RUN
        )
        if (comments.isEmpty()) return attempted

        val limits = if (stage == ProcessingStage.JEV_GATE) {
            BatchPlanner.JEV_LIMITS
        } else {
            BatchPlanner.GLM_LIMITS
        }
        val eligible = mutableListOf<CommentEntity>()
        val oversized = mutableListOf<String>()
        comments.forEach { comment ->
            if (exceedsSingleItemLimits(comment.normalizedText, limits)) {
                oversized += comment.localId
            } else {
                eligible += comment
            }
        }
        if (oversized.isNotEmpty()) {
            persistence.markStageCommentsPermanentFailure(
                importId,
                config.configVersion,
                stage,
                oversized,
                "REQUEST_ITEM_EXCEEDS_BATCH_LIMIT"
            )
        }
        if (eligible.isEmpty()) return attempted

        val byId = eligible.associateBy { it.localId }
        val planned = BatchPlanner.planBatches(
            eligible.map { BatchPlanner.Item(it.localId, it.normalizedText) },
            limits
        )
        for (plannedItems in planned) {
            val batchComments = plannedItems.map { byId.getValue(it.id) }
            val (batch, items) = createBatch(importId, config, stage, batchComments, limits)
            persistence.createBatchIfAbsent(batch, items)
            val stored = requireNotNull(batchDao.getBatchById(batch.batchId))
            if (processBatch(stored, config)) attempted++
        }
        return attempted
    }

    private suspend fun createBatch(
        importId: String,
        config: ProcessingConfigEntity,
        stage: ProcessingStage,
        comments: List<CommentEntity>,
        limits: BatchLimits
    ): Pair<ProcessingBatchEntity, List<ProcessingBatchItemEntity>> {
        val ordered = comments.sortedBy { it.localId }
        val fingerprint = sha256(buildString {
            append(stage.name).append('|').append(config.configVersion)
            ordered.forEach { append('|').append(it.localId).append(':').append(it.normalizedText) }
        })
        val batchId = "analysis-${stage.name.lowercase()}-${fingerprint.take(32)}"
        val now = clock()
        val items = ordered.mapIndexed { index, comment ->
            ProcessingBatchItemEntity(
                batchItemId = "$batchId:item:${sha256(comment.localId).take(16)}",
                batchId = batchId,
                commentId = comment.localId,
                ordinal = index,
                questionKey = GATE_QUESTION_KEY.takeIf { stage == ProcessingStage.JEV_GATE },
                requestItemId = comment.localId,
                status = BatchItemStatus.PLANNED,
                attemptCount = 0,
                nextAttemptAt = null,
                childBatchId = null,
                analysisResultId = null,
                lastErrorCode = null,
                createdAt = now,
                updatedAt = now
            )
        }
        val bytes = ordered.sumOf { it.normalizedText.toByteArray(Charsets.UTF_8).size.toLong() }
        val tokens = ordered.sumOf { (it.normalizedText.length / 4).coerceAtLeast(1).toLong() }
        val batch = ProcessingBatchEntity(
            batchId = batchId,
            stage = stage,
            status = BatchStatus.PLANNED,
            parentBatchId = null,
            processingConfigVersion = config.configVersion,
            modelRequested = if (stage == ProcessingStage.JEV_GATE) config.jevModelRequested else config.glmModelRequested,
            modelResolved = null,
            requestFingerprintSha256 = fingerprint,
            requestItemCount = ordered.size,
            requestUtf8Bytes = bytes,
            estimatedInputTokens = tokens,
            maxOutputTokens = null,
            attemptCount = 0,
            leaseOwner = null,
            leaseExpiresAt = null,
            nextAttemptAt = null,
            remoteRequestId = null,
            provider = null,
            inputTokens = null,
            outputTokens = null,
            costUsd = null,
            createdAt = now,
            updatedAt = now,
            lastErrorCode = null,
            lastErrorSummary = null,
            importId = importId
        )
        check(ordered.size <= limits.maxCount && bytes <= limits.maxBytes && tokens <= limits.maxTokens)
        return batch to items
    }

    private suspend fun processBatch(
        batch: ProcessingBatchEntity,
        config: ProcessingConfigEntity
    ): Boolean {
        if (!persistence.markBatchInFlight(batch.batchId)) return false
        val items = database.processingBatchDao().getItemsForBatch(batch.batchId)
        val commentsById = database.commentDao()
            .getCommentsByIds(items.map { it.commentId })
            .associateBy { it.localId }
        val ordered = items.map { requireNotNull(commentsById[it.commentId]) }
        val startNet = clock()
        when (batch.stage) {
            ProcessingStage.JEV_GATE -> {
                val response = try {
                    semanticGate.screen(
                        ordered.map {
                            GateInput(it.localId, it.normalizedText, it.authorDisplayName, it.videoId)
                        },
                        GateConfig(
                            configVersion = config.configVersion,
                            model = config.jevModelRequested,
                            rejectThreshold = config.jevRejectThreshold,
                            passThreshold = config.jevPassThreshold
                        )
                    )
                } catch (cancelled: CancellationException) {
                    telemetryRecorder?.recordEvent(TelemetryEvent.Cancellation(1))
                    releaseForResume(batch.batchId)
                    throw cancelled
                } catch (_: IOException) {
                    val netTime = clock() - startNet
                    telemetryRecorder?.recordEvent(TelemetryEvent.Failure(1))
                    recordFailure(batch.batchId, "NETWORK_ERROR")
                    return true
                }
                val netTime = clock() - startNet
                if (response.error != null) {
                    telemetryRecorder?.recordEvent(TelemetryEvent.Failure(1))
                    recordFailure(batch.batchId, response.error)
                } else {
                    val rejected = response.results.count { it.probability < config.jevRejectThreshold }
                    val passed = response.results.count { it.probability >= config.jevRejectThreshold }
                    telemetryRecorder?.recordEvent(
                        TelemetryEvent.JevBatch(
                            requested = ordered.size,
                            completed = response.results.size,
                            rejected = rejected,
                            passed = passed,
                            networkTimeMs = netTime,
                            bytes = batch.requestUtf8Bytes,
                            estTokens = batch.estimatedInputTokens,
                            actTokens = (response.inputTokens ?: 0L) + (response.outputTokens ?: 0L),
                            cost = response.costUsd ?: 0.0,
                            httpStatus = 200,
                            model = response.modelResolved
                        )
                    )
                    persistence.persistValidatedJevBatch(
                        batch.batchId,
                        response,
                        config.jevRejectThreshold,
                        config.jevPassThreshold
                    )
                }
            }

            ProcessingStage.GLM_ANALYSIS -> {
                val response = try {
                    deepAnalyzer.analyze(
                        ordered.map { AnalysisInput(it.localId, it.normalizedText) },
                        AnalysisConfig(config.configVersion, config.glmModelRequested)
                    )
                } catch (cancelled: CancellationException) {
                    telemetryRecorder?.recordEvent(TelemetryEvent.Cancellation(1))
                    releaseForResume(batch.batchId)
                    throw cancelled
                } catch (_: IOException) {
                    val netTime = clock() - startNet
                    telemetryRecorder?.recordEvent(TelemetryEvent.Failure(1))
                    recordFailure(batch.batchId, "NETWORK_ERROR")
                    return true
                }
                val netTime = clock() - startNet
                if (response.error != null) {
                    telemetryRecorder?.recordEvent(TelemetryEvent.Failure(1))
                    recordFailure(batch.batchId, response.error)
                } else {
                    telemetryRecorder?.recordEvent(
                        TelemetryEvent.GlmBatch(
                            requested = ordered.size,
                            completed = response.results.size,
                            networkTimeMs = netTime,
                            bytes = batch.requestUtf8Bytes,
                            estTokens = batch.estimatedInputTokens,
                            actTokens = (response.inputTokens ?: 0L) + (response.outputTokens ?: 0L),
                            cost = response.costUsd ?: 0.0,
                            httpStatus = 200,
                            model = response.modelResolved
                        )
                    )
                    persistence.persistValidatedGlmBatch(batch.batchId, response)
                }
            }

            else -> error("Unsupported analysis stage: ${batch.stage}")
        }
        return true
    }

    private suspend fun recordFailure(batchId: String, rawError: String) {
        val errorCode = rawError.substringBefore(':').trim().uppercase().takeIf {
            ERROR_CODE.matches(it)
        } ?: "ADAPTER_ERROR"
        val batch = requireNotNull(database.processingBatchDao().getBatchById(batchId))
        val statusCode = when {
            errorCode == "NETWORK_ERROR" -> 503
            errorCode == "GLM_OUTPUT_TRUNCATED" -> 503
            errorCode == "HTTP_429_RATE_LIMIT" -> 429
            errorCode == "HTTP_401_UNAUTHORIZED" -> 401
            errorCode.startsWith("HTTP_SERVER_ERROR_") ->
                errorCode.substringAfterLast('_').toIntOrNull() ?: 500
            errorCode.startsWith("HTTP_ERROR_") ->
                errorCode.substringAfterLast('_').toIntOrNull() ?: 400
            else -> 400
        }
        val decision = RetryPolicy.evaluate(statusCode, batch.attemptCount, random = random)
        val retryable = decision.disposition == RetryDisposition.RETRYABLE
        if (retryable) {
            telemetryRecorder?.recordEvent(TelemetryEvent.Retry(1))
        }
        persistence.recordBatchFailure(
            batchId = batchId,
            errorCode = errorCode,
            retryable = retryable,
            nextAttemptAt = (clock() + decision.delayMillis).takeIf { retryable }
        )
    }

    private suspend fun releaseForResume(batchId: String) {
        persistence.recordBatchFailure(
            batchId = batchId,
            errorCode = "CANCELLED",
            retryable = true,
            nextAttemptAt = clock()
        )
    }

    private fun exceedsSingleItemLimits(text: String, limits: BatchLimits): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8).size.toLong()
        val tokens = (text.length / 4).coerceAtLeast(1).toLong()
        return bytes > limits.maxBytes || tokens > limits.maxTokens
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val JEV_MODEL = "typesafe/jev-1.14"
        const val GLM_MODEL = "z-ai/glm-5.3-flash"
        const val GATE_QUESTION_KEY = "comment_quality_gate"
        const val MAX_COMMENTS_PER_RUN = 5_000
        val ERROR_CODE = Regex("[A-Z0-9][A-Z0-9_]{0,63}")
    }
}
