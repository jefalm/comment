package com.example.commentanalyzer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.entity.BatchItemStatus
import com.example.commentanalyzer.data.local.entity.BatchStatus
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchItemEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ResultStage
import com.example.commentanalyzer.domain.model.AnalysisBatchResult
import com.example.commentanalyzer.domain.model.AnalysisItemResult
import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.model.GateItemResult
import com.example.commentanalyzer.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisPersistenceRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AnalysisPersistenceRepository
    private var now = 100L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AnalysisPersistenceRepository(database) { now }
        runBlocking {
            database.commentDao().insertComments(listOf(comment("comment-1"), comment("comment-2")))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun validatedJevBatchPersistsAtomicallyAndIsIdempotent() = runBlocking {
        prepareBatch(ProcessingStage.JEV_GATE)
        assertTrue(repository.markBatchInFlight(BATCH_ID))
        now = 200L

        assertTrue(
            repository.persistValidatedJevBatch(
                BATCH_ID,
                GateBatchResult(
                    batchId = "remote-jev-request",
                    modelResolved = "typesafe/jev-1.14",
                    provider = "openrouter",
                    inputTokens = 20,
                    outputTokens = 4,
                    costUsd = 0.001,
                    results = listOf(
                        GateItemResult("comment-1", QUESTION_KEY, 0.8, "do-not-persist"),
                        GateItemResult("comment-2", QUESTION_KEY, 0.2, "do-not-persist")
                    ),
                    error = null
                )
            )
        )

        val batch = requireNotNull(database.processingBatchDao().getBatchById(BATCH_ID))
        val items = database.processingBatchDao().getItemsForBatch(BATCH_ID)
        val results = database.analysisResultDao().getResultsForBatch(BATCH_ID)
        assertEquals(BatchStatus.SUCCEEDED, batch.status)
        assertEquals(1, batch.attemptCount)
        assertEquals(100L, batch.startedAt)
        assertEquals(200L, batch.completedAt)
        assertEquals("remote-jev-request", batch.remoteRequestId)
        assertEquals(2, results.size)
        assertEquals(setOf("comment-1", "comment-2"), results.map { it.commentId }.toSet())
        assertTrue(results.all {
            it.stage == ResultStage.JEV_GATE &&
                it.processingConfigVersion == CONFIG_VERSION &&
                it.modelRequested == "typesafe/jev-1.14" &&
                it.modelResolved == "typesafe/jev-1.14" &&
                it.provider == "openrouter" &&
                it.inputTokens == 20L &&
                it.outputTokens == 4L &&
                it.costUsd == 0.001 &&
                it.batchId == BATCH_ID &&
                it.rawPayloadJson == null
        })
        assertEquals(setOf(0.8, 0.2), results.map { it.probability }.toSet())
        assertTrue(items.all { it.status == BatchItemStatus.SUCCEEDED && it.analysisResultId != null })

        assertFalse(
            repository.persistValidatedJevBatch(
                BATCH_ID,
                GateBatchResult(
                    batchId = "replayed-request",
                    modelResolved = "typesafe/jev-1.14",
                    provider = "openrouter",
                    inputTokens = 1,
                    outputTokens = 1,
                    costUsd = 0.0,
                    results = listOf(
                        GateItemResult("comment-1", QUESTION_KEY, 0.1, null),
                        GateItemResult("comment-2", QUESTION_KEY, 0.1, null)
                    ),
                    error = null
                )
            )
        )
        assertEquals(2, database.analysisResultDao().getResultsForBatch(BATCH_ID).size)
    }

    @Test
    fun partialValidatedResponseDoesNotPersistAnyResult() = runBlocking {
        prepareBatch(ProcessingStage.JEV_GATE)
        repository.markBatchInFlight(BATCH_ID)

        try {
            repository.persistValidatedJevBatch(
                BATCH_ID,
                GateBatchResult(
                    batchId = "remote-jev-request",
                    modelResolved = "typesafe/jev-1.14",
                    provider = null,
                    inputTokens = null,
                    outputTokens = null,
                    costUsd = null,
                    results = listOf(GateItemResult("comment-1", QUESTION_KEY, 0.7, null)),
                    error = null
                )
            )
            throw AssertionError("Expected incomplete response to be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(database.analysisResultDao().getResultsForBatch(BATCH_ID).isEmpty())
            assertEquals(
                BatchStatus.IN_FLIGHT,
                database.processingBatchDao().getBatchById(BATCH_ID)?.status
            )
            assertTrue(database.processingBatchDao().getItemsForBatch(BATCH_ID).all {
                it.status == BatchItemStatus.SENT && it.analysisResultId == null
            })
        }
    }

    @Test
    fun opaqueRemoteRequestIdsAllowPunctuationButRejectCredentials() = runBlocking {
        val punctuationBatchId = "batch-punctuation"
        val punctuationId = "provider/gen+id:region?shard=4#part"
        prepareBatch(ProcessingStage.JEV_GATE, punctuationBatchId)
        repository.markBatchInFlight(punctuationBatchId)
        assertTrue(
            repository.persistValidatedJevBatch(
                punctuationBatchId,
                jevResponse(punctuationId)
            )
        )
        assertEquals(
            punctuationId,
            database.processingBatchDao().getBatchById(punctuationBatchId)?.remoteRequestId
        )

        val unsafeResponses = listOf(
            jevResponse("Bearer secret-value"),
            jevResponse("prefix-sk-or-v1-plaintext-key-suffix"),
            jevResponse("{\"Authorization\":\"Basic secret-value\"}"),
            jevResponse("opaque-request-id", modelResolved = "sk-or-v1-plaintext-key"),
            jevResponse(
                "opaque-request-id",
                provider = "sk-or-v1-plaintext-key"
            )
        )
        unsafeResponses.forEachIndexed { index, response ->
            val batchId = "batch-secret-$index"
            prepareBatch(ProcessingStage.JEV_GATE, batchId)
            repository.markBatchInFlight(batchId)
            try {
                repository.persistValidatedJevBatch(batchId, response)
                throw AssertionError("Expected credential-like metadata to be rejected")
            } catch (_: IllegalArgumentException) {
                assertTrue(database.analysisResultDao().getResultsForBatch(batchId).isEmpty())
                assertEquals(
                    BatchStatus.IN_FLIGHT,
                    database.processingBatchDao().getBatchById(batchId)?.status
                )
            }
        }

        val summaryBatchId = "batch-secret-summary"
        prepareBatch(ProcessingStage.GLM_ANALYSIS, summaryBatchId)
        repository.markBatchInFlight(summaryBatchId)
        try {
            repository.persistValidatedGlmBatch(
                summaryBatchId,
                AnalysisBatchResult(
                    batchId = "adapter-batch-id",
                    modelResolved = "z-ai/glm-5.3-flash",
                    provider = "openrouter",
                    inputTokens = null,
                    outputTokens = null,
                    costUsd = null,
                    results = listOf(
                        AnalysisItemResult("comment-1", "feedback", "positive", 0.9, "sk-or-v1-plaintext-key"),
                        AnalysisItemResult("comment-2", "question", "neutral", 0.8, "Asks a question")
                    ),
                    error = null,
                    modelRequested = "z-ai/glm-5.3-flash"
                )
            )
            throw AssertionError("Expected credential-like result metadata to be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(database.analysisResultDao().getResultsForBatch(summaryBatchId).isEmpty())
            assertEquals(
                BatchStatus.IN_FLIGHT,
                database.processingBatchDao().getBatchById(summaryBatchId)?.status
            )
        }
    }

    @Test
    fun validatedGlmBatchPersistsConfidenceAndSchemaVersion() = runBlocking {
        prepareBatch(ProcessingStage.GLM_ANALYSIS)
        repository.markBatchInFlight(BATCH_ID)
        now = 300L

        assertTrue(
            repository.persistValidatedGlmBatch(
                BATCH_ID,
                AnalysisBatchResult(
                    batchId = "adapter-batch-id",
                    modelResolved = "z-ai/glm-5.3-flash",
                    provider = "openrouter",
                    inputTokens = 50,
                    outputTokens = 25,
                    costUsd = 0.002,
                    results = listOf(
                        AnalysisItemResult("comment-1", "feedback", "positive", 0.92, "Useful feedback"),
                        AnalysisItemResult("comment-2", "question", "neutral", 0.77, "Asks a question")
                    ),
                    error = null,
                    modelRequested = "z-ai/glm-5.3-flash",
                    generationId = "generation-123"
                )
            )
        )

        val batch = requireNotNull(database.processingBatchDao().getBatchById(BATCH_ID))
        val results = database.analysisResultDao().getResultsForBatch(BATCH_ID)
        assertEquals(1, batch.responseSchemaVersion)
        assertEquals("generation-123", batch.remoteRequestId)
        assertEquals(2, results.size)
        assertEquals(setOf(0.92, 0.77), results.map { it.confidence }.toSet())
        assertEquals(setOf("feedback", "question"), results.map { it.category }.toSet())
        assertEquals(setOf("positive", "neutral"), results.map { it.sentiment }.toSet())
        assertEquals(setOf("Useful feedback", "Asks a question"), results.map { it.summary }.toSet())
        assertTrue(results.all { it.stage == ResultStage.GLM_ANALYSIS && it.rawPayloadJson == null })
    }

    @Test
    fun failureTransitionKeepsAttemptAndSafeErrorCategory() = runBlocking {
        prepareBatch(ProcessingStage.GLM_ANALYSIS)
        repository.markBatchInFlight(BATCH_ID)
        now = 250L

        repository.recordBatchFailure(
            batchId = BATCH_ID,
            errorCode = "HTTP_429_RATE_LIMIT",
            retryable = true,
            nextAttemptAt = 500L
        )

        val batch = requireNotNull(database.processingBatchDao().getBatchById(BATCH_ID))
        assertEquals(BatchStatus.RETRY_WAIT, batch.status)
        assertEquals(1, batch.attemptCount)
        assertEquals(250L, batch.failedAt)
        assertEquals("HTTP_429_RATE_LIMIT", batch.lastErrorCode)
        assertEquals("HTTP_429_RATE_LIMIT", batch.lastErrorSummary)
        assertEquals(500L, batch.nextAttemptAt)
        assertTrue(database.analysisResultDao().getResultsForBatch(BATCH_ID).isEmpty())
        assertTrue(database.processingBatchDao().getItemsForBatch(BATCH_ID).all {
            it.status == BatchItemStatus.RETRY_WAIT && it.lastErrorCode == "HTTP_429_RATE_LIMIT"
        })

        now = 300L
        assertFalse(repository.markBatchInFlight(BATCH_ID))
        assertEquals(1, database.processingBatchDao().getBatchById(BATCH_ID)?.attemptCount)
        now = 500L
        assertTrue(repository.markBatchInFlight(BATCH_ID))
        assertEquals(2, database.processingBatchDao().getBatchById(BATCH_ID)?.attemptCount)
        assertEquals(250L, database.processingBatchDao().getBatchById(BATCH_ID)?.failedAt)
    }

    private suspend fun prepareBatch(stage: ProcessingStage, batchId: String = BATCH_ID) {
        val state = if (stage == ProcessingStage.JEV_GATE) {
            ProcessingState.READY_JEV
        } else {
            ProcessingState.READY_GLM
        }
        val commentStage = stage
        database.commentDao().updateComment(comment("comment-1").copy(processingState = state, currentStage = commentStage))
        database.commentDao().updateComment(comment("comment-2").copy(processingState = state, currentStage = commentStage))
        repository.createBatch(
            batch(stage, batchId),
            listOf(
                item("$batchId-item-1", batchId, "comment-1", 0, stage),
                item("$batchId-item-2", batchId, "comment-2", 1, stage)
            )
        )
    }

    private fun jevResponse(
        requestId: String,
        modelResolved: String = "typesafe/jev-1.14",
        provider: String? = null
    ) = GateBatchResult(
        batchId = requestId,
        modelResolved = modelResolved,
        provider = provider,
        inputTokens = null,
        outputTokens = null,
        costUsd = null,
        results = listOf(
            GateItemResult("comment-1", QUESTION_KEY, 0.7, null),
            GateItemResult("comment-2", QUESTION_KEY, 0.8, null)
        ),
        error = null
    )

    private fun batch(stage: ProcessingStage, batchId: String) = ProcessingBatchEntity(
        batchId = batchId,
        stage = stage,
        status = BatchStatus.PLANNED,
        parentBatchId = null,
        processingConfigVersion = CONFIG_VERSION,
        modelRequested = if (stage == ProcessingStage.JEV_GATE) "typesafe/jev-1.14" else "z-ai/glm-5.3-flash",
        modelResolved = null,
        requestFingerprintSha256 = "fingerprint",
        requestItemCount = 2,
        requestUtf8Bytes = 100,
        estimatedInputTokens = 25,
        maxOutputTokens = 100,
        attemptCount = 0,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        remoteRequestId = null,
        provider = null,
        inputTokens = null,
        outputTokens = null,
        costUsd = null,
        createdAt = 100,
        updatedAt = 100,
        lastErrorCode = null,
        lastErrorSummary = null,
        importId = IMPORT_ID
    )

    private fun item(id: String, batchId: String, commentId: String, ordinal: Int, stage: ProcessingStage) =
        ProcessingBatchItemEntity(
            batchItemId = id,
            batchId = batchId,
            commentId = commentId,
            ordinal = ordinal,
            questionKey = QUESTION_KEY.takeIf { stage == ProcessingStage.JEV_GATE },
            requestItemId = "request-$ordinal",
            status = BatchItemStatus.PLANNED,
            attemptCount = 0,
            nextAttemptAt = null,
            childBatchId = null,
            analysisResultId = null,
            lastErrorCode = null,
            createdAt = 100,
            updatedAt = 100
        )

    private fun comment(id: String) = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "$IMPORT_ID:scope",
        sourceCommentId = "source-$id",
        parentSourceCommentId = null,
        videoId = "abcdefghijk",
        importId = IMPORT_ID,
        authorDisplayName = null,
        originalText = "comment text",
        normalizedText = "comment text",
        publishedAtEpochMillis = null,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fingerprint-$id",
        processingState = ProcessingState.IMPORTED,
        currentStage = ProcessingStage.INGESTION,
        category = null,
        sentiment = null,
        relevanceScore = null,
        processingConfigVersion = CONFIG_VERSION,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        attemptCountLocal = 0,
        attemptCountJev = 0,
        attemptCountGlm = 0,
        lastErrorCode = null,
        lastErrorSummary = null,
        createdAt = 100,
        updatedAt = 100
    )

    private companion object {
        const val BATCH_ID = "batch-1"
        const val IMPORT_ID = "import-1"
        const val CONFIG_VERSION = 7L
        const val QUESTION_KEY = "comment_quality_gate"
    }
}
