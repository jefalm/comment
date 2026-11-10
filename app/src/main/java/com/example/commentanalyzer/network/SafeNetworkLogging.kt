package com.example.commentanalyzer.network

import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SafeNetworkLogging {
    private val sensitiveKeys = setOf("key", "access_token", "token", "api_key", "apikey", "client_secret", "authorization")

    fun sanitizeUrl(rawUrl: String): String {
        return try {
            val url = URL(rawUrl)
            val query = url.query
            if (query.isNullOrEmpty()) return "${url.protocol}://${url.host}${url.path}"

            val sanitizedQuery = query.split("&").joinToString("&") { param ->
                val parts = param.split("=", limit = 2)
                val decodedName = runCatching {
                    URLDecoder.decode(parts[0].replace("+", "%2B"), StandardCharsets.UTF_8.name())
                }.getOrDefault("")
                if (parts.size == 2 && sensitiveKeys.any { decodedName.contains(it, ignoreCase = true) }) {
                    "${parts[0]}=REDACTED"
                } else {
                    param
                }
            }
            "${url.protocol}://${url.host}${url.path}?$sanitizedQuery"
        } catch (e: Exception) {
            "[UNPARSEABLE_URL]"
        }
    }

    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) ->
        if (sensitiveKeys.any { name.contains(it.replace("_", "-"), ignoreCase = true) } ||
            name.contains("api-key", ignoreCase = true) || name.contains("secret", ignoreCase = true)
        ) "REDACTED" else value
    }
}
