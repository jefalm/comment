package com.example.commentanalyzer.ingestion.file

import com.example.commentanalyzer.domain.model.CommentStatistics
import com.example.commentanalyzer.domain.model.UiComment
import com.example.commentanalyzer.domain.model.VideoCommentExportData
import com.example.commentanalyzer.domain.model.VideoDetails
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

class TxtParseException(
    val reasonCode: String,
    val section: String?,
    val lineNumber: Int?
) : IllegalArgumentException(reasonCode)

object TxtExportParser {
    const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024

    private val videoHeader = Regex("^(\\d[\\d,]*) views$")
    private val declaredComments = Regex("^(\\d[\\d,\\u00a0]*) comments$")
    private val showing = Regex("^Showing (\\d+) / (\\d+) results$")
    private val commentHeader = Regex("^(@[^|]+) \\| (.+)$")
    private val ordinal = Regex("^#([1-9]\\d*)$")
    private val replyMarker = Regex("^(?:[^\\p{Alnum}\\s]+\\s+)?Load\\s+(\\d+)\\s+repl(?:y|ies)$", RegexOption.IGNORE_CASE)
    private val localTimestampFormatter = DateTimeFormatter
        .ofPattern("M/d/uuuu, h:mm:ss a", Locale.US)
        .withResolverStyle(ResolverStyle.STRICT)

    fun parse(inputStream: InputStream): VideoCommentExportData {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalBytes = 0
        while (true) {
            val read = inputStream.read(buffer)
            if (read < 0) break
            totalBytes += read
            if (totalBytes > MAX_FILE_SIZE_BYTES) {
                throw TxtParseException("file_too_large", null, null)
            }
            output.write(buffer, 0, read)
        }

        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (_: Exception) {
            throw TxtParseException("invalid_utf8", null, null)
        }
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        return parseLines(normalized.split('\n'))
    }

