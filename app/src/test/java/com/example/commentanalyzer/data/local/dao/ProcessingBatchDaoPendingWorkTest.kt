package com.example.commentanalyzer.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.entity.BatchStatus
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessingBatchDaoPendingWorkTest {
    private lateinit var database: AppDatabase
    private lateinit var processingBatchDao: ProcessingBatchDao
    private lateinit var commentDao: CommentDao

    private val testImportId = "test-import-1"
    private val testConfigVersion = 1L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        processingBatchDao = database.processingBatchDao()
        commentDao = database.commentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createBatch(
        batchId: String,
        stage: ProcessingStage = ProcessingStage.GLM_ANALYSIS,
        status: BatchStatus
    ): ProcessingBatchEntity {
        return ProcessingBatchEntity(
            batchId = batchId,
            stage = stage,
            status = status,
            parentBatchId = null,
            processingConfigVersion = testConfigVersion,
            modelRequested = "test-model",
            modelResolved = null,
            requestFingerprintSha256 = "fingerprint-$batchId",
            requestItemCount = 1,
            requestUtf8Bytes = 100L,
            estimatedInputTokens = 50L,
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
            createdAt = 1000L,
            updatedAt = 1000L,
            lastErrorCode = null,
            lastErrorSummary = null,
            importId = testImportId
        )
    }

    private fun createComment(
        localId: String,
        state: ProcessingState,
        stage: ProcessingStage = ProcessingStage.GLM_ANALYSIS
    ): CommentEntity {
        return CommentEntity(
            localId = localId,
            sourceType = SourceType.YOUTUBE_API,
            sourceScopeKey = "scope",
            sourceCommentId = "source-$localId",
            parentSourceCommentId = null,
            videoId = "video-1",
            importId = testImportId,
            authorDisplayName = "Author",
            originalText = "text",
            normalizedText = "text",
            publishedAtEpochMillis = 1000L,
            publishedAtPrecision = null,
            fingerprintVersion = 1,
            fingerprintSha256 = "fp-$localId",
            processingState = state,
            currentStage = stage,
            category = null,
            sentiment = null,
            relevanceScore = null,
            processingConfigVersion = testConfigVersion,
            leaseOwner = null,
            leaseExpiresAt = null,
            nextAttemptAt = null,
            attemptCountLocal = 0,
            attemptCountJev = 0,
            attemptCountGlm = 0,
            lastErrorCode = null,
            lastErrorSummary = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    @Test
    fun plannedBatchReturnsTrue() = runBlocking {
        processingBatchDao.insertBatch(createBatch("b1", status = BatchStatus.PLANNED))
        assertTrue(processingBatchDao.hasPendingAnalysisBatches(testImportId, testConfigVersion))
    }

    @Test
    fun retryWaitBatchReturnsTrue() = runBlocking {
        processingBatchDao.insertBatch(createBatch("b1", status = BatchStatus.RETRY_WAIT))
        assertTrue(processingBatchDao.hasPendingAnalysisBatches(testImportId, testConfigVersion))
    }

    @Test
    fun inFlightBatchReturnsTrue() = runBlocking {
        processingBatchDao.insertBatch(createBatch("b1", status = BatchStatus.IN_FLIGHT))
        assertTrue(processingBatchDao.hasPendingAnalysisBatches(testImportId, testConfigVersion))
    }

    @Test
    fun readyGlmCommentReturnsTrue() = runBlocking {
        commentDao.insertComments(listOf(createComment("c1", state = ProcessingState.READY_GLM)))
        assertTrue(processingBatchDao.hasPendingAnalysisBatches(testImportId, testConfigVersion))
    }

    @Test
    fun terminalOnlyStatesReturnsFalse() = runBlocking {
        processingBatchDao.insertBatch(createBatch("b1", status = BatchStatus.SUCCEEDED))
        processingBatchDao.insertBatch(createBatch("b2", status = BatchStatus.FAILED_PERMANENT))
        commentDao.insertComments(
            listOf(
                createComment("c1", state = ProcessingState.COMPLETED),
                createComment("c2", state = ProcessingState.FAILED_PERMANENT),
                createComment("c3", state = ProcessingState.LOCAL_REJECTED)
            )
        )
        assertFalse(processingBatchDao.hasPendingAnalysisBatches(testImportId, testConfigVersion))
    }
}
