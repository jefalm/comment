package com.example.commentanalyzer.domain.normalize

import java.text.Normalizer as JavaNormalizer
import java.time.Instant

object TextNormalizer {
    fun normalize(rawText: String?): String {
        if (rawText.isNullOrEmpty()) return ""
        var cleaned = if (rawText.startsWith("\uFEFF")) rawText.substring(1) else rawText
        cleaned = JavaNormalizer.normalize(cleaned, JavaNormalizer.Form.NFKC)
        cleaned = cleaned.replace("\r\n", "\n").replace("\r", "\n")
        cleaned = cleaned.trim().replace(Regex("\\s+"), " ")
        return cleaned
    }
}

object AuthorNormalizer {
    fun normalize(rawAuthor: String?): String {
        val trimmed = rawAuthor?.trim()
        if (trimmed.isNullOrEmpty()) return "[Unknown]"
        return TextNormalizer.normalize(trimmed)
    }
}

object TimestampNormalizer {
    fun parseToEpochMillis(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val instant = Instant.parse(isoString.trim())
            instant.toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