    fun parseLines(sourceLines: List<String>): VideoCommentExportData {
        val lines = sourceLines.toMutableList()
        if (lines.isNotEmpty()) lines[0] = lines[0].removePrefix("\uFEFF")
        val markerIndex = lines.indexOf("YouTube Comment Viewer")
        val videoIndex = lines.indexOf("VIDEO")
        val statisticsIndex = lines.indexOf("▲ STATISTICS")
        val commentsIndex = lines.indexOf("COMMENTS")
        if (markerIndex < 0) fail("missing_file_marker", null, 1)
        if (videoIndex < 0 || (markerIndex >= 0 && videoIndex <= markerIndex)) {
            fail("missing_video_section", "VIDEO", videoIndex.takeIf { it >= 0 }?.plus(1))
        }
        if (statisticsIndex < 0 || statisticsIndex <= videoIndex) fail("missing_statistics_section", "STATISTICS", null)
        if (commentsIndex < 0 || commentsIndex <= statisticsIndex) fail("missing_comments_section", "COMMENTS", null)

        val title = valueAt(lines, videoIndex + 1, "VIDEO")
        val channel = valueAt(lines, videoIndex + 2, "VIDEO")
        if (title.isBlank()) fail("missing_title", "VIDEO", videoIndex + 2)
        if (channel.isBlank()) fail("missing_channel", "VIDEO", videoIndex + 3)

        val viewsLineIndex = videoIndex + 3
        val views = videoHeader.matchEntire(lines.getOrNull(viewsLineIndex)?.trim().orEmpty())
            ?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
            ?: fail("invalid_views", "VIDEO", viewsLineIndex + 1)
        val unlabeledStatistic = lines.getOrNull(videoIndex + 4)?.trim().orEmpty()
        if (!unlabeledStatistic.matches(Regex("\\d+"))) fail("invalid_unlabeled_statistic", "VIDEO", videoIndex + 5)

        val publishedLineIndex = videoIndex + 5
        val publishedRaw = lines.getOrNull(publishedLineIndex)?.trim()
            ?.takeIf { it.startsWith("Published:") }
            ?.substringAfter("Published:")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fail("invalid_published_timestamp", "VIDEO", publishedLineIndex + 1)
        val publishedLocal = try {
            LocalDateTime.parse(publishedRaw, localTimestampFormatter)
        } catch (_: DateTimeParseException) {
            fail("invalid_published_timestamp", "VIDEO", publishedLineIndex + 1)
        }
        val totalCommentsIndex = videoIndex + 6
        val totalComments = declaredComments.matchEntire(lines.getOrNull(totalCommentsIndex)?.trim().orEmpty())
            ?.groupValues?.get(1)?.replace(",", "")?.replace("\u00a0", "")?.toIntOrNull()
            ?: fail("invalid_declared_comment_count", "VIDEO", totalCommentsIndex + 1)

        val topLevel = statistic(lines, statisticsIndex, "Top-level comments", "top_level_comments")
        val totalLikes = statistic(lines, statisticsIndex, "Total likes", "total_likes")
        val avgComments = decimalStatistic(lines, statisticsIndex, "Average comments per day", "average_comments")
        val avgLikes = decimalStatistic(lines, statisticsIndex, "Average likes per comment", "average_likes")
        if (avgComments < 0 || avgLikes < 0) fail("invalid_statistics_value", "STATISTICS", statisticsIndex + 1)

        val showingIndex = (commentsIndex + 1 until lines.size).firstOrNull {
            lines[it].trim().startsWith("Showing ")
        } ?: fail("missing_showing_count", "COMMENTS", commentsIndex + 1)
        val shown = showing.matchEntire(lines[showingIndex].trim())
            ?: fail("invalid_showing_count", "COMMENTS", showingIndex + 1)
        val shownCount = shown.groupValues[1].toIntOrNull() ?: fail("invalid_showing_count", "COMMENTS", showingIndex + 1)
        val shownTotal = shown.groupValues[2].toIntOrNull() ?: fail("invalid_showing_count", "COMMENTS", showingIndex + 1)

        val comments = mutableListOf<UiComment>()
        var declaredReplies = 0
        var index = showingIndex + 1
        while (index < lines.size) {
            val current = lines[index]
            if (isFooter(current)) break
            if (current.isBlank()) {
                index++
                continue
            }
            if (current.startsWith("@") && commentHeader.matchEntire(current) == null) {
                fail("malformed_comment_header", "COMMENTS", index + 1)
            }
            val header = commentHeader.matchEntire(current)
                ?: fail("unexpected_comment_content", "COMMENTS", index + 1)
            val author = header.groupValues[1]
            val timestampRaw = header.groupValues[2]
            val localTimestamp = try {
                LocalDateTime.parse(timestampRaw, localTimestampFormatter)
            } catch (_: DateTimeParseException) {
                fail("invalid_comment_timestamp", "COMMENTS", index + 1)
            }
            val ordinalIndex = index + 1
            val ordinalMatch = ordinal.matchEntire(lines.getOrNull(ordinalIndex)?.trim().orEmpty())
                ?: fail("invalid_comment_ordinal", "COMMENTS", ordinalIndex + 1)
            val ordinalValue = ordinalMatch.groupValues[1].toIntOrNull()
                ?: fail("invalid_comment_ordinal", "COMMENTS", ordinalIndex + 1)
            if (ordinalValue != comments.size + 1) fail("noncontiguous_comment_ordinal", "COMMENTS", ordinalIndex + 1)

            val textLines = mutableListOf<String>()
            var likes = 0
            var repliesHint: String? = null
            var replyCount = 0
            index = ordinalIndex + 1
            while (index < lines.size) {
                val bodyLine = lines[index]
                if (isFooter(bodyLine) || commentHeader.matches(bodyLine)) break
                if (bodyLine.startsWith("@")) fail("malformed_comment_header", "COMMENTS", index + 1)

                val trimmed = bodyLine.trim()
                val reply = replyMarker.matchEntire(trimmed)
                if (reply != null) {
                    repliesHint = trimmed
                    replyCount = reply.groupValues[1].toIntOrNull()
                        ?: fail("invalid_reply_count", "COMMENTS", index + 1)
                    index++
                    break
                }

                val numeric = trimmed.toIntOrNull()
                if (numeric != null && textLines.isEmpty()) {
                    fail("ambiguous_numeric_only_body", "COMMENTS", index + 1)
                }
                val nextLine = lines.getOrNull(index + 1)
                val isTrailingLikes = numeric != null && textLines.isNotEmpty() &&
                    (nextLine == null || isFooter(nextLine) || commentHeader.matches(nextLine) || replyMarker.matches(nextLine.trim()))
                if (isTrailingLikes) {
                    if (numeric < 0) fail("invalid_comment_likes", "COMMENTS", index + 1)
                    likes = numeric
                    index++
                    if (index < lines.size && replyMarker.matches(lines[index].trim())) {
                        val replyLine = lines[index].trim()
                        repliesHint = replyLine
                        replyCount = replyMarker.matchEntire(replyLine)?.groupValues?.get(1)?.toIntOrNull()
                            ?: fail("invalid_reply_count", "COMMENTS", index + 1)
                        index++
                    }
                    break
                }
                textLines.add(bodyLine)
                index++
            }
            if (textLines.isEmpty()) fail("empty_comment_body", "COMMENTS", ordinalIndex + 2)
            comments += UiComment(
                id = ordinalValue.toString(),
                author = author,
                date = timestampRaw,
                text = textLines.joinToString("\n"),
                likes = likes,
                repliesHint = repliesHint,
                declaredReplyCount = replyCount,
                publishedLocalDateTime = localTimestamp
            )
            declaredReplies += replyCount
        }

        if (comments.isEmpty()) fail("no_comments", "COMMENTS", showingIndex + 2)
        if (comments.size != shownCount || comments.size != shownTotal || comments.size != topLevel) {
            fail("top_level_count_mismatch", "COMMENTS", showingIndex + 1)
        }
        if (comments.sumOf { it.likes } != totalLikes) fail("likes_count_mismatch", "STATISTICS", statisticsIndex + 1)
        if (comments.size + declaredReplies != totalComments) fail("total_comment_count_mismatch", "VIDEO", totalCommentsIndex + 1)

        return VideoCommentExportData(
            videoDetails = VideoDetails(
                title = title,
                channel = channel,
                views = views,
                unlabeledStatisticRaw = unlabeledStatistic,
                publishedAt = publishedRaw,
                publishedLocalDateTime = publishedLocal,
                totalCommentsCount = totalComments
            ),
            statistics = CommentStatistics(
                topLevelComments = topLevel,
                totalLikes = totalLikes,
                avgCommentsPerDay = avgComments,
                avgLikesPerComment = avgLikes,
                declaredReplyCount = declaredReplies
            ),
            comments = comments
        )
    }

