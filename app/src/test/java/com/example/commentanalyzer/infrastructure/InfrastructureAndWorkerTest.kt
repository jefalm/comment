package com.example.commentanalyzer.infrastructure

import com.example.commentanalyzer.network.dto.youtube.CommentThreadItem
import com.example.commentanalyzer.network.dto.youtube.CommentThreadListResponse
import com.example.commentanalyzer.ingestion.youtube.YouTubeVideoIdParser
import com.example.commentanalyzer.worker.CommentAnalyzerWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InfrastructureAndWorkerTest {

    @Test
    fun parsesSupportedYouTubeVideoIdentifiersOnly() {
        assertEquals("abcdefghijk", YouTubeVideoIdParser.parse("abcdefghijk"))
        assertEquals("abcdefghijk", YouTubeVideoIdParser.parse("https://www.youtube.com/watch?v=abcdefghijk"))
        assertEquals("abcdefghijk", YouTubeVideoIdParser.parse("https://youtu.be/abcdefghijk"))
        assertEquals("abcdefghijk", YouTubeVideoIdParser.parse("https://www.youtube.com/shorts/abcdefghijk"))
        assertEquals(null, YouTubeVideoIdParser.parse("https://www.youtube.com/watch?v=abcdefghijk&v=lmnopqrstuv"))
        assertEquals(null, YouTubeVideoIdParser.parse("https://www.youtube.com/results?search_query=abcdefghijk"))
        assertEquals(null, YouTubeVideoIdParser.parse("http://youtu.be/abcdefghijk"))
        assertEquals(null, YouTubeVideoIdParser.parse("abcdefghij"))
    }

    @Test
    fun testYouTubeDtoParsingStructure() {
        val response = CommentThreadListResponse(nextPageToken = "token123", items = emptyList<CommentThreadItem>())
        assertEquals("token123", response.nextPageToken)
        assertNotNull(response.items)
    }

    @Test
    fun testWorkerConstants() {
        assertEquals("comment_analyzer_ingestion_work", CommentAnalyzerWorker.WORK_NAME_INGESTION)
        assertEquals("comment_analyzer_coordinator_work", CommentAnalyzerWorker.WORK_NAME_COORDINATOR)
    }
}
