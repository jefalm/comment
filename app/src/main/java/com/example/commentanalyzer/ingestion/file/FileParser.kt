package com.example.commentanalyzer.ingestion.file

data class ParsedCommentRow(
    val rowNumber: Int,
    val sourceCommentId: String?,
    val text: String?,
    val author: String?,
    val publishedAt: String?,
    val videoId: String?,
    val error: String?
)

object FileParser {
    fun parseCsvLine(rowNumber: Int, line: String, delimiter: Char = ','): ParsedCommentRow {
        val parts = line.split(delimiter)
        if (parts.isEmpty()) {
            return ParsedCommentRow(rowNumber, null, null, null, null, null, "EMPTY_ROW")
        }
        val text = parts.getOrNull(0)?.trim()
        if (text.isNullOrEmpty()) {
            return ParsedCommentRow(rowNumber, null, null, null, null, null, "MISSING_TEXT")
        }
        return ParsedCommentRow(
            rowNumber = rowNumber,
            sourceCommentId = parts.getOrNull(1)?.trim(),
            text = text,
            author = parts.getOrNull(2)?.trim(),
            publishedAt = parts.getOrNull(3)?.trim(),
            videoId = parts.getOrNull(4)?.trim(),
            error = null
        )
    }
}
