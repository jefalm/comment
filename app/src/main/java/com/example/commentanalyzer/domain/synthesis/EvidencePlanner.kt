package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.domain.analysis.SynthesisEvidenceInput
import com.example.commentanalyzer.domain.ranking.ExtractedFeatures
import com.example.commentanalyzer.domain.ranking.FeatureExtractor
import com.example.commentanalyzer.domain.ranking.SignalRanker
import kotlin.math.roundToInt

object EvidencePlanner {
    const val MAX_EVIDENCE_ITEMS = 200
    const val MAX_EVIDENCE_TOKENS = 48_000L
    const val MAX_EVIDENCE_BYTES = 256 * 1024L

    private const val CALIBRATION_TARGET_COUNT = 250
    private const val CALIBRATION_MAX_BYTES = 128 * 1024L
    private const val CALIBRATION_MAX_TOKENS = 16_000L

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

    fun calculateTargetEvidenceCount(eligibleCount: Int): Int = when {
        eligibleCount <= 80 -> eligibleCount
        eligibleCount <= 200 -> 110.coerceAtMost(eligibleCount)
        eligibleCount <= 500 -> 145.coerceAtMost(eligibleCount)
        else -> 180.coerceAtMost(eligibleCount)
    }.coerceAtMost(MAX_EVIDENCE_ITEMS)

    private data class ScoredCandidate(
        val comment: CommentEntity,
        val features: ExtractedFeatures,
        val score: Double
    )

