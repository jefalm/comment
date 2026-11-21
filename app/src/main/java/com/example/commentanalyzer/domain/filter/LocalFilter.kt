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

private data class DuplicateSurvivor(
    val sourceKey: String,
    val likeCount: Int,
    val publishedAtEpochMillis: Long
)

private val DUPLICATE_SURVIVOR_ORDER: Comparator<DuplicateSurvivor> =
    compareBy<DuplicateSurvivor> { it.likeCount }
        .thenBy { -it.publishedAtEpochMillis }
        .thenBy { it.sourceKey }

private fun duplicateClusterKey(normalizedText: String): String =
    normalizedText.trim().lowercase()

/**
 * Import-independent identity for a comment. Prefers the source-native comment id and falls back
 * to the source fingerprint. [CommentEntity.localId] must never be used as a deterministic
 * identity or tie-breaker: it is derived from the importId and therefore differs between imports
 * of the same source population.
 */
internal fun sourceStableKey(sourceCommentId: String?, fingerprintSha256: String): String =
    sourceCommentId ?: fingerprintSha256

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

    /**
     * Import-wide exact-duplicate group. [survivorSourceKey] is a source-stable identity, so the
     * survivor never depends on [CommentEntity.localId] or on which bounded processing chunk a
     * comment happened to land in.
     */
    data class DuplicateGroup(
        val survivorSourceKey: String,
        val clusterSize: Int
    )

    /** Accumulates import-wide exact-duplicate metadata without holding whole batches at once. */
    class DuplicateIndexBuilder {
        private val survivors = HashMap<String, DuplicateSurvivor>()
        private val clusterSizes = HashMap<String, Int>()

        fun add(
            normalizedText: String,
            sourceKey: String,
            likeCount: Int?,
            publishedAtEpochMillis: Long?
        ) {
            val key = duplicateClusterKey(normalizedText)
            clusterSizes[key] = (clusterSizes[key] ?: 0) + 1
            val candidate = DuplicateSurvivor(
                sourceKey = sourceKey,
                likeCount = likeCount ?: 0,
                publishedAtEpochMillis = publishedAtEpochMillis ?: Long.MAX_VALUE
            )
            val current = survivors[key]
            survivors[key] = if (current == null) {
                candidate
            } else {
                maxOf(current, candidate, DUPLICATE_SURVIVOR_ORDER)
            }
        }

        fun build(): Map<String, DuplicateGroup> {
            val groups = HashMap<String, DuplicateGroup>(survivors.size)
            for ((key, survivor) in survivors) {
                groups[key] = DuplicateGroup(
                    survivorSourceKey = survivor.sourceKey,
                    clusterSize = clusterSizes[key] ?: 1
                )
            }
            return groups
        }
    }

    /**
     * Evaluates one bounded batch of comments against import-wide [duplicateGroups] metadata.
     * Only per-comment text rules are evaluated here, so the expensive work stays bounded while
     * the analytical result stays import-wide and independent of batch boundaries.
     */
    fun evaluateBatch(
        comments: List<CommentEntity>,
        duplicateGroups: Map<String, DuplicateGroup>
    ): Map<String, BatchFilterResult> {
        val results = LinkedHashMap<String, BatchFilterResult>(comments.size)
        for (comment in comments) {
            val group = duplicateGroups[duplicateClusterKey(comment.normalizedText)]
            if (group == null || group.clusterSize <= 1) {
                results[comment.localId] = BatchFilterResult(evaluate(comment.normalizedText), 1)
                continue
            }
            val isSurvivor = group.survivorSourceKey ==
                sourceStableKey(comment.sourceCommentId, comment.fingerprintSha256)
            results[comment.localId] = if (isSurvivor) {
                BatchFilterResult(evaluate(comment.normalizedText), group.clusterSize)
            } else {
                BatchFilterResult(
                    FilterResult(FilterOutcome.REJECT, listOf("RULE_EXACT_DUPLICATE")),
                    group.clusterSize
                )
            }
        }
        return results
    }
}
