package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput
import com.example.commentanalyzer.domain.ranking.FeatureExtractor

object EvidencePlanner {
    private const val MAX_EVIDENCE_COUNT = 30
    private const val CALIBRATION_TARGET_COUNT = 250
    private const val MAX_EVIDENCE_BYTES = 128 * 1024L
    private const val MAX_EVIDENCE_TOKENS = 16000L

    fun planEvidence(
        comments: List<CommentEntity>,
        isCalibrationRun: Boolean = false
    ): List<SynthesisEvidenceInput> {
        if (comments.isEmpty()) return emptyList()

        return if (!isCalibrationRun) {
            planNormalEvidence(comments)
        } else {
            planCalibrationEvidence(comments)
        }
    }

    private fun planNormalEvidence(comments: List<CommentEntity>): List<SynthesisEvidenceInput> {
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

    private fun planCalibrationEvidence(comments: List<CommentEntity>): List<SynthesisEvidenceInput> {
        val eligible = comments.filter { isWithinItemBounds(it) }.sortedBy { it.localId }
        if (eligible.isEmpty()) return emptyList()

        val selectedSet = LinkedHashSet<CommentEntity>()
        val perCategoryLimit = 50

        // 1. High likes
        val highLikes = eligible.sortedWith(
            compareByDescending<CommentEntity> { it.likeCount ?: 0 }.thenBy { it.localId }
        ).take(perCategoryLimit)
        selectedSet.addAll(highLikes)

        // 2. High replies
        val highReplies = eligible.sortedWith(
            compareByDescending<CommentEntity> { it.declaredReplyCount ?: 0 }.thenBy { it.localId }
        ).take(perCategoryLimit)
        selectedSet.addAll(highReplies)

        // 3. Questions
        val questions = eligible.filter { it.originalText.contains("?") }.sortedWith(
            compareByDescending<CommentEntity> { it.likeCount ?: 0 }.thenBy { it.localId }
        ).take(perCategoryLimit)
        selectedSet.addAll(questions)

        // 4. Rare-vocabulary / low-like comments using FeatureExtractor.extract(comment, comments).rareTokenScore
        val lowEngagement = eligible.filter { (it.likeCount ?: 0) <= 2 }
        val rareLowEngagement = lowEngagement.map { comment ->
            val features = FeatureExtractor.extract(comment, comments, comment.nearDuplicateClusterSize)
            comment to features.rareTokenScore
        }.sortedWith(
            compareByDescending<Pair<CommentEntity, Double>> { it.second }
                .thenBy { it.first.localId }
        ).take(perCategoryLimit).map { it.first }
        selectedSet.addAll(rareLowEngagement)

        // 5. Deterministic random mid-tier comments
        val unselected = eligible.filter { !selectedSet.contains(it) }
        val importIdSeed = comments.firstOrNull()?.importId?.hashCode()?.toLong() ?: 42L
        val randomMidTier = unselected.shuffled(kotlin.random.Random(importIdSeed))
        for (item in randomMidTier) {
            if (selectedSet.size >= CALIBRATION_TARGET_COUNT) break
            selectedSet.add(item)
        }

        // Fill remaining if needed up to CALIBRATION_TARGET_COUNT
        if (selectedSet.size < CALIBRATION_TARGET_COUNT) {
            for (item in eligible) {
                if (selectedSet.size >= CALIBRATION_TARGET_COUNT) break
                selectedSet.add(item)
            }
        }

        // Apply byte and token safety bounds
        val bounded = mutableListOf<CommentEntity>()
        var totalBytes = 0L
        var totalTokens = 0L

        for (item in selectedSet) {
            val textBytes = item.originalText.toByteArray(Charsets.UTF_8).size.toLong()
            val textTokens = (item.originalText.length / 4).coerceAtLeast(1).toLong()

            if (totalBytes + textBytes > MAX_EVIDENCE_BYTES || totalTokens + textTokens > MAX_EVIDENCE_TOKENS) {
                continue
            }

            bounded.add(item)
            totalBytes += textBytes
            totalTokens += textTokens
        }

        // Deterministically shuffle prompt order
        val shuffleSeed = importIdSeed * 31 + 17
        val shuffled = bounded.shuffled(kotlin.random.Random(shuffleSeed))

        return shuffled.map {
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
