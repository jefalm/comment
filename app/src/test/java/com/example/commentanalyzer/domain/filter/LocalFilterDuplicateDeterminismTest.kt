package com.example.commentanalyzer.domain.filter

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that exact-duplicate handling is import-wide and source-stable: the same source
 * population must produce the same duplicate groups, survivors and cluster sizes regardless of
 * the import-specific localIds or of where the 250-comment batch boundaries happen to fall.
 */
class LocalFilterDuplicateDeterminismTest {

    private val duplicatedText = "Exactly duplicated perspective on the same topic."

    private data class ObservedOutcome(
        val outcome: FilterOutcome,
        val ruleIds: List<String>,
        val clusterSize: Int
    )

    @Test
    fun duplicateGroupsAreIdenticalForSameSourcePopulationWithDifferentLocalIds() {
        val firstImport = sourcePopulation(importTag = "a")
        val secondImport = sourcePopulation(importTag = "b", reverseSourceOrder = true)

        val firstGroups = buildGroups(firstImport)
        val secondGroups = buildGroups(secondImport)

        assertEquals(firstGroups, secondGroups)
        assertEquals(3, firstGroups.getValue(clusterKey(duplicatedText)).clusterSize)
    }

    @Test
    fun duplicateOutcomesIgnoreLocalIdOrdering() {
        val firstImport = sourcePopulation(importTag = "a")
        val secondImport = sourcePopulation(importTag = "b", reverseSourceOrder = true)

        val firstOutcomes = outcomesBySourceCommentId(firstImport, buildGroups(firstImport))
        val secondOutcomes = outcomesBySourceCommentId(secondImport, buildGroups(secondImport))

        assertEquals(firstOutcomes, secondOutcomes)
    }

    @Test
    fun batchedEvaluationMatchesSingleShotEvaluation() {
        val population = sourcePopulation(importTag = "a")
        val groups = buildGroups(population)

        val singleShot = outcomesBySourceCommentId(population, groups)
        val batched = outcomesBySourceCommentId(population.take(5), groups) +
            outcomesBySourceCommentId(population.drop(5), groups)

        assertEquals(singleShot.toSortedMap(), batched.toSortedMap())
    }

    @Test
    fun duplicateSpanningBatchBoundaryIsGroupedAndScoredConsistently() {
        val survivor = comment(
            localId = "import-a:zzz",
            sourceCommentId = "s-900",
            text = duplicatedText,
            likeCount = 7,
            publishedAtEpochMillis = 5_000L
        )
        val shadowed = comment(
            localId = "import-a:aaa",
            sourceCommentId = "s-100",
            text = duplicatedText,
            likeCount = 7,
            publishedAtEpochMillis = 5_000L
        )
        val population = listOf(survivor, shadowed)
        val groups = buildGroups(population)

        // Each comment is evaluated in its own bounded batch, as happens when the pair straddles
        // a 250-comment chunk boundary.
        val survivorResult = LocalFilter.evaluateBatch(listOf(survivor), groups).getValue(survivor.localId)
        val shadowedResult = LocalFilter.evaluateBatch(listOf(shadowed), groups).getValue(shadowed.localId)

        assertEquals(FilterOutcome.PASS, survivorResult.filterResult.outcome)
        assertEquals(2, survivorResult.nearDuplicateClusterSize)

        assertEquals(FilterOutcome.REJECT, shadowedResult.filterResult.outcome)
        assertEquals(listOf("RULE_EXACT_DUPLICATE"), shadowedResult.filterResult.ruleIds)
        assertEquals(2, shadowedResult.nearDuplicateClusterSize)
    }

    @Test
    fun survivorTieBreakUsesSourceCommentIdNotLocalId() {
        val lowSourceKey = comment(
            localId = "import-a:zzz",
            sourceCommentId = "s-100",
            text = duplicatedText,
            likeCount = 3,
            publishedAtEpochMillis = 5_000L
        )
        val highSourceKey = comment(
            localId = "import-a:aaa",
            sourceCommentId = "s-900",
            text = duplicatedText,
            likeCount = 3,
            publishedAtEpochMillis = 5_000L
        )

        val groups = buildGroups(listOf(lowSourceKey, highSourceKey))

        assertEquals("s-900", groups.getValue(clusterKey(duplicatedText)).survivorSourceKey)
    }

    private fun sourcePopulation(importTag: String, reverseSourceOrder: Boolean = false): List<CommentEntity> {
        val duplicateIndices = setOf(7, 25, 39)
        return (1..40).map { index ->
            val sourceId = "s-${index.toString().padStart(3, '0')}"
            val localId = if (reverseSourceOrder) {
                "import-$importTag:${sourceId.reversed()}"
            } else {
                "import-$importTag:$sourceId"
            }
            comment(
                localId = localId,
                sourceCommentId = sourceId,
                text = if (index in duplicateIndices) duplicatedText else "Unique perspective number $index",
                likeCount = index % 5,
                publishedAtEpochMillis = 1_000L * index
            )
        }
    }

    private fun buildGroups(comments: List<CommentEntity>): Map<String, LocalFilter.DuplicateGroup> {
        val builder = LocalFilter.DuplicateIndexBuilder()
        for (c in comments) {
            builder.add(
                normalizedText = c.normalizedText,
                sourceKey = sourceStableKey(c.sourceCommentId, c.fingerprintSha256),
                likeCount = c.likeCount,
                publishedAtEpochMillis = c.publishedAtEpochMillis
            )
        }
        return builder.build()
    }

    private fun outcomesBySourceCommentId(
        comments: List<CommentEntity>,
        groups: Map<String, LocalFilter.DuplicateGroup>
    ): Map<String, ObservedOutcome> {
        val byLocalId = comments.associateBy { it.localId }
        return LocalFilter.evaluateBatch(comments, groups).entries.associate { (localId, result) ->
            val sourceId = requireNotNull(byLocalId.getValue(localId).sourceCommentId)
            sourceId to ObservedOutcome(
                outcome = result.filterResult.outcome,
                ruleIds = result.filterResult.ruleIds,
                clusterSize = result.nearDuplicateClusterSize
            )
        }
    }

    private fun clusterKey(text: String) = text.trim().lowercase()

    private fun comment(
        localId: String,
        sourceCommentId: String,
        text: String,
        likeCount: Int?,
        publishedAtEpochMillis: Long?
    ): CommentEntity = CommentEntity(
        localId = localId,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "scope:1",
        sourceCommentId = sourceCommentId,
        parentSourceCommentId = null,
        videoId = "video-1",
        importId = "import-test",
        authorDisplayName = "Author",
        originalText = text,
        normalizedText = text,
        publishedAtEpochMillis = publishedAtEpochMillis,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fp-$sourceCommentId",
        processingState = ProcessingState.IMPORTED,
        currentStage = ProcessingStage.INGESTION,
        category = null,
        sentiment = null,
        relevanceScore = null,
        processingConfigVersion = 3L,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        attemptCountLocal = 0,
        attemptCountJev = 0,
        attemptCountGlm = 0,
        lastErrorCode = null,
        lastErrorSummary = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        likeCount = likeCount,
        declaredReplyCount = 0
    )
}
