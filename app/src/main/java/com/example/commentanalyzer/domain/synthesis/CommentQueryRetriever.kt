package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import kotlin.math.ln

object CommentQueryRetriever {
    const val MAX_QA_COMMENTS = 120
    const val MAX_QA_TOKENS = 32_000L

    private val STOPWORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
        "aren't", "as", "at", "be", "because", "been", "before", "being", "below", "between",
        "both", "but", "by", "can't", "cannot", "could", "couldn't", "did", "didn't", "do",
        "does", "doesn't", "doing", "don't", "down", "during", "each", "few", "for", "from",
        "further", "had", "hadn't", "has", "hasn't", "have", "haven't", "having", "he", "he'd",
        "he'll", "he's", "her", "here", "here's", "hers", "herself", "him", "himself", "his",
        "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't",
        "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself",
        "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our",
        "ours", "ourselves", "out", "over", "own", "same", "shan't", "she", "she'd", "she'll",
        "she's", "should", "shouldn't", "so", "some", "such", "than", "that", "that's", "the",
        "their", "theirs", "them", "themselves", "then", "there", "there's", "these", "they",
        "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too",
        "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're",
        "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's",
        "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would",
        "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself",
        "yourselves"
    )

    private val TOKEN_REGEX = Regex("""\w+""")

    fun tokenize(text: String): List<String> {
        return TOKEN_REGEX.findAll(text.lowercase())
            .map { it.value }
            .filter { it.length > 1 && it !in STOPWORDS }
            .toList()
    }

    fun estimateTokens(text: String): Long {
        return maxOf(1L, (text.length / 4).toLong())
    }

    fun retrieveRelevantComments(
        importId: String,
        configVersion: Long,
        question: String,
        candidateComments: List<CommentEntity>,
        activeSynthesisCitedIds: Set<String> = emptySet(),
        maxComments: Int = MAX_QA_COMMENTS,
        maxTokens: Long = MAX_QA_TOKENS
    ): List<CommentEntity> {
        // Scoped strictly to importId and configVersion
        val eligible = candidateComments.filter {
            it.importId == importId && it.processingConfigVersion == configVersion
        }
        if (eligible.isEmpty()) return emptyList()

        val queryTokens = tokenize(question)

        // Precompute document tokens
        val docTokens = eligible.associate { comment ->
            val text = "${comment.originalText} ${comment.normalizedText}"
            comment.localId to tokenize(text)
        }

        val totalDocs = eligible.size
        val avgDocLen = docTokens.values.map { it.size }.average().coerceAtLeast(1.0)

        // Compute document frequency n(q)
        val docFreq = mutableMapOf<String, Int>()
        for (q in queryTokens) {
            var count = 0
            for ((_, tokens) in docTokens) {
                if (q in tokens) count++
            }
            docFreq[q] = count
        }

        val k1 = 1.2
        val b = 0.75

        // Map by sourceCommentId for thread preservation
        val commentsBySourceId = eligible.filter { it.sourceCommentId != null }
            .associateBy { it.sourceCommentId!! }

        data class ScoredComment(
            val comment: CommentEntity,
            val bm25Score: Double,
            val signalBoost: Double,
            val citedBoost: Double,
            val totalScore: Double
        )

        val scored = eligible.map { comment ->
            val tokens = docTokens[comment.localId] ?: emptyList()
            val docLen = tokens.size

            var bm25 = 0.0
            if (queryTokens.isNotEmpty()) {
                val termFreqs = tokens.groupingBy { it }.eachCount()
                for (q in queryTokens) {
                    val tf = termFreqs[q] ?: 0
                    if (tf > 0) {
                        val nq = docFreq[q] ?: 0
                        val idf = ln(1.0 + (totalDocs - nq + 0.5) / (nq + 0.5))
                        val tfNorm = (tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (docLen / avgDocLen)))
                        bm25 += idf * tfNorm
                    }
                }
            }

            val signalScore = (comment.signalScore ?: 0.0).coerceAtLeast(0.0)
            val signalBoost = signalScore * 0.5
            val citedBoost = if (comment.localId in activeSynthesisCitedIds) 2.0 else 0.0
            val totalScore = bm25 + signalBoost + citedBoost

            ScoredComment(comment, bm25, signalBoost, citedBoost, totalScore)
        }

        // Deterministic sorting: totalScore DESC, signalScore DESC, publishedAtEpochMillis DESC, localId ASC
        val sortedScored = scored.sortedWith(
            compareByDescending<ScoredComment> { it.totalScore }
                .thenByDescending { it.comment.signalScore ?: 0.0 }
                .thenByDescending { it.comment.publishedAtEpochMillis ?: 0L }
                .thenBy { it.comment.localId }
        )

        val selected = mutableListOf<CommentEntity>()
        val selectedIds = mutableSetOf<String>()
        var currentTokens = 0L

        fun tryAdd(comment: CommentEntity): Boolean {
            if (comment.localId in selectedIds) return true
            if (selected.size >= maxComments) return false

            val estTokens = estimateTokens(comment.originalText)
            if (currentTokens + estTokens > maxTokens && selected.isNotEmpty()) {
                return false
            }

            selected.add(comment)
            selectedIds.add(comment.localId)
            currentTokens += estTokens
            return true
        }

        for (item in sortedScored) {
            if (selected.size >= maxComments) break

            val comment = item.comment

            // Thread preservation: if reply and root is available, add root first
            val parentSourceId = comment.parentSourceCommentId
            if (parentSourceId != null) {
                val rootComment = commentsBySourceId[parentSourceId]
                if (rootComment != null && rootComment.localId !in selectedIds) {
                    tryAdd(rootComment)
                }
            }

            if (!tryAdd(comment)) {
                if (selected.size >= maxComments) break
            }
        }

        return selected
    }
}
