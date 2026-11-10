package com.example.commentanalyzer.network

import java.net.URL

object SafeNetworkLogging {
    fun sanitizeUrl(rawUrl: String): String {
        return try {
            val url = URL(rawUrl)
            val query = url.query
            if (query.isNullOrEmpty()) return "${url.protocol}://${url.host}${url.path}"

            val sensitiveKeys = listOf("key", "access_token", "token", "api_key", "apikey", "client_secret")
            val sanitizedQuery = query.split("&").joinToString("&") { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2 && sensitiveKeys.any { parts[0].contains(it, ignoreCase = true) }) {
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
}
