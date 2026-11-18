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
    private var customSynthesisResult: SynthesisResult? = null
    private val fakeAnalyzer = object : SynthesisDeepAnalyzer {
        var callCount = 0
        var lastContext: SynthesisContext? = null
        override suspend fun synthesize(context: SynthesisContext): SynthesisBatchResult {
            callCount++
            lastContext = context
            return SynthesisBatchResult(
                synthesisId = "synthesis:${context.importId}:${context.configVersion}",
                importId = context.importId,
                configVersion = context.configVersion,
                modelResolved = "z-ai/glm-5.3-flash",
                provider = "test",
                inputTokens = 10,
                outputTokens = 10,
                costUsd = 0.001,
                synthesis = customSynthesisResult ?: SynthesisResult(
                    schemaVersion = 3,
                    executiveSummary = "Summary",
                    themes = emptyList(),
                    themesV3 = emptyList(),
                    agreements = emptyList(),
                    contrastingPerspectives = emptyList(),
                    notableInsights = emptyList()
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

    @Test
    fun orchestratorPassesDetectedPortugueseLanguageToAnalyzer() = runBlocking {
        database.commentDao().insertComments(
            listOf(
                CommentEntity(
                    localId = "c-pt-1", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                    parentSourceCommentId = null, videoId = "v1", importId = "import-pt", authorDisplayName = "U1",
                    originalText = "Muito bom este vídeo sobre inteligência artificial e economia!",
                    normalizedText = "muito bom este video", publishedAtEpochMillis = 100L,
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

        orchestrator.synthesizeImport("import-pt", 1L)
        assertEquals("pt-BR", fakeAnalyzer.lastContext?.targetLanguage)
    }

    @Test
    fun orchestratorPassesDetectedEnglishLanguageToAnalyzer() = runBlocking {
        database.commentDao().insertComments(
            listOf(
                CommentEntity(
                    localId = "c-en-1", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                    parentSourceCommentId = null, videoId = "v1", importId = "import-en", authorDisplayName = "U1",
                    originalText = "Great podcast episode breaking down the tech industry and economics.",
                    normalizedText = "great podcast episode", publishedAtEpochMillis = 100L,
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

        orchestrator.synthesizeImport("import-en", 1L)
        assertEquals("en", fakeAnalyzer.lastContext?.targetLanguage)
    }

    @Test
    fun synthesisReceivesOriginalTextAndJevFieldsWithoutGlmAnalysisResult() = runBlocking {
        database.commentDao().insertComments(
            listOf(
                CommentEntity(
                    localId = "c-jev-only", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
                    parentSourceCommentId = null, videoId = "v1", importId = "import-jev", authorDisplayName = "U1",
                    originalText = "This is the original comment text for synthesis", normalizedText = "this is the original comment text for synthesis", publishedAtEpochMillis = 100L,
                    publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "a",
                    processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED,
                    currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.DONE,
                    category = "praise", sentiment = "positive", relevanceScore = null,
                    categoryConfidence = 0.94, sentimentConfidence = 0.88,
                    processingConfigVersion = 1L,
                    leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
                    attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                    createdAt = 100L, updatedAt = 100L, likeCount = 42
                )
            )
        )

        orchestrator.synthesizeImport("import-jev", 1L)

        val context = requireNotNull(fakeAnalyzer.lastContext)
        assertEquals(1, context.evidenceItems.size)
        val evidence = context.evidenceItems.first()
        assertEquals("c-jev-only", evidence.commentId)
        assertEquals("This is the original comment text for synthesis", evidence.text)
        assertEquals("praise", evidence.category)
        assertEquals("positive", evidence.sentiment)
        assertEquals(0.94, evidence.categoryConfidence!!, 0.001)
        assertEquals(0.88, evidence.sentimentConfidence!!, 0.001)
        assertEquals(42, evidence.likeCount)
        assertTrue(database.analysisResultDao().getResultsForComment("c-jev-only", 1L).isEmpty())
    }

    @Test
    fun synthesisPersistsExposureTelemetryForExactRun() = runBlocking {
        val comments = (1..10).map { i ->
            CommentEntity(
                localId = "comm-$i", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "$i",
                parentSourceCommentId = null, videoId = "v1", importId = "import-telemetry", authorDisplayName = "User $i",
                originalText = "Useful comment $i for testing exposure telemetry persistence.",
                normalizedText = "useful comment $i for testing exposure telemetry persistence.",
                publishedAtEpochMillis = 100L, publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "fp-$i",
                processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED,
                currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.DONE,
                category = "feedback", sentiment = "positive", relevanceScore = 0.9,
                processingConfigVersion = 3L, leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null,
                attemptCountLocal = 0, attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
                createdAt = 100L, updatedAt = 100L, likeCount = i
            )
        }
        database.commentDao().insertComments(comments)

        val result = orchestrator.synthesizeImport("import-telemetry", 3L)
        assertNotNull(result.synthesis)

        val updatedComments = database.commentDao().getCommentsByIds(comments.map { it.localId })
        assertTrue(updatedComments.all { it.wasEvidenceCandidate })
        assertTrue(updatedComments.all { it.wasSelectedForEvidence })
        assertTrue(updatedComments.all { it.evidencePosition >= 0 })
    }

    @Test
    fun calibrationRunBypassesCachedSynthesisAndExecutes() = runBlocking {
        val comment = CommentEntity(
            localId = "c-calib-cache", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
            parentSourceCommentId = null, videoId = "v1", importId = "import-calib-cache", authorDisplayName = "User",
            originalText = "Comment to test calibration bypassing cache",
            normalizedText = "comment to test calibration bypassing cache",
            publishedAtEpochMillis = 100L, publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "fp-1",
            processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED,
            currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.DONE,
            category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 3L,
            leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
            attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
            createdAt = 100L, updatedAt = 100L, likeCount = 10
        )
        database.commentDao().insertComments(listOf(comment))

        // First run: normal (populates cache)
        val normalResult1 = orchestrator.synthesizeImport("import-calib-cache", 3L, isCalibrationRun = false)
        assertNotNull(normalResult1.synthesis)
        assertEquals(1, fakeAnalyzer.callCount)

        // Second run: normal (hits cache, fakeAnalyzer NOT called again)
        val normalResult2 = orchestrator.synthesizeImport("import-calib-cache", 3L, isCalibrationRun = false)
        assertNotNull(normalResult2.synthesis)
        assertEquals(1, fakeAnalyzer.callCount)

        // Third run: calibration run (must bypass cache, fakeAnalyzer called again)
        val calibResult = orchestrator.synthesizeImport("import-calib-cache", 3L, isCalibrationRun = true)
        assertNotNull(calibResult.synthesis)
        assertEquals(2, fakeAnalyzer.callCount)
    }

    @Test
    fun v3SynthesisHydratesRepresentativeQuoteAndAuthorLocallyFromRoom() = runBlocking {
        val comment = CommentEntity(
            localId = "c-quote", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
            parentSourceCommentId = null, videoId = "v1", importId = "import-quote", authorDisplayName = "Original Author",
            originalText = "This is the real comment text that must be hydrated locally.",
            normalizedText = "this is the real comment text",
            publishedAtEpochMillis = 100L, publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "fp-q",
            processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED,
            currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.DONE,
            category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 3L,
            leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
            attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
            createdAt = 100L, updatedAt = 100L, likeCount = 10
        )
        database.commentDao().insertComments(listOf(comment))

        customSynthesisResult = SynthesisResult(
            schemaVersion = 3,
            executiveSummary = "Summary",
            themesV3 = listOf(
                com.example.commentanalyzer.domain.model.SynthesisThemeV3(
                    title = "Key Feature",
                    takeaway = "Audience loves the new feature",
                    representativeCommentId = "c-quote",
                    citedCommentIds = listOf("c-quote")
                )
            )
        )

        val result = orchestrator.synthesizeImport("import-quote", 3L)
        assertNotNull(result.synthesis)
        val theme = result.synthesis!!.themesV3.single()
        assertEquals("This is the real comment text that must be hydrated locally.", theme.representativeQuote)
        assertEquals("Original Author", theme.representativeAuthor)
        assertEquals(1, theme.evidenceCount)

        // Verify persisted entity in database also contains hydrated fields
        val persisted = database.commentSynthesisDao().getSynthesis("import-quote", 3L)
        assertNotNull(persisted)
        val parsed = com.google.gson.Gson().fromJson(persisted!!.payloadJson, SynthesisResult::class.java)
        assertEquals("This is the real comment text that must be hydrated locally.", parsed.themesV3[0].representativeQuote)
        assertEquals("Original Author", parsed.themesV3[0].representativeAuthor)
    }

    @Test
    fun v3SynthesisFailsClosedWhenRepresentativeCommentNotFoundInRoom() = runBlocking {
        val comment = CommentEntity(
            localId = "c-exists", sourceType = SourceType.YOUTUBE, sourceScopeKey = "v1", sourceCommentId = "1",
            parentSourceCommentId = null, videoId = "v1", importId = "import-missing", authorDisplayName = "Author",
            originalText = "Existing text", normalizedText = "existing text",
            publishedAtEpochMillis = 100L, publishedAtPrecision = null, fingerprintVersion = 1, fingerprintSha256 = "fp-e",
            processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED,
            currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.DONE,
            category = "praise", sentiment = "positive", relevanceScore = 1.0, processingConfigVersion = 3L,
            leaseOwner = null, leaseExpiresAt = null, nextAttemptAt = null, attemptCountLocal = 0,
            attemptCountJev = 0, attemptCountGlm = 0, lastErrorCode = null, lastErrorSummary = null,
            createdAt = 100L, updatedAt = 100L, likeCount = 10
        )
        database.commentDao().insertComments(listOf(comment))

        customSynthesisResult = SynthesisResult(
            schemaVersion = 3,
            executiveSummary = "Summary",
            themesV3 = listOf(
                com.example.commentanalyzer.domain.model.SynthesisThemeV3(
                    title = "Key Feature",
                    takeaway = "Takeaway",
                    representativeCommentId = "c-missing-from-room",
                    citedCommentIds = listOf("c-exists")
                )
            )
        )

        val result = orchestrator.synthesizeImport("import-missing", 3L)
        assertNull(result.synthesis)
        assertEquals("SYNTHESIS_REPRESENTATIVE_COMMENT_NOT_FOUND", result.error)

        // Verify nothing was persisted
        val persisted = database.commentSynthesisDao().getSynthesis("import-missing", 3L)
        assertNull(persisted)
    }
}



