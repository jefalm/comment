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

@RunWith(RobolectricTestRunner::class)
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

        val all = load(dao.pagingComments("import-a", "", RELATIONSHIP_ALL))
        assertEquals(listOf("reply-new", "top-new", "top-old"), all.map(CommentEntity::localId))

        val search = load(dao.pagingComments("import-a", "nEeDlE", RELATIONSHIP_ALL))
        assertEquals(listOf("reply-new", "top-old"), search.map(CommentEntity::localId))

        val topLevel = load(dao.pagingComments("import-a", "", RELATIONSHIP_TOP_LEVEL))
        assertEquals(listOf("top-new", "top-old"), topLevel.map(CommentEntity::localId))

        val replies = load(dao.pagingComments("import-a", "", RELATIONSHIP_REPLIES))
        assertEquals(listOf("reply-new"), replies.map(CommentEntity::localId))
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