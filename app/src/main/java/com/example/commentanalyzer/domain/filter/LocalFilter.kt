package com.example.commentanalyzer.domain.filter

import com.example.commentanalyzer.data.local.entity.CommentEntity

data class FilterResult(
    val outcome: FilterOutcome,
    val ruleIds: List<String>,
    val filterVersion: Int = 1
)

enum class FilterOutcome {
    PASS, REJECT, REVIEW
}

object LocalFilter {
    fun evaluate(normalizedText: String): FilterResult {
        val rules = mutableListOf<String>()
        if (normalizedText.isBlank()) {
            rules.add("RULE_EMPTY_TEXT")
            return FilterResult(FilterOutcome.REJECT, rules)
        }
        if (Regex("(.)\\1{9,}").containsMatchIn(normalizedText)) {
            rules.add("RULE_REPEATED_CHARS")
            return FilterResult(FilterOutcome.REJECT, rules)
        }

        val textWithoutUrls = normalizedText.replace(Regex("https?://\\S+|www\\.\\S+"), "").trim()
        if (textWithoutUrls.isBlank() || (!textWithoutUrls.any { it.isLetterOrDigit() } && textWithoutUrls != "?" && textWithoutUrls != "!")) {
            if (normalizedText.contains(Regex("https?://|www\\."))) {
                rules.add("RULE_URL_ONLY_SPAM")
                return FilterResult(FilterOutcome.REJECT, rules)
            }
        }

        if (!normalizedText.any { it.isLetterOrDigit() } && normalizedText != "?" && normalizedText != "!") {
            rules.add("RULE_NO_ALPHANUMERIC")
            return FilterResult(FilterOutcome.REJECT, rules)
        }

        val linkCount = Regex("https?://\\S+|www\\.\\S+").findAll(normalizedText).count()
        if (linkCount > 3) {
            rules.add("RULE_EXCESSIVE_LINKS")
            return FilterResult(FilterOutcome.REVIEW, rules)
        }
        rules.add("RULE_PASS")
        return FilterResult(FilterOutcome.PASS, rules)
    }

    data class BatchFilterResult(
        val filterResult: FilterResult,
        val nearDuplicateClusterSize: Int
    )

    fun evaluateBatch(comments: List<CommentEntity>): Map<String, BatchFilterResult> {
        val results = mutableMapOf<String, BatchFilterResult>()
        val clusters = comments.groupBy { it.normalizedText.trim().lowercase() }

        for ((_, cluster) in clusters) {
            val clusterSize = cluster.size
            if (clusterSize == 1) {
                val comment = cluster.first()
                val eval = evaluate(comment.normalizedText)
                results[comment.localId] = BatchFilterResult(eval, 1)
            } else {
                val primary = cluster.maxWithOrNull(
                    compareBy<CommentEntity> { it.likeCount ?: 0 }
                        .thenBy { -(it.publishedAtEpochMillis ?: Long.MAX_VALUE) }
                        .thenBy { it.localId }
                ) ?: cluster.first()

                for (comment in cluster) {
                    if (comment.localId == primary.localId) {
                        val eval = evaluate(comment.normalizedText)
                        results[comment.localId] = BatchFilterResult(eval, clusterSize)
                    } else {
                        results[comment.localId] = BatchFilterResult(
                            FilterResult(FilterOutcome.REJECT, listOf("RULE_EXACT_DUPLICATE")),
                            clusterSize
                        )
                    }
                }
            }
        }
        return results
    }
}
