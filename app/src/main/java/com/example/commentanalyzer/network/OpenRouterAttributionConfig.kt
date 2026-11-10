package com.example.commentanalyzer.network

data class OpenRouterAttributionConfig(
    val title: String = "YouTube Comment Analyzer",
    val refererUrl: String? = null
) {
    fun getValidatedReferer(): String? {
        val trimmed = refererUrl?.trim()
        if (trimmed.isNullOrEmpty()) return null
        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://localhost", ignoreCase = true)) {
            return trimmed
        }
        return null
    }
}
