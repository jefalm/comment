package com.example.commentanalyzer.pipeline

import com.example.commentanalyzer.domain.batching.BatchLimits
import com.example.commentanalyzer.domain.batching.BatchPlanner
import com.example.commentanalyzer.domain.filter.FilterOutcome
import com.example.commentanalyzer.domain.filter.LocalFilter
import com.example.commentanalyzer.domain.retry.RetryDisposition
import com.example.commentanalyzer.domain.retry.RetryPolicy
import com.example.commentanalyzer.ingestion.file.FileParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PipelineLogicTest {

    @Test
    fun testLocalFilterOutcomes() {
        assertEquals(FilterOutcome.REJECT, LocalFilter.evaluate("").outcome)
        assertEquals(FilterOutcome.PASS, LocalFilter.evaluate("hi").outcome)
        assertEquals(FilterOutcome.PASS, LocalFilter.evaluate("ok").outcome)
        assertEquals(FilterOutcome.PASS, LocalFilter.evaluate("?").outcome)
        assertEquals(FilterOutcome.REJECT, LocalFilter.evaluate("aaaaaaaaaaaa").outcome)
        assertEquals(FilterOutcome.PASS, LocalFilter.evaluate("This is a great video!").outcome)
        assertEquals(FilterOutcome.REVIEW, LocalFilter.evaluate("Check out https://a.com https://b.com https://c.com https://d.com").outcome)
    }

    @Test
    fun testBatchPlannerSortingAndLimits() {
        val items = listOf(
            BatchPlanner.Item("c2", "second"),
            BatchPlanner.Item("c1", "first")
        )
        val batches = BatchPlanner.planBatches(items, BatchLimits(maxCount = 1, maxBytes = 1000L, maxTokens = 100L))
        assertEquals(2, batches.size)
        assertEquals("c1", batches[0][0].id)
        assertEquals("c2", batches[1][0].id)
    }

    @Test
    fun testRetryPolicyClassification() {
        val decision401 = RetryPolicy.evaluate(401, 1)
        assertEquals(RetryDisposition.PERMANENT, decision401.disposition)

        val decision503 = RetryPolicy.evaluate(503, 1, random = Random(42))
        assertEquals(RetryDisposition.RETRYABLE, decision503.disposition)

        val decisionMax = RetryPolicy.evaluate(503, 6)
        assertEquals(RetryDisposition.PERMANENT, decisionMax.disposition)
    }

    @Test
    fun testFileParserCsv() {
        val parsed = FileParser.parseCsvLine(1, "Great video,comment-1,Alice,2023-01-01T00:00:00Z,video-1")
        assertEquals("Great video", parsed.text)
        assertEquals("comment-1", parsed.sourceCommentId)
        assertEquals("Alice", parsed.author)
        assertNull(parsed.error)
    }
}
