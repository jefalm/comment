package com.example.commentanalyzer.ingestion

import com.example.commentanalyzer.ingestion.file.TxtExportParser
import com.example.commentanalyzer.ingestion.file.TxtParseException
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtExportParserTest {
    @Test
    fun parsesCanonicalExportAndReconcilesMetadata() {
        val data = TxtExportParser.parseLines(canonicalFixture())

        assertEquals(6_750L, data.videoDetails.views)
        assertEquals("250", data.videoDetails.unlabeledStatisticRaw)
        assertEquals(41, data.videoDetails.totalCommentsCount)
        assertEquals(24, data.statistics.topLevelComments)
        assertEquals(17, data.statistics.declaredReplyCount)
        assertEquals(58, data.statistics.totalLikes)
        assertEquals((1..24).map(Int::toString), data.comments.map { it.id })
        assertEquals(6, data.comments[2].text.lines().size)
        assertEquals(0, data.comments[10].likes)
        assertEquals("11/9/2026, 1:57:32 PM", data.comments.first().date)
        assertNull(data.comments.first().publishedEpochMillis)
        assertTrue(data.comments.first().text.contains("😂"))
        assertTrue(data.comments[13].text.contains("IDOTA"))
        assertEquals(7, data.comments.count { it.repliesHint != null })
    }

    @Test
    fun acceptsUtf8BomAndCrLf() {
        val text = canonicalFixture().joinToString("\r\n")
        val bytes = ("\uFEFF" + text).toByteArray(StandardCharsets.UTF_8)
        val result = TxtExportParser.parse(ByteArrayInputStream(bytes))
        assertEquals(24, result.comments.size)
    }

    @Test
    fun rejectsMismatchedDeclaredLikesWithTypedFailure() {
        val lines = canonicalFixture().map {
            if (it.startsWith("Total likes\t")) "Total likes\t59" else it
        }
        val failure = try {
            TxtExportParser.parseLines(lines)
            error("Expected parser validation failure")
        } catch (expected: TxtParseException) {
            expected
        }

        assertEquals("likes_count_mismatch", failure.reasonCode)
        assertEquals("STATISTICS", failure.section)
    }

    @Test
    fun rejectsMissingSectionsAndNoncontiguousOrdinals() {
        val source = canonicalFixture()
        assertFailure("missing_statistics_section") {
            TxtExportParser.parseLines(source.filterNot { it == "▲ STATISTICS" })
        }
        val changedOrdinal = source.map { if (it == "#2") "#3" else it }
        assertFailure("noncontiguous_comment_ordinal") { TxtExportParser.parseLines(changedOrdinal) }
    }

    @Test
    fun rejectsNumericOnlyCommentBodies() {
        val lines = canonicalFixture().mapIndexed { index, line ->
            if (line == "Uma bancada de \"direita\" no congresso e no senado e não tem uma alma viva capaz de fazer um projeto de lei que exija apenas conteúdo científico do MEC... Eita Brasil...") {
                "12345"
            } else {
                line
            }
        }
        assertFailure("ambiguous_numeric_only_body") { TxtExportParser.parseLines(lines) }
    }

    @Test
    fun rejectsMalformedUtf8AndOversizedInput() {
        assertFailure("invalid_utf8") {
            TxtExportParser.parse(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
        assertFailure("file_too_large") {
            TxtExportParser.parse(ByteArrayInputStream(ByteArray(TxtExportParser.MAX_FILE_SIZE_BYTES + 1)))
        }
    }

    private fun canonicalFixture(): List<String> {
        val candidates = listOf(
            File("src/main/assets/gemini_export.txt"),
            File("app/src/main/assets/gemini_export.txt")
        )
        val fixture = candidates.firstOrNull(File::isFile)
            ?: error("Canonical export fixture was not found")
        return fixture.readLines(Charsets.UTF_8)
    }

    private fun assertFailure(reason: String, action: () -> Unit) {
        val failure = try {
            action()
            error("Expected parser validation failure")
        } catch (expected: TxtParseException) {
            expected
        }
        assertEquals(reason, failure.reasonCode)
    }
}
