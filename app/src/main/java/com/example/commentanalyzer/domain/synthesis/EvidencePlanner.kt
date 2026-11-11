package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput

object EvidencePlanner {
    private const val MAX_EVIDENCE_COUNT = 30
    private const val MAX_EVIDENCE_BYTES = 128 * 1024L
    private const val MAX_EVIDENCE_TOKENS = 16000L

    fun planEvidence(comments: List<CommentEntity>): List<SynthesisEvidenceInput> {
        if (comments.isEmpty()) return emptyList()

        val sorted = comments.sortedBy { it.localId }
        val selected = mutableMapOf<String, CommentEntity>()

        val categories = sorted.mapNotNull { it.category }.distinct().sorted()
        val sentiments = sorted.mapNotNull { it.sentiment }.distinct().sorted()

        for (cat in categories) {
            val matching = sorted.filter { it.category == cat }
            if (matching.isNotEmpty()) {
                val representative = matching.minByOrNull { it.likeCount ?: 0 } ?: matching.first()
                if (isWithinItemBounds(representative)) {
                    selected[representative.localId] = representative
                }
                val highEngagement = matching.maxByOrNull { it.likeCount ?: 0 } ?: matching.first()
                if (isWithinItemBounds(highEngagement)) {
                    selected[highEngagement.localId] = highEngagement
                }
            }
        }

        for (sent in sentiments) {
            val matching = sorted.filter { it.sentiment == sent }
            if (matching.isNotEmpty()) {
                val rep = matching.minByOrNull { it.likeCount ?: 0 } ?: matching.first()
                if (isWithinItemBounds(rep)) {
                    selected[rep.localId] = rep
                }
            }
        }

        val catCounts = sorted.groupingBy { it.category ?: "unknown" }.eachCount()
        val minorityCategories = catCounts.entries.sortedBy { it.value }.map { it.key }
        for (cat in minorityCategories) {
            val matching = sorted.filter { (it.category ?: "unknown") == cat }
            if (matching.isNotEmpty() && selected.size < MAX_EVIDENCE_COUNT) {
                val candidate = matching.first()
                if (isWithinItemBounds(candidate)) {
                    selected[candidate.localId] = candidate
                }
            }
        }

        for (comment in sorted) {
            if (selected.size >= MAX_EVIDENCE_COUNT) break
            if (isWithinItemBounds(comment)) {
                selected[comment.localId] = comment
            }
        }

        val finalizedList = selected.values.sortedBy { it.localId }

        val bounded = mutableListOf<CommentEntity>()
        var totalBytes = 0L
        var totalTokens = 0L

        for (item in finalizedList) {
            if (bounded.size >= MAX_EVIDENCE_COUNT) break
            val textBytes = item.originalText.toByteArray(Charsets.UTF_8).size.toLong()
            val textTokens = (item.originalText.length / 4).coerceAtLeast(1).toLong()

            if (totalBytes + textBytes > MAX_EVIDENCE_BYTES || totalTokens + textTokens > MAX_EVIDENCE_TOKENS) {
                continue
            }

            bounded.add(item)
            totalBytes += textBytes
            totalTokens += textTokens
        }

        return bounded.map {
            SynthesisEvidenceInput(
                commentId = it.localId,
                text = it.originalText,
                category = it.category,
                sentiment = it.sentiment,
                likeCount = it.likeCount,
                sentimentConfidence = it.sentimentConfidence,
                categoryConfidence = it.categoryConfidence
            )
        }
    }

    private fun isWithinItemBounds(comment: CommentEntity): Boolean {
        val textBytes = comment.originalText.toByteArray(Charsets.UTF_8).size.toLong()
        val textTokens = (comment.originalText.length / 4).coerceAtLeast(1).toLong()
        return textBytes <= MAX_EVIDENCE_BYTES && textTokens <= MAX_EVIDENCE_TOKENS
    }
}
