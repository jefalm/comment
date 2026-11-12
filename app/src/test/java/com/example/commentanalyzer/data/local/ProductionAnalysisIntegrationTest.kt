package com.example.commentanalyzer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.ProductionAppContainer
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ResultStage
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.network.dto.glm.GlmRequest
import com.example.commentanalyzer.network.dto.glm.GlmResponse
import com.example.commentanalyzer.network.dto.jev.JevDecisionResponse
import com.example.commentanalyzer.network.dto.jev.JevRequest
import com.example.commentanalyzer.network.dto.jev.JevUsage
import com.example.commentanalyzer.network.glm.GlmApiService
import com.example.commentanalyzer.network.jev.JevApiService
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductionAnalysisIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var jevService: FakeJevApiService
    private lateinit var glmService: FakeGlmApiService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        jevService = FakeJevApiService()
        glmService = FakeGlmApiService()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completedProductionGraphPersistsResultsAndExposesAggregateWithoutDuplicates() = runBlocking {
        addComment(comment("complete"))
        val container = container()

        container.analysisOrchestrator.run(IMPORT_ID, config())
        container.analysisOrchestrator.run(IMPORT_ID, config())

        assertEquals(1, jevService.calls)
        assertEquals(1, glmService.calls)
        assertEquals(
            setOf(ResultStage.LOCAL_FILTER, ResultStage.JEV_GATE, ResultStage.GLM_ANALYSIS),
            database.analysisResultDao().getResultsForComment("complete", CONFIG_VERSION)
                .map { it.stage }.toSet()
        )
        val summary = container.analysisReadRepository
            .observeSummary(IMPORT_ID, CONFIG_VERSION)
            .first()
        assertEquals(1L, summary.processing.completed)
        assertEquals(listOf("feedback" to 1L), summary.categories.map { it.value to it.count })
        assertEquals(listOf("positive" to 1L), summary.sentiments.map { it.value to it.count })
    }

    @Test
    fun eitherMissingKeyOrConsentPreventsRemoteCalls() = runBlocking {
        addComment(comment("no-key"))
        container(key = null, consent = true).analysisOrchestrator.run(IMPORT_ID, config())
        assertEquals(0, jevService.calls)
        assertEquals(0, glmService.calls)
        assertEquals(
            ProcessingState.READY_JEV,
            database.commentDao().getCommentById("no-key")?.processingState
        )

        addComment(comment("no-consent"))
        container(key = "test", consent = false).analysisOrchestrator.run(IMPORT_ID, config())
        assertEquals(0, jevService.calls)
        assertEquals(0, glmService.calls)
        assertEquals(
            ProcessingState.READY_JEV,
            database.commentDao().getCommentById("no-consent")?.processingState
        )
    }

    @Test
    fun jevRejectionDoesNotReachGlm() = runBlocking {
        addComment(comment("rejected"))
        jevService.probability = 0.1

        container().analysisOrchestrator.run(IMPORT_ID, config())

        assertEquals(1, jevService.calls)
        assertEquals(0, glmService.calls)
        assertEquals(
            ProcessingState.JEV_REJECTED,
            database.commentDao().getCommentById("rejected")?.processingState
        )
    }

    @Test
    fun invalidGlmBatchDoesNotPersistAnyGlmResults() = runBlocking {
        addComment(comment("batch-a"))
        addComment(comment("batch-b"))
        glmService.omitLastResult = true

        container().analysisOrchestrator.run(IMPORT_ID, config())

        assertEquals(1, glmService.calls)
        assertEquals(
            0,
            database.analysisResultDao().getResultsForComment("batch-a", CONFIG_VERSION)
                .count { it.stage == ResultStage.GLM_ANALYSIS } +
                database.analysisResultDao().getResultsForComment("batch-b", CONFIG_VERSION)
                    .count { it.stage == ResultStage.GLM_ANALYSIS }
        )
        assertEquals(
            ProcessingState.FAILED_PERMANENT,
            database.commentDao().getCommentById("batch-a")?.processingState
        )
        assertEquals(
            ProcessingState.FAILED_PERMANENT,
            database.commentDao().getCommentById("batch-b")?.processingState
        )
    }

    @Test
    fun retryableJevFailureIsPersistedAsRetryState() = runBlocking {
        addComment(comment("retry"))
        jevService.httpError = 429

        container().analysisOrchestrator.run(IMPORT_ID, config())

        val saved = requireNotNull(database.commentDao().getCommentById("retry"))
        assertEquals(ProcessingState.RETRY_WAIT, saved.processingState)
        assertNotNull(saved.nextAttemptAt)
        assertEquals("HTTP_429_RATE_LIMIT", saved.lastErrorCode)
    }

    @Test
    fun deterministicJevValidationFailureIsTerminal() = runBlocking {
        addComment(comment("invalid"))
        jevService.probability = 1.5

        container().analysisOrchestrator.run(IMPORT_ID, config())

        val saved = requireNotNull(database.commentDao().getCommentById("invalid"))
        assertEquals(ProcessingState.FAILED_PERMANENT, saved.processingState)
        assertEquals("JEV_VALIDATION_ERROR", saved.lastErrorCode)
        assertFalse(database.analysisResultDao().getResultsForComment("invalid", CONFIG_VERSION)
            .any { it.stage == ResultStage.JEV_GATE })
    }

    private fun container(
        key: String? = "test",
        consent: Boolean = true
    ) = ProductionAppContainer(
        database = database,
        openRouterKeyProvider = { key },
        remoteProcessingConsentProvider = { consent },
        jevApiService = jevService,
        glmApiService = glmService
    )

    private suspend fun addComment(value: CommentEntity) {
        database.commentDao().insertComment(value)
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
        createdAt = 1_000L,
        isActive = true
    )

    private fun comment(id: String) = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "$IMPORT_ID:scope",
        sourceCommentId = "source-$id",
        parentSourceCommentId = null,
        videoId = "abcdefghijk",
        importId = IMPORT_ID,
        authorDisplayName = "Author",
        originalText = "Useful comment",
        normalizedText = "Useful comment",
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
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private class FakeJevApiService : JevApiService {
        var calls = 0
        var probability = 0.9
        var httpError: Int? = null

        override suspend fun getDecisions(request: JevRequest): Response<JevDecisionResponse> {
            calls++
            httpError?.let { return Response.error(it, ResponseBody.create(null, "")) }
            val isV2 = request.questions.containsKey("comment_quality_gate")
            val answers = if (isV2) {
                mapOf(
                    "comment_quality_gate" to com.example.commentanalyzer.network.dto.jev.JevAnswerValue(
                        type = "noul",
                        noul = probability
                    ),
                    "comment_category" to com.example.commentanalyzer.network.dto.jev.JevAnswerValue(
                        type = "choice",
                        choice = "feedback",
                        confidence = 0.85
                    ),
                    "comment_sentiment" to com.example.commentanalyzer.network.dto.jev.JevAnswerValue(
                        type = "choice",
                        choice = "positive",
                        confidence = 0.92
                    )
                )
            } else {
                val questionKey = request.questions.keys.single()
                mapOf(
                    questionKey to com.example.commentanalyzer.network.dto.jev.JevAnswerValue(
                        type = "noul",
                        noul = probability
                    )
                )
            }
            return Response.success(
                JevDecisionResponse(
                    id = "test-request",
                    model = request.model,
                    provider = "test",
                    usage = JevUsage(inputTokens = 10, outputTokens = 2, costUsd = 0.0),
                    answers = answers
                )
            )
        }
    }

    private class FakeGlmApiService : GlmApiService {
        var calls = 0
        var omitLastResult = false

        override suspend fun createChatCompletion(request: GlmRequest): Response<ResponseBody> {
            calls++
            val requestedIds = JsonParser.parseString(request.messages.last().content)
                .asJsonObject.getAsJsonArray("comments")
                .map { it.asJsonObject.get("commentId").asString }
            val resultIds = if (omitLastResult) requestedIds.dropLast(1) else requestedIds
            val content = gson.toJson(
                mapOf(
                    "schemaVersion" to 1,
                    "results" to resultIds.map { id ->
                        mapOf(
                            "commentId" to id,
                            "category" to "feedback",
                            "sentiment" to "positive",
                            "confidence" to 0.9,
                            "summary" to "Helpful feedback"
                        )
                    }
                )
            )
            val envelope = gson.toJson(
                mapOf(
                    "id" to "test-generation",
                    "model" to request.model,
                    "provider" to "test",
                    "usage" to mapOf("input_tokens" to 20, "output_tokens" to 5, "cost" to 0.0),
                    "choices" to listOf(mapOf("message" to mapOf("content" to content)))
                )
            )
            return Response.success(ResponseBody.create(null, envelope))
        }

        private companion object {
            val gson = Gson()
        }
    }

    private companion object {
        const val IMPORT_ID = "production-integration"
        const val CONFIG_VERSION = 3L
        val gson = Gson()
    }
}
