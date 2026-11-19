package com.example.commentanalyzer.data.local.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class CommentDaoPagingTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: CommentDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.commentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pagingIsScopedSearchableAndNewestSortedInFlatView() = runBlocking {
        dao.insertComments(
            listOf(
                comment("top-old", "import-a", "Needle top level", 10L),
                comment("reply-new", "import-a", "Needle reply", 30L, "source-top"),
                comment("other-import", "import-b", "Needle elsewhere", 40L),
                comment("top-new", "import-a", "Other top level", 20L)
            )
        )

        val all = load(dao.pagingComments(importId = "import-a", query = "", viewMode = 0, sort = 3))
        assertEquals(listOf("reply-new", "top-new", "top-old"), all.map(CommentEntity::localId))

        val search = load(dao.pagingComments(importId = "import-a", query = "nEeDlE", viewMode = 0, sort = 3))
        assertEquals(listOf("reply-new", "top-old"), search.map(CommentEntity::localId))
    }

    @Test
    fun threadsViewAdjacencyAndChronologicalRepliesAndOrphans() = runBlocking {
        dao.insertComments(
            listOf(
                comment("root-1", "import-a", "Root 1", 100L),
                comment("reply-1-b", "import-a", "Reply 1b", 120L, "source-root-1"),
                comment("reply-1-a", "import-a", "Reply 1a", 110L, "source-root-1"),
                comment("orphan-reply", "import-a", "Orphan", 90L, "source-missing-root")
            )
        )

        val threads = load(dao.pagingComments(importId = "import-a", viewMode = 1, sort = 3))
        assertEquals(listOf("root-1", "reply-1-a", "reply-1-b", "orphan-reply"), threads.map(CommentEntity::localId))
    }

    @Test
    fun sortModesMostRelevantMostEngagedMostDebated() = runBlocking {
        val c1 = comment("c1", "import-a", "First", 100L).copy(
            signalScore = 2.5,
            likeCount = 10,
            declaredReplyCount = 1
        )
        val c2 = comment("c2", "import-a", "Second", 110L).copy(
            signalScore = 5.0,
            likeCount = 2,
            declaredReplyCount = 5
        )
        val c3 = comment("c3", "import-a", "Third", 120L).copy(
            signalScore = 1.0,
            likeCount = 20,
            declaredReplyCount = 0
        )
        dao.insertComments(listOf(c1, c2, c3))

        val mostRelevant = load(dao.pagingComments(importId = "import-a", viewMode = 0, sort = 0))
        assertEquals(listOf("c2", "c1", "c3"), mostRelevant.map(CommentEntity::localId))

        val mostEngaged = load(dao.pagingComments(importId = "import-a", viewMode = 0, sort = 1))
        assertEquals(listOf("c3", "c1", "c2"), mostEngaged.map(CommentEntity::localId))

        val mostDebated = load(dao.pagingComments(importId = "import-a", viewMode = 0, sort = 2))
        assertEquals(listOf("c2", "c1", "c3"), mostDebated.map(CommentEntity::localId))
    }

    @Test
    fun quickFiltersReferencedInSynthesisHasRepliesQuestions() = runBlocking {
        val c1 = comment("c1", "import-a", "Normal comment", 100L).copy(signalTags = "debate,novel")
        val c2 = comment("c2", "import-a", "Question comment", 110L).copy(signalTags = "metrics,question")
        val c3 = comment("c3", "import-a", "Comment with replies", 120L).copy(declaredReplyCount = 3)
        dao.insertComments(listOf(c1, c2, c3))

        // Questions filter
        val questions = load(dao.pagingComments(importId = "import-a", questionsOnly = 1, viewMode = 0, sort = 3))
        assertEquals(listOf("c2"), questions.map(CommentEntity::localId))

        // Has replies filter
        val withReplies = load(dao.pagingComments(importId = "import-a", hasRepliesOnly = 1, viewMode = 0, sort = 3))
        assertEquals(listOf("c3"), withReplies.map(CommentEntity::localId))

        // Referenced in synthesis filter
        val referenced = load(
            dao.pagingComments(
                importId = "import-a",
                citedIds = listOf("c1", "c3"),
                hasCitedFilter = 1,
                viewMode = 0,
                sort = 3
            )
        )
        assertEquals(listOf("c3", "c1"), referenced.map(CommentEntity::localId))
    }

    @Test
    fun recentImportOptionsOnlyIncludePersistedCommentScopes() = runBlocking {
        dao.insertComment(comment("saved-comment", "import-a", "Saved", 10L))
        database.importDao().insertImport(import("import-a", completedAt = 30L))
        database.importDao().insertImport(import("empty-import", completedAt = 40L))

        val imports = database.importDao().observeRecentImportsWithComments().first()

        assertEquals(listOf("import-a"), imports.map(ImportEntity::importId))
    }

    @Test
    fun threadFocusFiltersToRootAndRepliesAndExcludesUnrelated() = runBlocking {
        dao.insertComments(
            listOf(
                comment("root-1", "import-1", "Root 1", 1000L, null),
                comment("reply-1", "import-1", "Reply 1.1", 1010L, "source-root-1"),
                comment("reply-2", "import-1", "Reply 1.2", 1020L, "source-root-1"),
                comment("root-2", "import-1", "Root 2", 2000L, null),
                comment("reply-2-1", "import-1", "Reply 2.1", 2010L, "source-root-2")
            )
        )

        // Root focus returns root and its replies in order, excludes unrelated
        val rootFocusResult = load(dao.pagingComments(importId = "import-1", viewMode = 1, focusedThreadSourceId = "source-root-1"))
        assertEquals(listOf("root-1", "reply-1", "reply-2"), rootFocusResult.map { it.localId })

        // Null focus restores normal results (both threads present)
        val normalResult = load(dao.pagingComments(importId = "import-1", viewMode = 1, focusedThreadSourceId = null))
        assertEquals(5, normalResult.size)
    }

    private suspend fun load(source: PagingSource<Int, CommentEntity>): List<CommentEntity> {
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false
            )
        )
        assertTrue("Expected a page, got $result", result is PagingSource.LoadResult.Page)
        return (result as PagingSource.LoadResult.Page).data
    }

    @Test
    fun hasImportedCommentsForAnalysisRespectsScopeStateAndStage() = runBlocking {
        val targetImport = "import-target"
        val otherImport = "import-other"
        val configVersion = 3L

        org.junit.Assert.assertFalse(dao.hasImportedCommentsForAnalysis(targetImport, configVersion))

        // 1. Matching row -> true
        val matching = comment("c-match", targetImport, "Text 1", 100L).copy(
            processingConfigVersion = configVersion,
            processingState = ProcessingState.IMPORTED,
            currentStage = ProcessingStage.INGESTION
        )
        dao.insertComment(matching)
        assertTrue(dao.hasImportedCommentsForAnalysis(targetImport, configVersion))

        // Remove the matching row so it no longer matches
        dao.insertComment(matching.copy(processingState = ProcessingState.COMPLETED))
        org.junit.Assert.assertFalse(dao.hasImportedCommentsForAnalysis(targetImport, configVersion))

        // Insert distinct non-matching rows
        val wrongImport = comment("c-wrong-import", otherImport, "Text", 101L).copy(
            processingConfigVersion = configVersion,
            processingState = ProcessingState.IMPORTED,
            currentStage = ProcessingStage.INGESTION
        )
        val wrongConfig = comment("c-wrong-config", targetImport, "Text", 102L).copy(
            processingConfigVersion = configVersion + 1,
            processingState = ProcessingState.IMPORTED,
            currentStage = ProcessingStage.INGESTION
        )
        val wrongState = comment("c-wrong-state", targetImport, "Text", 103L).copy(
            processingConfigVersion = configVersion,
            processingState = ProcessingState.READY_GLM,
            currentStage = ProcessingStage.INGESTION
        )
        val wrongStage = comment("c-wrong-stage", targetImport, "Text", 104L).copy(
            processingConfigVersion = configVersion,
            processingState = ProcessingState.IMPORTED,
            currentStage = ProcessingStage.DONE
        )
        dao.insertComments(listOf(wrongImport, wrongConfig, wrongState, wrongStage))

        // Original target scope must still be false despite other rows existing
        org.junit.Assert.assertFalse(dao.hasImportedCommentsForAnalysis(targetImport, configVersion))
    }

    private fun comment(
        id: String,
        importId: String,
        text: String,
        publishedAt: Long,
        parentId: String? = null
    ) = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "$importId:scope",
        sourceCommentId = "source-$id",
        parentSourceCommentId = parentId,
        videoId = "abcdefghijk",
        importId = importId,
        authorDisplayName = "Author",
        originalText = text,
        normalizedText = text.lowercase(),
        publishedAtEpochMillis = publishedAt,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fingerprint-$id",
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
        createdAt = publishedAt,
        updatedAt = publishedAt
    )

    private fun import(importId: String, completedAt: Long) = ImportEntity(
        importId = importId,
        sourceType = SourceType.YOUTUBE_API,
        sourceLocator = "abcdefghijk",
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
        startedAt = completedAt - 10,
        completedAt = completedAt,
        lastErrorCode = null,
        videoId = "abcdefghijk",
        videoTitle = importId
    )
}
