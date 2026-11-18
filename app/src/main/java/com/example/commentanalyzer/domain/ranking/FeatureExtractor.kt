package com.example.commentanalyzer.domain.ranking

import com.example.commentanalyzer.data.local.entity.CommentEntity
import kotlin.math.ln

data class ExtractedFeatures(
    val logLikes: Double,
    val logReplies: Double,
    val wordCount: Double,
    val questionMarkCount: Double,
    val hasTimestamp: Double,
    val hasPercentage: Double,
    val hasCurrency: Double,
    val isReply: Double,
    val rareTokenScore: Double,
    val hasDiscourseMarker: Double,
    val nearDuplicateClusterSize: Double,
    val charCount: Double = 0.0,
    val sentenceCount: Double = 0.0,
    val threadSize: Double = 0.0,
    val uniqueTokenRatio: Double = 0.0
) {
    fun toMap(): Map<String, Double> = mapOf(
        "logLikes" to logLikes,
        "logReplies" to logReplies,
        "wordCount" to wordCount,
        "questionMarkCount" to questionMarkCount,
        "hasTimestamp" to hasTimestamp,
        "hasPercentage" to hasPercentage,
        "hasCurrency" to hasCurrency,
        "isReply" to isReply,
        "rareTokenScore" to rareTokenScore,
        "hasDiscourseMarker" to hasDiscourseMarker,
        "nearDuplicateClusterSize" to nearDuplicateClusterSize
    )
}

object FeatureExtractor {
    private val TIMESTAMP_REGEX = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val PERCENTAGE_REGEX = Regex("""\b\d+(?:\.\d+)?%""")
    private val CURRENCY_REGEX = Regex("""(?:R\$|US\$|[$€£])\s?\d+(?:[.,]\d+)?""")
    private val DISCOURSE_MARKER_REGEX = Regex(
        """(?i)\b(however|actually|wrong|disagree|but|porém|discordo|na verdade)\b"""
    )
    private val WORD_TOKEN_REGEX = Regex("""\b\w+\b""")

    fun extract(
        comment: CommentEntity,
        allCommentsInImport: List<CommentEntity>,
        nearDuplicateClusterSize: Int = 1
    ): ExtractedFeatures {
        val text = comment.originalText
        val words = WORD_TOKEN_REGEX.findAll(text.lowercase()).map { it.value }.toList()
        val wordCountVal = words.size.toDouble()
        val charCountVal = text.length.toDouble()
        val sentenceCountVal = text.split(Regex("""[.!?]+""")).filter { it.isNotBlank() }.size.coerceAtLeast(1).toDouble()
        val questionMarkCountVal = text.count { it == '?' }.toDouble()

        val logLikesVal = ln(1.0 + (comment.likeCount ?: 0))
        val logRepliesVal = ln(1.0 + (comment.declaredReplyCount ?: 0))

        val hasTimestampVal = if (TIMESTAMP_REGEX.containsMatchIn(text)) 1.0 else 0.0
        val hasPercentageVal = if (PERCENTAGE_REGEX.containsMatchIn(text)) 1.0 else 0.0
        val hasCurrencyVal = if (CURRENCY_REGEX.containsMatchIn(text)) 1.0 else 0.0
        val isReplyVal = if (comment.parentSourceCommentId != null) 1.0 else 0.0
        val hasDiscourseMarkerVal = if (DISCOURSE_MARKER_REGEX.containsMatchIn(text)) 1.0 else 0.0

        val threadSizeVal = if (comment.sourceCommentId != null) {
            1.0 + allCommentsInImport.count { it.parentSourceCommentId == comment.sourceCommentId }
        } else {
            1.0
        }

        val uniqueWords = words.toSet()
        val uniqueTokenRatioVal = if (words.isNotEmpty()) uniqueWords.size.toDouble() / words.size else 0.0

        val nImport = allCommentsInImport.size.toDouble().coerceAtLeast(1.0)
        val docFreqs = mutableMapOf<String, Int>()
        for (w in uniqueWords) {
            docFreqs[w] = allCommentsInImport.count { c ->
                c.originalText.lowercase().contains(w)
            }
        }
        val rareTokenScoreVal = if (uniqueWords.isNotEmpty()) {
            val totalIdf = uniqueWords.sumOf { w ->
                val df = docFreqs[w] ?: 1
                ln((nImport + 1.0) / (df + 1.0))
            }
            (totalIdf / uniqueWords.size).coerceIn(0.0, 10.0) / 10.0
        } else {
            0.0
        }

        return ExtractedFeatures(
            logLikes = logLikesVal,
            logReplies = logRepliesVal,
            wordCount = wordCountVal,
            questionMarkCount = questionMarkCountVal,
            hasTimestamp = hasTimestampVal,
            hasPercentage = hasPercentageVal,
            hasCurrency = hasCurrencyVal,
            isReply = isReplyVal,
            rareTokenScore = rareTokenScoreVal,
            hasDiscourseMarker = hasDiscourseMarkerVal,
            nearDuplicateClusterSize = nearDuplicateClusterSize.toDouble(),
            charCount = charCountVal,
            sentenceCount = sentenceCountVal,
            threadSize = threadSizeVal,
            uniqueTokenRatio = uniqueTokenRatioVal
        )
    }
}
