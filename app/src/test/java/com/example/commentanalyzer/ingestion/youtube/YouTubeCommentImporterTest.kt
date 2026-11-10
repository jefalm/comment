package com.example.commentanalyzer.ingestion.youtube

import com.example.commentanalyzer.domain.model.ImportRequest
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.network.youtube.YouTubeApiService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeCommentImporterTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requestsVideoThreadsAndPaginatedCompleteReplies() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"items":[{"id":"abcdefghijk","snippet":{"title":"Video","channelTitle":"Channel","publishedAt":"2026-01-02T03:04:05Z"},"statistics":{"viewCount":"0","commentCount":"3"}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""
            {"nextPageToken":"thread-next","items":[{"id":"thread-1","snippet":{"videoId":"abcdefghijk","topLevelComment":{"id":"top-1","snippet":{"videoId":"abcdefghijk","textDisplay":"Olá 😀","authorDisplayName":"Autor","publishedAt":"2026-01-02T03:04:05Z","likeCount":2}},"totalReplyCount":2},"replies":{"comments":[{"id":"reply-1","snippet":{"videoId":"abcdefghijk","textDisplay":"First","authorDisplayName":"A","parentId":"top-1"}}]}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""
            {"nextPageToken":"reply-next","items":[{"id":"reply-1","snippet":{"videoId":"abcdefghijk","textDisplay":"First","authorDisplayName":"A","parentId":"top-1"}},{"id":"reply-2","snippet":{"videoId":"abcdefghijk","textDisplay":"Second","authorDisplayName":"B","parentId":"top-1"}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""
            {"items":[{"id":"reply-2","snippet":{"videoId":"abcdefghijk","textDisplay":"Second","authorDisplayName":"B","parentId":"top-1"}}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        val sink = RecordingSink()
        val importer = YouTubeCommentImporter(
            YouTubeApiService.create(server.url("/").toString()),
            YouTubeApiKeyProvider { "test-key" },
            sink
        )
        val request = ImportRequest(
            importId = "import-1",
            sourceType = SourceType.YOUTUBE_API,
            sourceLocator = "https://youtu.be/abcdefghijk",
            sourceScopeKey = "abcdefghijk:import-1",
            includeReplies = true
        )

        val result = importer.import(request)

        assertTrue(result.completed)
        assertEquals(3L, result.itemsInserted)
        assertEquals(1, sink.threadPages.size)
        assertEquals("Olá 😀", sink.threadPages.single().first().text)
        assertEquals(listOf("reply-2", null), sink.replyPages.map { it.singleOrNull()?.sourceCommentId })
        assertEquals("youtube/v3/videos", server.takeRequest().path?.substringBefore('?')?.removePrefix("/"))
        val threadRequest = server.takeRequest()
        assertTrue(threadRequest.path.orEmpty().contains("maxResults=100"))
        assertTrue(threadRequest.path.orEmpty().contains("textFormat=plainText"))
        assertFalse(threadRequest.path.orEmpty().contains("search"))
        val firstReplyRequest = server.takeRequest()
        assertTrue(firstReplyRequest.path.orEmpty().contains("parentId=top-1"))
        assertFalse(firstReplyRequest.path.orEmpty().contains("pageToken"))
        assertTrue(server.takeRequest().path.orEmpty().contains("pageToken=reply-next"))
        assertTrue(server.takeRequest().path.orEmpty().contains("pageToken=thread-next"))
    }

    private class RecordingSink : YouTubeImportPageSink {
        val threadPages = mutableListOf<List<YouTubeImportedComment>>()
        val replyPages = mutableListOf<List<YouTubeImportedComment>>()

        override suspend fun prepareImport(request: ImportRequest, video: YouTubeVideoPreview) = Unit
        override suspend fun beginImport(request: ImportRequest, video: YouTubeVideoPreview) = Unit

        override suspend fun persistThreadPage(
            importId: String,
            video: YouTubeVideoPreview,
            consumedPageToken: String?,
            nextPageToken: String?,
            comments: List<YouTubeImportedComment>
        ): PersistedPageCounts {
            threadPages += comments
            return PersistedPageCounts(comments.size.toLong(), 0)
        }

        override suspend fun persistReplyPage(
            importId: String,
            parentCommentId: String,
            consumedPageToken: String?,
            nextPageToken: String?,
            replies: List<YouTubeImportedComment>,
            threadComplete: Boolean
        ): PersistedPageCounts {
            replyPages += replies
            return PersistedPageCounts(replies.size.toLong(), 0)
        }

        override suspend fun markImportCompleted(importId: String) = Unit
        override suspend fun markImportFailed(importId: String, code: String, retryable: Boolean) = Unit
        override suspend fun markImportCancelled(importId: String) = Unit
    }
}