    private fun statistic(lines: List<String>, sectionIndex: Int, label: String, code: String): Int {
        val index = (sectionIndex + 1 until lines.size).firstOrNull { lines[it].substringBefore('\t').trim() == label }
            ?: fail("missing_$code", "STATISTICS", sectionIndex + 1)
        return lines[index].substringAfterLast('\t', "").trim().toIntOrNull()
            ?: fail("invalid_$code", "STATISTICS", index + 1)
    }

    private fun decimalStatistic(lines: List<String>, sectionIndex: Int, label: String, code: String): Double {
        val index = (sectionIndex + 1 until lines.size).firstOrNull { lines[it].substringBefore('\t').trim() == label }
            ?: fail("missing_$code", "STATISTICS", sectionIndex + 1)
        return lines[index].substringAfterLast('\t', "").trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() } ?: fail("invalid_$code", "STATISTICS", index + 1)
    }

    private fun valueAt(lines: List<String>, index: Int, section: String): String =
        lines.getOrNull(index)?.trim() ?: fail("incomplete_video_metadata", section, index + 1)

    private fun isFooter(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "Terms of Service | Dark mode | Report issues" ||
            trimmed.startsWith("v3.3.4")
    }

    private fun fail(code: String, section: String?, line: Int?): Nothing =
        throw TxtParseException(code, section, line)
}
