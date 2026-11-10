package com.example.commentanalyzer.infrastructure

import com.example.commentanalyzer.network.dto.youtube.CommentThreadItem
import com.example.commentanalyzer.network.dto.youtube.CommentThreadListResponse
import com.example.commentanalyzer.worker.CommentAnalyzerWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InfrastructureAndWorkerTest {

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
