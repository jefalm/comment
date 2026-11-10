package com.example.commentanalyzer.ingestion.youtube

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object YouTubeVideoIdParser {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun parse(input: String): String? {
        val value = input.trim()
        if (videoIdPattern.matches(value)) return value

        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null || uri.port != -1) return null

        val host = uri.host?.lowercase() ?: return null
        val candidate = when {
            host == "youtu.be" -> uri.path.trim('/').substringBefore('/').takeIf { uri.path.trim('/').count { c -> c == '/' } == 0 }
            host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com" -> {
                val segments = uri.path.split('/').filter(String::isNotEmpty)
                when {
                    uri.path == "/watch" -> queryValues(uri.rawQuery, "v").singleOrNull()
                    segments.size == 2 && segments[0] == "shorts" -> segments[1]
                    else -> null
                }
            }
            else -> null
        }
        return candidate?.takeIf(videoIdPattern::matches)
    }

    private fun queryValues(query: String?, name: String): List<String> = query.orEmpty()
        .split('&')
        .mapNotNull { parameter ->
            val parts = parameter.split('=', limit = 2)
            if (parts.size != 2 || URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()) != name) null
            else URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
        }
}
