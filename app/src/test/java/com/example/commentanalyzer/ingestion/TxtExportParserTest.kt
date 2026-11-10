package com.example.commentanalyzer.ingestion

import com.example.commentanalyzer.ingestion.file.TxtExportParser
import org.junit.Assert.*
import org.junit.Test

class TxtExportParserTest {

    @Test
    fun testParseSampleExport() {
        val lines = listOf(
            "YouTube Comment Viewer",
            "VIDEO",
            "ENEM 2026: REDAÇÃO SOBRE O SUS",
            "O Antagonista",
            "6,750 views",
            "250",
            "Published: 11/9/2026, 12:59:35 PM",
            "41 comments",
            "▲ STATISTICS",
            "Top-level comments\t24",
            "Total likes\t58",
            "Average comments per day\t24.00",
            "Average likes per comment\t2.42",
            "@adilsonalves8028 | 11/9/2026, 1:57:32 PM",
            "#1",
            "Uma bancada de \"direita\" no congresso e no senado 😂",
            "11",
            "▼ Load 5 replies"
        )

        val result = TxtExportParser.parseLines(lines)
        assertEquals("ENEM 2026: REDAÇÃO SOBRE O SUS", result.videoDetails.title)
        assertEquals("O Antagonista", result.videoDetails.channel)
        assertEquals(6750L, result.videoDetails.views)
        assertEquals(250L, result.videoDetails.likes)
        assertEquals(24, result.statistics.topLevelComments)
        assertEquals(1, result.comments.size)

        val comment = result.comments[0]
        assertEquals("1", comment.id)
        assertEquals("@adilsonalves8028", comment.author)
        assertEquals("11/9/2026, 1:57:32 PM", comment.date)
        assertTrue(comment.text.contains("direita"))
        assertTrue(comment.text.contains("😂"))
        assertEquals(11, comment.likes)
        assertEquals("▼ Load 5 replies", comment.repliesHint)
    }
}
