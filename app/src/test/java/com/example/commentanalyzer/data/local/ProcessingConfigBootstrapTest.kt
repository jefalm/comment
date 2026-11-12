package com.example.commentanalyzer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.TimestampPrecision
import com.example.commentanalyzer.domain.model.ImportRequest
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.ingestion.file.TxtExportImporter
import com.example.commentanalyzer.ingestion.youtube.YouTubeImportedComment
import com.example.commentanalyzer.ingestion.youtube.YouTubeVideoPreview
import java.io.File
import kotlinx.coroutines.flow.first
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
class ProcessingConfigBootstrapTest {
    private lateinit var database: AppDatabase
    private lateinit var context: Context

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
    fun bootstrapCreatesActiveConfigV2AndIsIdempotent() = runBlocking {
        // 1. Fresh database bootstrap
        val first = ProcessingConfigBootstrap.bootstrapActiveConfig(database)
        assertEquals(2L, first.configVersion)
        assertTrue(first.isActive)
        assertEquals("typesafe/jev-1.14", first.jevModelRequested)
        assertEquals("z-ai/glm-5.3-flash", first.glmModelRequested)

        val active = database.processingConfigDao().getActiveConfig()
        assertNotNull(active)
        assertEquals(2L, active?.configVersion)

        // 2. Idempotent bootstrap call
        val second = ProcessingConfigBootstrap.bootstrapActiveConfig(database)
        assertEquals(2L, second.configVersion)
        assertTrue(second.isActive)
    }

    @Test
    fun bootstrapPreservesExistingHistoricalV1Rows() = runBlocking {
        // Insert historical v1 import and comment
        val v1Import = ImportEntity(
            importId = "v1-import",
            sourceType = SourceType.YOUTUBE_API,
            sourceLocator = "video-1",
            sourceScopeKey = "scope-1",
            status = ImportStatus.COMPLETED,
            includeReplies = false,
            nextThreadPageToken = null,
            activeParentCommentId = null,
            nextReplyPageToken = null,
            itemsSeen = 1,
            itemsInserted = 1,
            itemsDeduplicated = 0,
            itemsFailed = 0,
            startedAt = 100L,
            completedAt = 200L,
            lastErrorCode = null,
            sourceConfigVersion = 1
        )
        database.importDao().insertImport(v1Import)

        val v1Comment = CommentEntity(
            localId = "c-v1-1",
            sourceType = SourceType.YOUTUBE_API,
            sourceScopeKey = "scope-1",
            sourceCommentId = "sc-1",
            parentSourceCommentId = null,
            videoId = "video-1",
            importId = "v1-import",
            authorDisplayName = "Author",
            originalText = "Great video",
            normalizedText = "great video",
            publishedAtEpochMillis = 1000L,
            publishedAtPrecision = TimestampPrecision.SECOND,
            fingerprintVersion = 1,
            fingerprintSha256 = "fp-1",
            processingState = ProcessingState.COMPLETED,
            currentStage = ProcessingStage.GLM_ANALYSIS,
            category = "praise",
            sentiment = "positive",
            relevanceScore = null,
            processingConfigVersion = 1,
            leaseOwner = null,
            leaseExpiresAt = null,
            nextAttemptAt = null,
            attemptCountLocal = 1,
            attemptCountJev = 1,
            attemptCountGlm = 1,
            lastErrorCode = null,
            lastErrorSummary = null,
            createdAt = 100L,
            updatedAt = 200L
        )
        database.commentDao().insertComments(listOf(v1Comment))

        // Run bootstrap
        ProcessingConfigBootstrap.bootstrapActiveConfig(database)

        // Verify historical rows remain intact
        val fetchedImport = database.importDao().getImportById("v1-import")
        assertNotNull(fetchedImport)
        assertEquals(1, fetchedImport?.sourceConfigVersion)

        val fetchedComment = database.commentDao().getCommentById("c-v1-1")
        assertNotNull(fetchedComment)
        assertEquals(1L, fetchedComment?.processingConfigVersion)
    }

    @Test
    fun newYouTubeImportUsesActiveConfigV2() = runBlocking {
        ProcessingConfigBootstrap.bootstrapActiveConfig(database)

        val sink = RoomYouTubeImportPageSink(database)
        val request = ImportRequest(
            importId = "new-yt-import",
            sourceType = SourceType.YOUTUBE_API,
            sourceLocator = "yt-video-1",
            sourceScopeKey = "scope-yt",
            includeReplies = false
        )
        val preview = YouTubeVideoPreview(
            videoId = "yt-video-1",
            title = "Title",
            channelTitle = "Channel",
            publishedAt = "2026-01-01T00:00:00Z",
            commentCount = 10L,
            viewCount = 1000L,
            likeCount = 50L
        )
        sink.prepareImport(request, preview)

        val imported = database.importDao().getImportById("new-yt-import")
        assertNotNull(imported)
        assertEquals(2, imported?.sourceConfigVersion)

        sink.persistThreadPage(
            importId = "new-yt-import",
            video = preview,
            consumedPageToken = null,
            nextPageToken = null,
            comments = listOf(
                YouTubeImportedComment(
                    sourceCommentId = "yt-c-1",
                    parentSourceCommentId = null,
                    videoId = "yt-video-1",
                    isTopLevel = true,
                    authorDisplayName = "Author",
                    text = "New comment",
                    likeCount = 5,
                    publishedAt = "2026-01-01T00:00:00Z",
                    updatedAt = null,
                    declaredReplyCount = 0
                )
            )
        )

        val comments = database.commentDao().getCommentsForImport("new-yt-import")
        assertEquals(1, comments.size)
        assertEquals(2L, comments.first().processingConfigVersion)
    }

    @Test
    fun newTxtImportUsesActiveConfigV2AndAnalysisObservesConfigV2() = runBlocking {
        ProcessingConfigBootstrap.bootstrapActiveConfig(database)

        val fixture = listOf(
            File("src/main/assets/gemini_export.txt"),
            File("app/src/main/assets/gemini_export.txt")
        ).firstOrNull(File::isFile) ?: error("Fixture gemini_export.txt not found")

        val importer = TxtExportImporter(database)
        val request = ImportRequest(
            importId = "new-txt-import",
            sourceType = SourceType.TXT_EXPORT,
            sourceLocator = fixture.absolutePath,
            sourceScopeKey = "scope-txt",
            includeReplies = false
        )
        val progress = importer.import(request)
        assertEquals(null, progress.lastErrorCode)
        assertTrue(progress.itemsInserted > 0)

        val imported = database.importDao().getImportById("new-txt-import")
        assertNotNull(imported)
        assertEquals(2, imported?.sourceConfigVersion)

        val comments = database.commentDao().getCommentsForImport("new-txt-import")
        assertTrue(comments.isNotEmpty())
        assertTrue(comments.all { it.processingConfigVersion == 2L })

        // Verify AnalysisReadRepository observes config 2
        val readRepo = AnalysisReadRepository(
            commentDao = database.commentDao(),
            importDao = database.importDao(),
            processingBatchDao = database.processingBatchDao(),
            commentSynthesisDao = database.commentSynthesisDao()
        )
        val versions = readRepo.observeConfigVersions("new-txt-import").first()
        assertEquals(listOf(2L), versions)
    }
}
