package com.example.commentanalyzer.domain.synthesis

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.domain.analysis.SynthesisContext
import com.example.commentanalyzer.domain.analysis.SynthesisDeepAnalyzer
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.model.SynthesisBatchResult
import com.example.commentanalyzer.domain.model.SynthesisResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SynthesisOrchestratorTest {
    private lateinit var database: AppDatabase
    private lateinit var orchestrator: SynthesisOrchestrator
    private val fakeAnalyzer = object : SynthesisDeepAnalyzer {
        var callCount = 0
        override suspend fun synthesize(context: SynthesisContext): SynthesisBatchResult {
            callCount++
            return SynthesisBatchResult(
                synthesisId = "synthesis:${context.importId}:${context.configVersion}",
                importId = context.importId,
                configVersion = context.configVersion,
                modelResolved = "z-ai/glm-5.3-flash",
                provider = "test",
                inputTokens = 10,
                outputTokens = 10,
                costUsd = 0.001,
                synthesis = SynthesisResult(
                    schemaVersion = 2,
                    executiveSummary = "Summary",
                    themes = emptyList(),
                    agreements = emptyList(),
                    contrastingPerspectives = emptyList(),
                    notableInsights = emptyList(),
                    actionableFollowUps = emptyList()
                ),
                error = null,
                modelRequested = "z-ai/glm-5.3-flash",
                generationId = "gen-1"
            )
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        orchestrator = SynthesisOrchestrator(database, fakeAnalyzer)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameImportConfigMaintainsIdempotentSynthesisOwnershipWithoutRerun() = runBlocking {
        database.commentDao().insertComments(
            listOf(
                CommentEntity(
                    localId = "c1", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                    parentSourceCommentId = null, videoId = "v1", importId = "import-1", authorDisplayName = "U1",
                    originalText = "Comment one", normalizedText = "comment one", publishedAtEpochMillis = 100L,
                    publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "a",
                    processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                    currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                    category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 1L,
                    leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                    attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                    createdAt = 100L, updatedAt = 100L, likeCount = 10
                )
            )
        )

        val firstResult = orchestrator.synthesizeImport("import-1", 1L)
        val secondResult = orchestrator.synthesizeImport("import-1", 1L)

        assertNotNull(firstResult.synthesis)
        assertNotNull(secondResult.synthesis)
        assertEquals(1, fakeAnalyzer.callCount)
        assertEquals("synthesis:import-1:1", firstResult.synthesisId)
        assertEquals(firstResult.synthesisId, secondResult.synthesisId)

        val forcedResult = orchestrator.synthesizeImport("import-1", 1L, forceRerun = true)
        assertNotNull(forcedResult.synthesis)
        assertEquals(2, fakeAnalyzer.callCount)
    }

    @Test
    fun configVersionIsolationAndAuthoritativePopulations() = runBlocking {
        database.commentDao().insertComments(
            listOf(
                CommentEntity(
                    localId = "c1", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                    parentSourceCommentId = null, videoId = "v1", importId = "import-1", authorDisplayName = "U1",
                    originalText = "Config 1 comment", normalizedText = "config 1", publishedAtEpochMillis = 100L,
                    publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "a",
                    processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                    currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                    category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 1L,
                    leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                    attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                    createdAt = 100L, updatedAt = 100L, likeCount = 10
                ),
                CommentEntity(
                    localId = "c2", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "2",
                    parentSourceCommentId = null, videoId = "v1", importId = "import-1", authorDisplayName = "U2",
                    originalText = "Config 2 comment", normalizedText = "config 2", publishedAtEpochMillis = 200L,
                    publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "b",
                    processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                    currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                    category = "criticism", sentiment = "negative", relevanceScore = 1.0, processingConfigVersion = 2L,
                    leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                    attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                    createdAt = 200L, updatedAt = 200L, likeCount = 5
                )
            )
        )

        val result1 = orchestrator.synthesizeImport("import-1", 1L)
        val result2 = orchestrator.synthesizeImport("import-1", 2L)

        assertNotNull(result1.synthesis)
        assertNotNull(result2.synthesis)
        assertEquals("synthesis:import-1:1", result1.synthesisId)
        assertEquals("synthesis:import-1:2", result2.synthesisId)
    }

    @Test
    fun oversizedFirstCandidateIsExcludedAndHardLimitsMaintained() {
        val hugeText = "X".repeat(200 * 1024)
        val comments = listOf(
            CommentEntity(
                localId = "c-huge", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                parentSourceCommentId = null, videoId = "v1", importId = "import-1", authorDisplayName = "U1",
                originalText = hugeText, normalizedText = "huge", publishedAtEpochMillis = 100L,
                publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "a",
                processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 1L,
                leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                createdAt = 100L, updatedAt = 100L, likeCount = 10
            ),
            CommentEntity(
                localId = "c-normal", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "2",
                parentSourceCommentId = null, videoId = "v1", importId = "import-1", authorDisplayName = "U2",
                originalText = "Normal comment text.", normalizedText = "normal", publishedAtEpochMillis = 200L,
                publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "b",
                processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                category = "criticism", sentiment = "negative", relevanceScore = 1.0, processingConfigVersion = 1L,
                leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                createdAt = 200L, updatedAt = 200L, likeCount = 5
            )
        )

        val planned = EvidencePlanner.planEvidence(comments)
        assertTrue(planned.none { it.commentId == "c-huge" })
        assertTrue(planned.any { it.commentId == "c-normal" })
        assertTrue(planned.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() } <= 128 * 1024L)
    }

    @Test
    fun failedSynthesisDoesNotPartiallyPersist() = runBlocking {
        database.commentDao().insertComments(
            listOf(
                CommentEntity(
                    localId = "c-fail-1", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                    parentSourceCommentId = null, videoId = "v1", importId = "import-1", authorDisplayName = "U1",
                    originalText = "Comment to synthesize", normalizedText = "comment to synthesize", publishedAtEpochMillis = 100L,
                    publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "a",
                    processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM,
                    currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.GLM_ANALYSIS,
                    category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 1L,
                    leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                    attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                    createdAt = 100L, updatedAt = 100L, likeCount = 10
                )
            )
        )

        val failingAnalyzer = object : SynthesisDeepAnalyzer {
            override suspend fun synthesize(context: SynthesisContext): SynthesisBatchResult {
                return SynthesisBatchResult(
                    synthesisId = "synth-fail",
                    importId = context.importId,
                    configVersion = context.configVersion,
                    modelResolved = "z-ai/glm-5.3-flash",
                    provider = null,
                    inputTokens = null,
                    outputTokens = null,
                    costUsd = null,
                    synthesis = null,
                    error = "GLM_MALFORMED_JSON",
                    modelRequested = "z-ai/glm-5.3-flash",
                    generationId = null
                )
            }
        }
        val failingOrchestrator = SynthesisOrchestrator(database, failingAnalyzer)

        val result = failingOrchestrator.synthesizeImport("import-1", 1L)
        assertEquals("GLM_MALFORMED_JSON", result.error)
        assertNull(result.synthesis)
        assertNull(database.commentSynthesisDao().getSynthesis("import-1", 1L))
    }
}