    private fun planNormalEvidence(comments: List<CommentEntity>): List<SynthesisEvidenceInput> {
        val eligible = comments.filter { isWithinItemBounds(it, MAX_EVIDENCE_BYTES, MAX_EVIDENCE_TOKENS) }
        if (eligible.isEmpty()) return emptyList()

        val scored = eligible.map { comment ->
            val features = FeatureExtractor.extract(comment, eligible, comment.nearDuplicateClusterSize)
            val score = comment.signalScore ?: SignalRanker.forVersion(
                comment.rankerVersion ?: SignalRanker.DEFAULT_VERSION
            ).score(features).score
            ScoredCandidate(comment, features, score)
        }

        val scoreComparator = compareByDescending<ScoredCandidate> { it.score }
            .thenBy { it.comment.localId }

        val targetCount = calculateTargetEvidenceCount(eligible.size)

        val selectedMap = LinkedHashMap<String, ScoredCandidate>()

        if (eligible.size <= 80) {
            val sortedEligible = scored.sortedWith(scoreComparator)
            for (c in sortedEligible) {
                if (selectedMap.size >= targetCount) break
                selectedMap[c.comment.localId] = c
            }
        } else {
            val targetHigh = (targetCount * 0.45).roundToInt()
            val targetDebate = (targetCount * 0.25).roundToInt()
            val targetQuestions = (targetCount * 0.15).roundToInt()
            val targetNovelty = (targetCount - targetHigh - targetDebate - targetQuestions).coerceAtLeast(0)

            // Stratum 3: Questions & Requests (~15%)
            val questionCandidates = scored.filter { it.features.questionMarkCount > 0.0 }
                .sortedWith(scoreComparator)
            val selectedQuestions = mutableListOf<ScoredCandidate>()
            for (candidate in questionCandidates) {
                if (selectedQuestions.size >= targetQuestions) break
                selectedQuestions.add(candidate)
            }
            val unusedQuestions = targetQuestions - selectedQuestions.size

            // Stratum 2: Debate / Thread Context (~25%)
            // High-reply roots paired with their highest-scoring replies
            val rootCandidates = scored.filter {
                (it.comment.declaredReplyCount ?: 0) > 0 && it.comment.parentSourceCommentId == null
            }.sortedWith(
                compareByDescending<ScoredCandidate> { it.comment.declaredReplyCount ?: 0 }
                    .thenByDescending { it.score }
                    .thenBy { it.comment.localId }
            )

            val repliesByParent = scored.filter { it.comment.parentSourceCommentId != null }
                .groupBy { it.comment.parentSourceCommentId }

            val selectedDebate = mutableListOf<ScoredCandidate>()
            val selectedDebateIds = mutableSetOf<String>()

            for (root in rootCandidates) {
                if (selectedDebate.size >= targetDebate) break
                val replies = repliesByParent[root.comment.sourceCommentId] ?: emptyList()
                val sortedReplies = replies.sortedWith(
                    compareByDescending<ScoredCandidate> { it.features.hasDiscourseMarker }
                        .thenByDescending { it.score }
                        .thenBy { it.comment.localId }
                )

                if (sortedReplies.isNotEmpty()) {
                    if (selectedDebateIds.add(root.comment.localId)) {
                        selectedDebate.add(root)
                    }
                    for (reply in sortedReplies) {
                        if (selectedDebate.size >= targetDebate) break
                        if (selectedDebateIds.add(reply.comment.localId)) {
                            selectedDebate.add(reply)
                        }
                    }
                }
            }

            // Also include standalone discourse marker comments if debate bucket has space
            if (selectedDebate.size < targetDebate) {
                val debateComments = scored.filter {
                    it.features.hasDiscourseMarker > 0.5 && !selectedDebateIds.contains(it.comment.localId)
                }.sortedWith(scoreComparator)

                for (cand in debateComments) {
                    if (selectedDebate.size >= targetDebate) break
                    if (selectedDebateIds.add(cand.comment.localId)) {
                        selectedDebate.add(cand)
                    }
                }
            }
            val unusedDebate = targetDebate - selectedDebate.size

            // Stratum 4: Novelty / Minority Perspectives (~15%)
            // Low-engagement comments with high rareTokenScore
            val noveltyCandidates = scored.filter {
                (it.comment.likeCount ?: 0) <= 2 && it.features.rareTokenScore >= 0.25
            }.sortedWith(
                compareByDescending<ScoredCandidate> { it.features.rareTokenScore }
                    .thenByDescending { it.score }
                    .thenBy { it.comment.localId }
            )

            val selectedNovelty = mutableListOf<ScoredCandidate>()
            val selectedNoveltyIds = mutableSetOf<String>()
            for (candidate in noveltyCandidates) {
                if (selectedNovelty.size >= targetNovelty) break
                if (selectedNoveltyIds.add(candidate.comment.localId)) {
                    selectedNovelty.add(candidate)
                }
            }
            val unusedNovelty = (targetNovelty - selectedNovelty.size).coerceAtLeast(0)

            // Deterministic spillover: High Signal -> Debate / Thread -> Novelty / Minority
            val highSignalQuota = targetHigh + unusedQuestions + unusedDebate + unusedNovelty

            // Add selected questions, debate, and novelty into selectedMap
            for (item in selectedQuestions) {
                selectedMap[item.comment.localId] = item
            }
            for (item in selectedDebate) {
                selectedMap[item.comment.localId] = item
            }
            for (item in selectedNovelty) {
                selectedMap[item.comment.localId] = item
            }

            // Fill High Signal candidates up to highSignalQuota (and bounded by targetCount)
            val allHighSignal = scored.sortedWith(scoreComparator)
            var highSignalCount = 0
            for (candidate in allHighSignal) {
                if (selectedMap.size >= targetCount || highSignalCount >= highSignalQuota) break
                // Duplicate suppression: skip if duplicate cluster size > 2
                if (candidate.comment.nearDuplicateClusterSize > 2) {
                    continue
                }
                if (!selectedMap.containsKey(candidate.comment.localId)) {
                    selectedMap[candidate.comment.localId] = candidate
                    highSignalCount++
                }
            }
        }

        // Maintain thread context: if a reply is selected and its root is in eligible, include root if space allows
        val rootLookup = eligible.filter { it.sourceCommentId != null && it.parentSourceCommentId == null }
            .associateBy { it.sourceCommentId }
        val toAddRoots = mutableListOf<CommentEntity>()
        for (selected in selectedMap.values) {
            val parentSourceId = selected.comment.parentSourceCommentId
            if (parentSourceId != null) {
                val root = rootLookup[parentSourceId]
                if (root != null && !selectedMap.containsKey(root.localId)) {
                    toAddRoots.add(root)
                }
            }
        }
        for (root in toAddRoots) {
            if (selectedMap.size >= MAX_EVIDENCE_ITEMS) break
            val features = FeatureExtractor.extract(root, eligible, root.nearDuplicateClusterSize)
            val score = root.signalScore ?: SignalRanker.forVersion(
                root.rankerVersion ?: SignalRanker.DEFAULT_VERSION
            ).score(features).score
            selectedMap[root.localId] = ScoredCandidate(root, features, score)
        }

        // Order deterministically: thread root before replies, then by score
        val selectedItems = selectedMap.values.toList()
        val sortedForPrompt = selectedItems.sortedWith(
            compareByDescending<ScoredCandidate> { it.score }
                .thenBy { it.comment.localId }
        )

        // Enforce hard bounds (48,000 tokens, 256 KiB, 200 items)
        val bounded = mutableListOf<CommentEntity>()
        var totalBytes = 0L
        var totalTokens = 0L

        for (item in sortedForPrompt) {
            if (bounded.size >= MAX_EVIDENCE_ITEMS) break
            val textBytes = item.comment.originalText.toByteArray(Charsets.UTF_8).size.toLong()
            val textTokens = (item.comment.originalText.length / 4).coerceAtLeast(1).toLong()

            if (totalBytes + textBytes > MAX_EVIDENCE_BYTES || totalTokens + textTokens > MAX_EVIDENCE_TOKENS) {
                continue
            }

            bounded.add(item.comment)
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
        val eligible = comments.filter { isWithinItemBounds(it, CALIBRATION_MAX_BYTES, CALIBRATION_MAX_TOKENS) }
            .sortedBy { it.localId }
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

            if (totalBytes + textBytes > CALIBRATION_MAX_BYTES || totalTokens + textTokens > CALIBRATION_MAX_TOKENS) {
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

    private fun isWithinItemBounds(
        comment: CommentEntity,
        maxBytes: Long = MAX_EVIDENCE_BYTES,
        maxTokens: Long = MAX_EVIDENCE_TOKENS
    ): Boolean {
        val textBytes = comment.originalText.toByteArray(Charsets.UTF_8).size.toLong()
        val textTokens = (comment.originalText.length / 4).coerceAtLeast(1).toLong()
        return textBytes <= maxBytes && textTokens <= maxTokens
    }
}
