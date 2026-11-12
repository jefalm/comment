package com.example.commentanalyzer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.entity.AnalysisResultEntity
import com.example.commentanalyzer.data.local.entity.BatchStatus
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ResultStage
import com.example.commentanalyzer.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisReadRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AnalysisReadRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AnalysisReadRepository(
            database.commentDao(),
            database.importDao(),
            database.processingBatchDao(),
            database.commentSynthesisDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun processingAndValidatedResultAggregatesAreScopedByImportAndConfig() = runBlocking {
        database.importDao().insertImport(import("import-a", "Video A", 1_000L))
        database.importDao().insertImport(import("import-b", "Video B", 2_000L))
        database.commentDao().insertComments(
            listOf(
                comment("completed", "import-a", 7, ProcessingState.COMPLETED, "feedback", "positive"),
                comment("local", "import-a", 7, ProcessingState.LOCAL_REJECTED),
                comment("jev", "import-a", 7, ProcessingState.JEV_REJECTED),
                comment("retry", "import-a", 7, ProcessingState.RETRY_WAIT),
                comment("failed", "import-a", 7, ProcessingState.FAILED_PERMANENT),
                comment("unvalidated", "import-a", 7, ProcessingState.COMPLETED, "unvalidated", "negative"),
                comment("other-config", "import-a", 8, ProcessingState.COMPLETED, "question", "neutral"),
                comment("other-import", "import-b", 7, ProcessingState.COMPLETED, "question", "neutral")
            )
        )
        database.analysisResultDao().insertResults(
            listOf(
                result("glm-valid", "completed", 7, ResultStage.GLM_ANALYSIS),
                result("jev-only", "jev", 7, ResultStage.JEV_GATE, category = "not-a-category"),
                result("glm-other-config", "other-config", 8, ResultStage.GLM_ANALYSIS),
                result("glm-other-import", "other-import", 7, ResultStage.GLM_ANALYSIS)
            )
        )

        val summary = repository.observeSummary("import-a", 7).first()

        assertEquals(6L, summary.processing.totalComments)
        assertEquals(1L, summary.processing.locallyRejected)
        assertEquals(1L, summary.processing.jevRejected)
        assertEquals(1L, summary.processing.retrying)
        assertEquals(1L, summary.processing.permanentlyFailed)
        assertEquals(1L, summary.processing.completed)
        assertEquals(listOf("feedback" to 1L), summary.categories.map { it.value to it.count })
        assertEquals(listOf("positive" to 1L), summary.sentiments.map { it.value to it.count })
        assertEquals(listOf(8L, 7L), repository.observeConfigVersions("import-a").first())
        assertEquals(
            listOf("Video B", "Video A"),
            repository.observeImports().first().map { it.title }
        )
    }

    @Test
    fun usageTotalsSumKnownSuccessfulBatchesWithoutImportOrConfigContamination() = runBlocking {
        database.processingBatchDao().insertBatch(
            batch("jev-known", "import-a", 7, ProcessingStage.JEV_GATE, 100, 10, 0.01)
        )
        database.processingBatchDao().insertBatch(
            batch("glm-partial", "import-a", 7, ProcessingStage.GLM_ANALYSIS, null, 20, null)
        )
        database.processingBatchDao().insertBatch(
            batch("other-config", "import-a", 8, ProcessingStage.GLM_ANALYSIS, 900, 900, 9.0)
        )
        database.processingBatchDao().insertBatch(
            batch("other-import", "import-b", 7, ProcessingStage.GLM_ANALYSIS, 800, 800, 8.0)
        )
        database.processingBatchDao().insertBatch(
            batch("null-metrics", "import-a", 9, ProcessingStage.JEV_GATE, null, null, null)
        )

        val known = repository.observeSummary("import-a", 7).first().usage
        val unknown = repository.observeSummary("import-a", 9).first().usage

        assertEquals(100L, known.inputTokens)
        assertEquals(30L, known.outputTokens)
        assertEquals(0.01, known.costUsd!!, 0.000001)
        assertNull(unknown.inputTokens)
        assertNull(unknown.outputTokens)
        assertNull(unknown.costUsd)
    }

    @Test
    fun emptyScopeHasZeroCountsEmptyDistributionsAndUnknownUsage() = runBlocking {
        val summary = repository.observeSummary("missing-import", 1).first()

        assertEquals(0L, summary.processing.totalComments)
        assertEquals(0L, summary.processing.completed)
        assertEquals(0L, summary.processing.locallyRejected)
        assertEquals(0L, summary.processing.jevRejected)
        assertEquals(0L, summary.processing.retrying)
        assertEquals(0L, summary.processing.permanentlyFailed)
        assertEquals(emptyList<Pair<String, Long>>(), summary.categories.map { it.value to it.count })
        assertEquals(emptyList<Pair<String, Long>>(), summary.sentiments.map { it.value to it.count })
        assertNull(summary.usage.inputTokens)
        assertNull(summary.usage.outputTokens)
        assertNull(summary.usage.costUsd)
    }

    @Test
    fun persistedInProgressStatesAreObservableForBackgroundAnalysis() = runBlocking {
        database.commentDao().insertComments(
            listOf(
                comment("jev-running", "import-a", 7, ProcessingState.JEV_IN_PROGRESS)
                    .copy(currentStage = ProcessingStage.JEV_GATE),
                comment("glm-running", "import-a", 7, ProcessingState.GLM_IN_PROGRESS)
                    .copy(currentStage = ProcessingStage.GLM_ANALYSIS),
                comment("imported-eligible", "import-a", 7, ProcessingState.IMPORTED),
                comment("ready-jev", "import-a", 7, ProcessingState.READY_JEV),
                comment("ready-glm", "import-a", 7, ProcessingState.READY_GLM),
                comment("manual-review", "import-a", 7, ProcessingState.MANUAL_REVIEW),
                comment("retry-wait", "import-a", 7, ProcessingState.RETRY_WAIT),
                comment("local-rejected", "import-a", 7, ProcessingState.LOCAL_REJECTED),
                comment("jev-rejected", "import-a", 7, ProcessingState.JEV_REJECTED),
                comment("failed-perm", "import-a", 7, ProcessingState.FAILED_PERMANENT),
                comment("completed-valid", "import-a", 7, ProcessingState.COMPLETED, "feedback", "positive"),
                comment("cancelled", "import-a", 7, ProcessingState.CANCELLED)
            )
        )
        database.analysisResultDao().insertResult(
            result("glm-completed", "completed-valid", 7, ResultStage.GLM_ANALYSIS)
        )

        val summary = repository.observeSummary("import-a", 7).first()

        // 1 & 2: JEV_IN_PROGRESS (1) + GLM_IN_PROGRESS (1) = 2 inProgress
        // 3: READY_JEV does NOT count as inProgress
        // 4: READY_GLM does NOT count as inProgress
        // 5: IMPORTED does NOT count as inProgress
        // 6: MANUAL_REVIEW does NOT count as inProgress
        assertEquals(2L, summary.processing.inProgress)

        // 7: RETRY_WAIT increments retrying but NOT inProgress
        assertEquals(1L, summary.processing.retrying)

        // 8: existing completed/rejected/failed counts remain correct
        assertEquals(12L, summary.processing.totalComments)
        assertEquals(1L, summary.processing.completed)
        assertEquals(1L, summary.processing.locallyRejected)
        assertEquals(1L, summary.processing.jevRejected)
        assertEquals(1L, summary.processing.permanentlyFailed)
    }

    private fun comment(
        id: String,
        importId: String,
        configVersion: Long,
        state: ProcessingState,
        category: String? = null,
        sentiment: String? = null
    ) = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "$importId:scope",
        sourceCommentId = "source-$id",
        parentSourceCommentId = null,
        videoId = null,
        importId = importId,
        authorDisplayName = null,
        originalText = "comment",
        normalizedText = "comment",
        publishedAtEpochMillis = null,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fingerprint-$id",
        processingState = state,
        currentStage = if (state == ProcessingState.COMPLETED) ProcessingStage.DONE else ProcessingStage.INGESTION,
        category = category,
        sentiment = sentiment,
        relevanceScore = null,
        processingConfigVersion = configVersion,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        attemptCountLocal = 0,
        attemptCountJev = 0,
        attemptCountGlm = 0,
        lastErrorCode = null,
        lastErrorSummary = null,
        createdAt = 1,
        updatedAt = 1
    )

    private fun result(
        resultId: String,
        commentId: String,
        configVersion: Long,
        stage: ResultStage,
        category: String? = null
    ) = AnalysisResultEntity(
        resultId = resultId,
        commentId = commentId,
        stage = stage,
        processingConfigVersion = configVersion,
        modelRequested = "model",
        modelResolved = null,
        provider = null,
        inputTokens = null,
        outputTokens = null,
        costUsd = null,
        passed = null,
        probability = null,
        category = category,
        sentiment = null,
        relevanceScore = null,
        summary = null,
        rawPayloadJson = null,
        createdAt = 1
    )

    private fun batch(
        batchId: String,
        importId: String,
        configVersion: Long,
        stage: ProcessingStage,
        inputTokens: Long?,
        outputTokens: Long?,
        costUsd: Double?
    ) = ProcessingBatchEntity(
        batchId = batchId,
        stage = stage,
        status = BatchStatus.SUCCEEDED,
        parentBatchId = null,
        processingConfigVersion = configVersion,
        modelRequested = "model",
        modelResolved = "model",
        requestFingerprintSha256 = "fingerprint",
        requestItemCount = 1,
        requestUtf8Bytes = 10,
        estimatedInputTokens = 2,
        maxOutputTokens = 10,
        attemptCount = 1,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        remoteRequestId = null,
        provider = "openrouter",
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        costUsd = costUsd,
        createdAt = 1,
        updatedAt = 1,
        lastErrorCode = null,
        lastErrorSummary = null,
        importId = importId
    )

    private fun import(importId: String, title: String, startedAt: Long) = ImportEntity(
        importId = importId,
        sourceType = SourceType.YOUTUBE_API,
        sourceLocator = "https://youtube.com/watch?v=abcdefghijk",
        sourceScopeKey = "$importId:scope",
        status = ImportStatus.COMPLETED,
        includeReplies = true,
        nextThreadPageToken = null,
        activeParentCommentId = null,
        nextReplyPageToken = null,
        itemsSeen = 1,
        itemsInserted = 1,
        itemsDeduplicated = 0,
        itemsFailed = 0,
        startedAt = startedAt,
        completedAt = startedAt,
        lastErrorCode = null,
        videoId = "abcdefghijk",
        videoTitle = title
    )
}
