package com.example.commentanalyzer.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.ProductionAppContainer
import com.example.commentanalyzer.data.local.AnalysisOrchestrator
import com.example.commentanalyzer.data.local.AnalysisPersistenceRepository
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.InMemoryTelemetryRecorder
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.network.dto.glm.GlmResponse
import com.example.commentanalyzer.network.dto.glm.GlmUsage
import com.example.commentanalyzer.network.dto.jev.JevAnswerValue
import com.example.commentanalyzer.network.dto.jev.JevDecisionResponse
import com.example.commentanalyzer.network.dto.jev.JevUsage
import com.example.commentanalyzer.network.glm.GlmApiService
import com.example.commentanalyzer.network.jev.JevApiService
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisBaselineTest {
    private lateinit var server: MockWebServer
    private lateinit var database: AppDatabase
    private val gson = Gson()
    private lateinit var telemetry: InMemoryTelemetryRecorder

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        telemetry = InMemoryTelemetryRecorder()
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    private fun container(): ProductionAppContainer {
        val tokenProvider = { "sk-test-key" }
        val jevService = JevApiService.create(server.url("/").toString(), tokenProvider)
        val glmService = GlmApiService.create(server.url("/").toString(), tokenProvider)
        return ProductionAppContainer(
            database = database,
            openRouterKeyProvider = tokenProvider,
            remoteProcessingConsentProvider = { true },
            jevApiService = jevService,
            glmApiService = glmService
        )
    }

    private fun config() = ProcessingConfigEntity(
        configVersion = 1L,
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

    private suspend fun insertImport(importId: String, itemCount: Long) {
        database.importDao().insertImport(
            ImportEntity(
                importId = importId,
                sourceType = SourceType.YOUTUBE_API,
                sourceLocator = "https://youtube.com/watch?v=v1",
                sourceScopeKey = "$importId:scope",
                status = ImportStatus.COMPLETED,
                includeReplies = true,
                nextThreadPageToken = null,
                activeParentCommentId = null,
                nextReplyPageToken = null,
                itemsSeen = itemCount,
                itemsInserted = itemCount,
                itemsDeduplicated = 0,
                itemsFailed = 0,
                startedAt = 1_000L,
                completedAt = 1_000L,
                lastErrorCode = null,
                videoId = "v1"
            )
        )
    }

    private fun createComment(id: String, importId: String, text: String) = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "$importId:scope",
        sourceCommentId = "source-$id",
        parentSourceCommentId = null,
        videoId = "v1",
        importId = importId,
        authorDisplayName = "Author $id",
        originalText = text,
        normalizedText = text,
        publishedAtEpochMillis = null,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fp-$id",
        processingState = ProcessingState.IMPORTED,
        currentStage = ProcessingStage.INGESTION,
        category = null,
        sentiment = null,
        relevanceScore = null,
        processingConfigVersion = 1L,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        attemptCountLocal = 0,
        attemptCountJev = 0,
        attemptCountGlm = 0,
        lastErrorCode = null,
        lastErrorSummary = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        likeCount = 5
    )

    private fun createGlmChoice(commentIds: List<String>): com.example.commentanalyzer.network.dto.glm.GlmChoice {
        val resultsJson = gson.toJson(
            mapOf(
                "schemaVersion" to 1,
                "results" to commentIds.map { id ->
                    mapOf(
                        "commentId" to id,
                        "category" to "feedback",
                        "sentiment" to "positive",
                        "confidence" to 0.9,
                        "summary" to "Summary for $id"
                    )
                }
            )
        )
        return com.example.commentanalyzer.network.dto.glm.GlmChoice(
            message = com.example.commentanalyzer.network.dto.glm.GlmMessage("assistant", resultsJson),
            finishReason = "stop"
        )
    }

    @Test
    fun baselineScenario10Comments() = runBlocking {
        val importId = "import-10"
        insertImport(importId, 10L)
        for (i in 1..10) {
            database.commentDao().insertComment(createComment("c-$i", importId, "Comment text number $i"))
            val jevResp = JevDecisionResponse(
                id = "jev-gen-$i",
                model = "typesafe/jev-1.14",
                provider = "test-provider",
                usage = JevUsage(10L, 5L, 0.0001),
                answers = mapOf("comment_quality_gate" to JevAnswerValue("noul", noul = 0.8))
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(jevResp)))
        }

        val glmResp = GlmResponse(
            id = "glm-gen-1",
            model = "z-ai/glm-5.3-flash",
            provider = "test-provider",
            usage = GlmUsage(50L, 20L, 0.0005),
            choices = listOf(createGlmChoice((1..10).map { "c-$it" }))
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(glmResp)))

        val orchestrator = AnalysisOrchestrator(
            database = database,
            persistence = AnalysisPersistenceRepository(database),
            semanticGate = container().semanticGate,
            deepAnalyzer = container().deepAnalyzer,
            readiness = { com.example.commentanalyzer.data.local.RemoteProcessingReadiness(true, true) },
            telemetryRecorder = telemetry
        )

        val result = orchestrator.run(importId, config())

        assertEquals(10, result.locallyProcessedComments)
        val metrics = telemetry.getMetrics()
        assertEquals(10, metrics.discoveredCount)
        assertTrue(metrics.jevRequestCount > 0)
        assertTrue(metrics.glmRequestCount > 0)
        assertTrue(metrics.actualTokens > 0L)
        assertTrue(metrics.costUsd > 0.0)
    }

    @Test
    fun baselineScenario100Comments() = runBlocking {
        val importId = "import-100"
        insertImport(importId, 100L)
        for (i in 1..100) {
            database.commentDao().insertComment(createComment("c-$i", importId, "Comment body $i"))
        }
        for (i in 1..100) {
            val jevResp = JevDecisionResponse(
                id = "jev-gen-$i",
                model = "typesafe/jev-1.14",
                provider = "test-provider",
                usage = JevUsage(10L, 5L, 0.0001),
                answers = mapOf("comment_quality_gate" to JevAnswerValue("noul", noul = 0.8))
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(jevResp)))
        }
        for (i in 1..5) {
            val batchIds = ((i - 1) * 20 + 1..i * 20).map { "c-$it" }
            val glmResp = GlmResponse(
                id = "glm-gen-$i",
                model = "z-ai/glm-5.3-flash",
                provider = "test-provider",
                usage = GlmUsage(50L, 20L, 0.0005),
                choices = listOf(createGlmChoice(batchIds))
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(gson.toJson(glmResp)))
        }

        val orchestrator = AnalysisOrchestrator(
            database = database,
            persistence = AnalysisPersistenceRepository(database),
            semanticGate = container().semanticGate,
            deepAnalyzer = container().deepAnalyzer,
            readiness = { com.example.commentanalyzer.data.local.RemoteProcessingReadiness(true, true) },
            telemetryRecorder = telemetry
        )

        val result = orchestrator.run(importId, config())
        assertEquals(100, result.locallyProcessedComments)
        val metrics = telemetry.getMetrics()
        assertEquals(100, metrics.discoveredCount)
    }

    @Test
    fun errorScenario429RateLimit() = runBlocking {
        val importId = "import-429"
        insertImport(importId, 1L)
        database.commentDao().insertComment(createComment("c-1", importId, "Rate limited comment"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("Rate Limit Exceeded"))

        val orchestrator = AnalysisOrchestrator(
            database = database,
            persistence = AnalysisPersistenceRepository(database),
            semanticGate = container().semanticGate,
            deepAnalyzer = container().deepAnalyzer,
            readiness = { com.example.commentanalyzer.data.local.RemoteProcessingReadiness(true, true) },
            telemetryRecorder = telemetry
        )

        orchestrator.run(importId, config())
        val saved = database.commentDao().getCommentById("c-1")
        assertEquals(ProcessingState.RETRY_WAIT, saved?.processingState)
        assertEquals("HTTP_429_RATE_LIMIT", saved?.lastErrorCode)
        assertEquals(1, telemetry.getMetrics().retriesCount)
    }

    @Test
    fun errorScenario5xxServer() = runBlocking {
        val importId = "import-503"
        insertImport(importId, 1L)
        database.commentDao().insertComment(createComment("c-1", importId, "Server error comment"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))

        val orchestrator = AnalysisOrchestrator(
            database = database,
            persistence = AnalysisPersistenceRepository(database),
            semanticGate = container().semanticGate,
            deepAnalyzer = container().deepAnalyzer,
            readiness = { com.example.commentanalyzer.data.local.RemoteProcessingReadiness(true, true) },
            telemetryRecorder = telemetry
        )

        orchestrator.run(importId, config())
        val saved = database.commentDao().getCommentById("c-1")
        assertEquals(ProcessingState.RETRY_WAIT, saved?.processingState)
        assertEquals("HTTP_SERVER_ERROR_503", saved?.lastErrorCode)
        assertEquals(1, telemetry.getMetrics().retriesCount)
    }

    @Test
    fun errorScenarioMalformedJson() = runBlocking {
        val importId = "import-malformed"
        insertImport(importId, 1L)
        database.commentDao().insertComment(createComment("c-1", importId, "Malformed JSON test"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("NOT VALID JSON"))

        val orchestrator = AnalysisOrchestrator(
            database = database,
            persistence = AnalysisPersistenceRepository(database),
            semanticGate = container().semanticGate,
            deepAnalyzer = container().deepAnalyzer,
            readiness = { com.example.commentanalyzer.data.local.RemoteProcessingReadiness(true, true) },
            telemetryRecorder = telemetry
        )

        orchestrator.run(importId, config())
        val saved = database.commentDao().getCommentById("c-1")
        assertEquals(ProcessingState.RETRY_WAIT, saved?.processingState)
    }
}
