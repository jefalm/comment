package com.example.commentanalyzer.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
    fun pagingIsScopedOrderedSearchableAndRelationshipAware() = runBlocking {
        dao.insertComments(
            listOf(
                comment("top-old", "import-a", "Needle top level", 10L),
                comment("reply-new", "import-a", "Needle reply", 30L, "source-top"),
                comment("other-import", "import-b", "Needle elsewhere", 40L),
                comment("top-new", "import-a", "Other top level", 20L)
            )
        )

        val all = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL, emptyList(), 0, emptyList(), 0, 0, 0, 0, 0))
        assertEquals(listOf("reply-new", "top-new", "top-old"), all.map(CommentEntity::localId))

        val search = load(dao.pagingComments("import-a", "nEeDlE", RELATIONSHIP_ALL, emptyList(), 0, emptyList(), 0, 0, 0, 0, 0))
        assertEquals(listOf("reply-new", "top-old"), search.map(CommentEntity::localId))

        val topLevel = load(dao.pagingComments("import-a", "", RELATIONSHIP_TOP_LEVEL, emptyList(), 0, emptyList(), 0, 0, 0, 0, 0))
        assertEquals(listOf("top-new", "top-old"), topLevel.map(CommentEntity::localId))

        val replies = load(dao.pagingComments("import-a", "", RELATIONSHIP_REPLIES, emptyList(), 0, emptyList(), 0, 0, 0, 0, 0))
        assertEquals(listOf("reply-new"), replies.map(CommentEntity::localId))
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

        val threads = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL, emptyList(), 0, emptyList(), 0, 0, 0, 1, 0))
        assertEquals(listOf("root-1", "reply-1-a", "reply-1-b", "orphan-reply"), threads.map(CommentEntity::localId))
    }

    @Test
    fun categoryGroupingAndGroupSizeOrdering() = runBlocking {
        val c1 = comment("c1", "import-a", "Cat 1 A", 100L).copy(category = "feedback")
        val c2 = comment("c2", "import-a", "Cat 1 B", 110L).copy(category = "feedback")
        val c3 = comment("c3", "import-a", "Cat 2 A", 120L).copy(category = "criticism")
        dao.insertComments(listOf(c1, c2, c3))

        val categories = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL, emptyList(), 0, emptyList(), 0, 0, 0, 2, 4))
        assertEquals(listOf("c2", "c1", "c3"), categories.map(CommentEntity::localId))
    }

    @Test
    fun multiSelectFiltersAndCrossDimensionAnd() = runBlocking {
        val c1 = comment("c1", "import-a", "Crit Neg", 100L).copy(category = "criticism", sentiment = "negative")
        val c2 = comment("c2", "import-a", "Q Neutral", 110L).copy(category = "question", sentiment = "neutral")
        val c3 = comment("c3", "import-a", "Crit Pos", 120L).copy(category = "criticism", sentiment = "positive")
        dao.insertComments(listOf(c1, c2, c3))

        val filtered = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL, listOf("criticism", "question"), 1, listOf("negative", "neutral"), 1, 0, 0, 0, 0))
        assertEquals(listOf("c2", "c1"), filtered.map(CommentEntity::localId))
    }

    @Test
    fun processingStateFilterAndHasReplies() = runBlocking {
        val c1 = comment("c1", "import-a", "Unclassified", 100L).copy(category = null, sentiment = null)
        val c2 = comment("c2", "import-a", "Retrying", 110L, parentId = null).copy(processingState = ProcessingState.RETRY_WAIT, declaredReplyCount = 2)
        dao.insertComments(listOf(c1, c2))

        val retrying = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL, emptyList(), 0, emptyList(), 0, 3, 0, 0, 0))
        assertEquals(listOf("c2"), retrying.map(CommentEntity::localId))

        val withReplies = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL, emptyList(), 0, emptyList(), 0, 0, 1, 0, 0))
        assertEquals(listOf("c2"), withReplies.map(CommentEntity::localId))
    }

    @Test
    fun needsAttentionDeterministicTuple() = runBlocking {
        val cCrit = comment("crit", "import-a", "Criticism", 100L).copy(category = "criticism")
        val cFeed = comment("feed", "import-a", "Feedback", 110L).copy(category = "feedback")
        val cQuest = comment("quest", "import-a", "Question", 120L).copy(category = "question")
        dao.insertComments(listOf(cCrit, cFeed, cQuest))

        val sorted = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL, emptyList(), 0, emptyList(), 0, 0, 0, 0, 3))
        assertEquals(listOf("crit", "feed", "quest"), sorted.map(CommentEntity::localId))
    }

    @Test
    fun recentImportOptionsOnlyIncludePersistedCommentScopes() = runBlocking {
        dao.insertComment(comment("saved-comment", "import-a", "Saved", 10L))
        database.importDao().insertImport(import("import-a", completedAt = 30L))
        database.importDao().insertImport(import("empty-import", completedAt = 40L))

        val imports = database.importDao().observeRecentImportsWithComments().first()

        assertEquals(listOf("import-a"), imports.map(ImportEntity::importId))
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

    private companion object {
        const val RELATIONSHIP_ALL = -1
        const val RELATIONSHIP_TOP_LEVEL = 0
        const val RELATIONSHIP_REPLIES = 1
    }
}
