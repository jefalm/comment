package com.example.commentanalyzer.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.CommentSynthesisEntity
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.model.SynthesisAgreement
import com.example.commentanalyzer.domain.model.SynthesisContrastingPerspective
import com.example.commentanalyzer.domain.model.SynthesisNotableInsight
import com.example.commentanalyzer.domain.model.SynthesisResult
import com.example.commentanalyzer.domain.model.SynthesisTheme
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetryExporterTest {
    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private val gson = Gson()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun telemetryExporterDerivesRealCitationsAndSectionTypes() = runBlocking {
        val importId = "import-telemetry-1"
        val configVersion = 3L

        val comment1 = createCompletedComment("c1", importId, configVersion)
        val comment2 = createCompletedComment("c2", importId, configVersion)
        val comment3 = createCompletedComment("c3", importId, configVersion)
        database.commentDao().insertComments(listOf(comment1, comment2, comment3))

        val synthesisResult = SynthesisResult(
            schemaVersion = 1,
            executiveSummary = "Executive summary",
            themes = listOf(
                SynthesisTheme("Theme 1", "Desc 1", listOf("c1")),
                SynthesisTheme("Theme 2", "Desc 2", listOf("c1"))
            ),
            agreements = listOf(
                SynthesisAgreement("Agreement 1", "Desc", listOf("c1", "c2"))
            ),
            contrastingPerspectives = listOf(
                SynthesisContrastingPerspective("A", "B", "Desc", listOf("c2"))
            ),
            notableInsights = listOf(
                SynthesisNotableInsight("Insight 1", listOf("c1"))
            )
        )

        database.commentSynthesisDao().insertSynthesis(
            CommentSynthesisEntity(
                synthesisId = "synth:$importId:$configVersion",
                importId = importId,
                configVersion = configVersion,
                modelRequested = "z-ai/glm-5.3-flash",
                modelResolved = "z-ai/glm-5.3-flash",
                provider = "openrouter",
                inputTokens = 100L,
                outputTokens = 50L,
                costUsd = 0.001,
                payloadJson = gson.toJson(synthesisResult),
                createdAt = 1000L
            )
        )

        val files = TelemetryExporter.exportTelemetry(context, database)
        assertNotNull(files)
        val (csvFile, _) = files!!

        val lines = csvFile.readLines()
        val header = lines.first().split(",")
        val wasCitedAnyIdx = header.indexOf("wasCitedAny")
        val citationCountIdx = header.indexOf("citationCount")
        val sectionTypesIdx = header.indexOf("citationSectionTypes")

        assertTrue(wasCitedAnyIdx >= 0)
        assertTrue(citationCountIdx >= 0)
        assertTrue(sectionTypesIdx >= 0)

        // c1: cited in themes (twice), agreements (once), notableInsights (once) -> count 4, sectionTypes "agreements;notableInsights;themes"
        // c2: cited in agreements (once), contrastingPerspectives (once) -> count 2, sectionTypes "agreements;contrastingPerspectives"
        // c3: not cited -> count 0, sectionTypes "", wasCitedAny false
        val dataRows = lines.drop(1).map { it.split(",") }
        assertEquals(3, dataRows.size)

        val rowC1 = dataRows.first { it[wasCitedAnyIdx] == "true" && it[citationCountIdx] == "4" }
        assertEquals("agreements;notableInsights;themes", rowC1[sectionTypesIdx])

        val rowC2 = dataRows.first { it[wasCitedAnyIdx] == "true" && it[citationCountIdx] == "2" }
        assertEquals("agreements;contrastingPerspectives", rowC2[sectionTypesIdx])

        val rowC3 = dataRows.first { it[wasCitedAnyIdx] == "false" && it[citationCountIdx] == "0" }
        assertEquals("", rowC3[sectionTypesIdx])
    }

    private fun createCompletedComment(
        id: String,
        importId: String,
        configVersion: Long
    ): CommentEntity = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "$importId:scope",
        sourceCommentId = "source-$id",
        parentSourceCommentId = null,
        videoId = "vid-1",
        importId = importId,
        authorDisplayName = "Author $id",
        originalText = "Comment text for $id",
        normalizedText = "comment text for $id",
        publishedAtEpochMillis = 1000L,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fp-$id",
        processingState = ProcessingState.COMPLETED,
        currentStage = ProcessingStage.DONE,
        category = "praise",
        sentiment = "positive",
        relevanceScore = 0.95,
        processingConfigVersion = configVersion,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        attemptCountLocal = 0,
        attemptCountJev = 0,
        attemptCountGlm = 0,
        lastErrorCode = null,
        lastErrorSummary = null,
        createdAt = 1000L,
        updatedAt = 1000L,
        wasEvidenceCandidate = true,
        wasSelectedForEvidence = true,
        evidencePosition = 0
    )
}
