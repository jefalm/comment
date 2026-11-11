package com.example.commentanalyzer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ResultStage
import com.example.commentanalyzer.domain.analysis.DeepAnalyzer
import com.example.commentanalyzer.domain.gate.SemanticGate
import com.example.commentanalyzer.domain.model.AnalysisBatchResult
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput
import com.example.commentanalyzer.domain.model.AnalysisItemResult
import com.example.commentanalyzer.domain.model.GateBatchResult
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import com.example.commentanalyzer.domain.model.GateItemResult
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.retry.RetryPolicy
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
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisOrchestratorTest {
    private lateinit var database: AppDatabase
    private lateinit var persistence: AnalysisPersistenceRepository
    private val events = mutableListOf<String>()
    private var now = 1_000_000L
    private var gateCalls = 0
    private var glmCalls = 0
    private var gateError: String? = null
    private var glmError: String? = null
    private var probability = 0.9
    private var partialGlmResponse = false

    private val gate = object : SemanticGate {
        override suspend fun screen(batch: List<GateInput>, config: GateConfig): GateBatchResult {
            events += "jev"
            gateCalls++
            return GateBatchResult(
                batchId = "fake-jev-request",
                modelResolved = config.model,
                provider = "openrouter",
                inputTokens = 10,
                outputTokens = 2,
                costUsd = 0.0,
                results = batch.map {
                    GateItemResult(it.commentId, config.questionContract.questionKey, probability, null)
                },
                error = gateError
            )
        }
    }

    private val analyzer = object : DeepAnalyzer {
        override suspend fun analyze(
            batch: List<AnalysisInput>,
            config: AnalysisConfig
        ): AnalysisBatchResult {
            events += "glm"
            glmCalls++
            val results = batch.map {
                AnalysisItemResult(it.commentId, "feedback", "positive", 0.9, "Helpful feedback")
            }.let { if (partialGlmResponse) it.take(1) else it }
            return AnalysisBatchResult(
                batchId = "fake-glm-batch",
                modelResolved = config.model,
                provider = "openrouter",
                inputTokens = 20,
                outputTokens = 5,
                costUsd = 0.0,
                results = results,
                error = glmError,
                modelRequested = config.model,
                generationId = "fake-generation"
            )
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        persistence = AnalysisPersistenceRepository(database) { now }
        resetFakes()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun localRejectionNeverReachesJev() = runBlocking {
        addComment(comment("short", "hi"))

        orchestrator().run(IMPORT_ID, config())

        assertEquals(0, gateCalls)
        assertEquals(0, glmCalls)
        assertEquals(ProcessingState.LOCAL_REJECTED, database.commentDao().getCommentById("short")?.processingState)
        assertEquals(
            listOf(ResultStage.LOCAL_FILTER),
            database.analysisResultDao().getResultsForComment("short", CONFIG_VERSION).map { it.stage }
        )
    }

    @Test
    fun jevRejectionNeverReachesGlm() = runBlocking {
        addComment(comment("rejected"))
        probability = 0.1

        orchestrator().run(IMPORT_ID, config())

        assertEquals(1, gateCalls)
        assertEquals(0, glmCalls)
        assertEquals(ProcessingState.JEV_REJECTED, database.commentDao().getCommentById("rejected")?.processingState)
        assertEquals(1, resultsFor("rejected", ResultStage.JEV_GATE).size)
    }

    @Test
    fun passingCommentFlowsThroughLocalFilterJevAndGlmToTerminalState() = runBlocking {
        addComment(comment("passed"))

        orchestrator().run(IMPORT_ID, config())

        assertEquals(listOf("jev", "glm"), events)
        val saved = requireNotNull(database.commentDao().getCommentById("passed"))
        assertEquals(ProcessingState.COMPLETED, saved.processingState)
        assertEquals(ProcessingStage.DONE, saved.currentStage)
        assertEquals("feedback", saved.category)
        assertEquals("positive", saved.sentiment)
        assertEquals(
            setOf(ResultStage.LOCAL_FILTER, ResultStage.JEV_GATE, ResultStage.GLM_ANALYSIS),
            database.analysisResultDao().getResultsForComment("passed", CONFIG_VERSION).map { it.stage }.toSet()
        )
        assertEquals(2, database.processingBatchDao().let { dao ->
            listOf(ProcessingStage.JEV_GATE, ProcessingStage.GLM_ANALYSIS).count { stage ->
                // Both successful stage transitions are persisted as successful batches.
                runBlocking {
                    dao.getRunnableBatches(IMPORT_ID, CONFIG_VERSION, stage, now, 10).isEmpty()
                }
            }
        })
    }

    @Test
    fun rerunDoesNotDuplicateSuccessfulRemoteResultsOrCalls() = runBlocking {
        addComment(comment("once"))
        val orchestrator = orchestrator()

        orchestrator.run(IMPORT_ID, config())
        orchestrator.run(IMPORT_ID, config())

        assertEquals(1, gateCalls)
        assertEquals(1, glmCalls)
        assertEquals(3, database.analysisResultDao().getResultsForComment("once", CONFIG_VERSION).size)
    }

    @Test
    fun failedValidatedGlmBatchDoesNotPersistPartialResults() = runBlocking {
        addComment(comment("partial"))
        glmError = "GLM_RESULT_ID_SET_MISMATCH"
        partialGlmResponse = true

        orchestrator().run(IMPORT_ID, config())

        val persisted = database.analysisResultDao().getResultsForComment("partial", CONFIG_VERSION)
        assertEquals(2, persisted.size)
        assertEquals(setOf(ResultStage.LOCAL_FILTER, ResultStage.JEV_GATE), persisted.map { it.stage }.toSet())
        assertTrue(resultsFor("partial", ResultStage.GLM_ANALYSIS).isEmpty())
        assertEquals(
            ProcessingState.FAILED_PERMANENT,
            database.commentDao().getCommentById("partial")?.processingState
        )
        assertEquals(1, glmCalls)
        assertTrue(events.contains("glm"))
    }

    @Test
    fun readinessFalsePreventsAllRemoteProcessing() = runBlocking {
        addComment(comment("not-ready"))

        orchestrator(readiness = { RemoteProcessingReadiness(true, false) })
            .run(IMPORT_ID, config())

        assertEquals(0, gateCalls)
        assertEquals(0, glmCalls)
        assertEquals(ProcessingState.READY_JEV, database.commentDao().getCommentById("not-ready")?.processingState)
    }

    @Test
    fun retryableFailureUsesBoundedRetryPolicyAndPersistsSchedule() = runBlocking {
        addComment(comment("retry"))
        gateError = "HTTP_429_RATE_LIMIT"
        val retryRandom = Random(23)

        orchestrator(random = retryRandom).run(IMPORT_ID, config())

        val expectedDelay = RetryPolicy.evaluate(429, 1, random = Random(23)).delayMillis
        val saved = requireNotNull(database.commentDao().getCommentById("retry"))
        assertEquals(ProcessingState.RETRY_WAIT, saved.processingState)
        assertEquals(now + expectedDelay, saved.nextAttemptAt)
        assertEquals("HTTP_429_RATE_LIMIT", saved.lastErrorCode)
        assertTrue(resultsFor("retry", ResultStage.JEV_GATE).isEmpty())
    }

    @Test
    fun deterministicValidationFailureIsNotRetried() = runBlocking {
        addComment(comment("invalid"))
        gateError = "JEV_VALIDATION_ERROR: invalid answer"
        val orchestrator = orchestrator()

        orchestrator.run(IMPORT_ID, config())
        now += 60_000L
        orchestrator.run(IMPORT_ID, config())

        assertEquals(1, gateCalls)
        assertEquals(ProcessingState.FAILED_PERMANENT, database.commentDao().getCommentById("invalid")?.processingState)
        assertTrue(resultsFor("invalid", ResultStage.JEV_GATE).isEmpty())
    }

    @Test
    fun adapterValidationCategoryIsPreservedInsteadOfNetworkError() = runBlocking {
        addComment(comment("category"))
        gateError = "JEV_VALIDATION_ERROR: missing answer"

        orchestrator().run(IMPORT_ID, config())

        val batch = database.processingBatchDao().getRunnableBatches(
            IMPORT_ID, CONFIG_VERSION, ProcessingStage.JEV_GATE, now, 1
        )
        assertTrue(batch.isEmpty())
        assertEquals(
            "JEV_VALIDATION_ERROR",
            database.commentDao().getCommentById("category")?.lastErrorCode
        )
        assertFalse(database.commentDao().getCommentById("category")?.lastErrorCode == "NETWORK_ERROR")
    }

    private fun orchestrator(
        readiness: suspend () -> RemoteProcessingReadiness = {
            RemoteProcessingReadiness(hasOpenRouterKey = true, remoteProcessingConsent = true)
        },
        random: Random = Random(23)
    ) = AnalysisOrchestrator(
        database = database,
        persistence = persistence,
        semanticGate = gate,
        deepAnalyzer = analyzer,
        readiness = readiness,
        clock = { now },
        random = random
    )

    private suspend fun addComment(value: CommentEntity) {
        database.commentDao().insertComment(value)
    }

    private suspend fun resultsFor(commentId: String, stage: ResultStage) =
        database.analysisResultDao().getResultsForComment(commentId, CONFIG_VERSION).filter { it.stage == stage }

    private fun resetFakes() {
        events.clear()
        gateCalls = 0
        glmCalls = 0
        gateError = null
        glmError = null
        probability = 0.9
        partialGlmResponse = false
    }

    private fun config() = ProcessingConfigEntity(
        configVersion = CONFIG_VERSION,
        jevModelRequested = "typesafe/jev-1.14",
        jevRejectThreshold = 0.3,
        jevPassThreshold = 0.7,
        glmModelRequested = "z-ai/glm-5.3-flash",
        youtubeKeyCiphertext = null,
        openrouterKeyCiphertext = null,
        openrouterReferer = null,
        createdAt = now,
        isActive = true
    )

    private fun comment(id: String, text: String = "Useful comment"): CommentEntity = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "$IMPORT_ID:scope",
        sourceCommentId = "source-$id",
        parentSourceCommentId = null,
        videoId = "abcdefghijk",
        importId = IMPORT_ID,
        authorDisplayName = "Author",
        originalText = text,
        normalizedText = text,
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
        createdAt = now,
        updatedAt = now
    )

    private companion object {
        const val IMPORT_ID = "import-orchestration"
        const val CONFIG_VERSION = 3L
    }
}
