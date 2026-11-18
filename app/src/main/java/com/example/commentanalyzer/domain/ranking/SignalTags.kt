package com.example.commentanalyzer.domain.ranking

object SignalTags {
    const val DEBATE = "debate"
    const val METRICS = "metrics"
    const val NOVEL = "novel"
    const val QUESTION = "question"
    const val TIMESTAMP = "timestamp"

    val ALLOWED_VOCABULARY = setOf(DEBATE, METRICS, NOVEL, QUESTION, TIMESTAMP)

    fun canonicalize(rawTags: String?): String? {
        if (rawTags.isNullOrBlank()) return null
        val valid = rawTags.split(',')
            .map { it.trim().lowercase() }
            .filter { it in ALLOWED_VOCABULARY }
            .distinct()
            .sorted()
        return if (valid.isEmpty()) null else valid.joinToString(",")
    }

    fun isValid(rawTags: String?): Boolean {
        if (rawTags == null) return true
        if (rawTags.isBlank()) return false
        val parts = rawTags.split(',')
        return parts.all { it in ALLOWED_VOCABULARY } && parts == parts.distinct().sorted()
    }
}
