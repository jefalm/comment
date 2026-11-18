package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidencePlannerTest {

    @Test
    fun smallImportUnder80SelectsAllEligibleComments() {
        val comments = (1..50).map { i ->
            createComment(id = "c-$i", text = "This is comment $i with high quality thoughts.", likes = i)
        }
        val planned = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)
        assertEquals(50, planned.size)
    }

    @Test
    fun adaptiveTargetFor81To200Targets110() {
        val comments = (1..150).map { i ->
            createComment(
                id = "c-$i",
                text = "Comment $i informative content ${if (i % 5 == 0) "Why is this happening?" else "great point"}.",
                likes = i % 20,
                replies = i % 5
            )
        }
        val planned = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)
        assertEquals(110, planned.size)
    }

    @Test
    fun adaptiveTargetFor201To500Targets145() {
        val comments = (1..300).map { i ->
            createComment(
                id = "c-$i",
                text = "Comment $i substantive analysis ${if (i % 6 == 0) "Could you explain?" else "agree entirely"}.",
                likes = i % 15,
                replies = i % 4
            )
        }
        val planned = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)
        assertEquals(145, planned.size)
    }

    @Test
    fun adaptiveTargetForOver500Targets180() {
        val comments = (1..550).map { i ->
            createComment(
                id = "c-$i",
                text = "Comment $i community feedback ${if (i % 8 == 0) "What do you think?" else "very helpful"}.",
                likes = i % 30,
                replies = i % 6
            )
        }
        val planned = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)
        assertEquals(180, planned.size)
    }

    @Test
    fun hardTokenByteAndItemBoundsEnforced() {
        val longContent = "Detailed technical explanation repeating for testing bounds. ".repeat(30)
        val comments = (1..250).map { i ->
            createComment(id = "c-$i", text = "Comment $i: $longContent", likes = i)
        }
        val planned = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)

        val totalBytes = planned.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() }
        val totalTokens = planned.sumOf { (it.text.length / 4).coerceAtLeast(1).toLong() }

        assertTrue(totalBytes <= EvidencePlanner.MAX_EVIDENCE_BYTES)
        assertTrue(totalTokens <= EvidencePlanner.MAX_EVIDENCE_TOKENS)
        assertTrue(planned.size <= EvidencePlanner.MAX_EVIDENCE_ITEMS)
    }

    @Test
    fun deterministicOutputAcrossRuns() {
        val comments = (1..120).map { i ->
            val question = if (i % 4 == 0) " What is the question?" else ""
            val discourse = if (i % 7 == 0) " However, I disagree with that." else ""
            createComment(
                id = "c-$i",
                text = "Insight $i.$question$discourse",
                likes = i % 10,
                replies = i % 3
            )
        }

        val run1 = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)
        val run2 = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)

        assertEquals(run1.map { it.commentId }, run2.map { it.commentId })
    }

    @Test
    fun underpopulatedBucketSpilloverRedistributesToHighSignalWithoutFiller() {
        // 120 comments with zero questions
        val comments = (1..120).map { i ->
            createComment(
                id = "c-$i",
                text = "Statement $i without question marks.",
                likes = i
            )
        }
        val planned = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)
        // Should reach target (110) by redistributing unused question quota to high signal
        assertEquals(110, planned.size)
        // None of the comments contain ?
        assertTrue(planned.none { it.text.contains("?") })
    }

    @Test
    fun threadContextPreservedForSelectedReplies() {
        // Root has deliberately low independent score (-50.0) and would not be selected on its own
        val root = createComment(
            id = "root-1",
            text = "k",
            sourceCommentId = "src-root-1",
            parentSourceCommentId = null,
            likes = 0,
            replies = 1
        ).copy(signalScore = -50.0)

        // Reply has high score and debate discourse marker
        val reply1 = createComment(
            id = "reply-1",
            text = "However, I disagree with the premise and evidence shows contrary results.",
            sourceCommentId = "src-reply-1",
            parentSourceCommentId = "src-root-1",
            likes = 100,
            replies = 0
        ).copy(signalScore = 50.0)

        // 148 other comments with moderate scores (0.0 .. 20.0)
        val otherComments = (1..148).map { i ->
            createComment(id = "c-$i", text = "Other substantive comment $i", likes = i % 10)
                .copy(signalScore = (i % 20).toDouble())
        }

        // Total comments = 150 -> target is 110. Root ranked #150 would be excluded without thread context.
        val allComments = listOf(root, reply1) + otherComments
        val planned = EvidencePlanner.planEvidence(allComments, isCalibrationRun = false)

        val plannedIds = planned.map { it.commentId }
        assertTrue("High scoring debate reply must be selected", plannedIds.contains("reply-1"))
        assertTrue("Low-scoring root must be included causally for thread context", plannedIds.contains("root-1"))
    }

    @Test
    fun duplicateSuppressionPenalizesNearDuplicateClusters() {
        // High cluster size duplicate comments
        val dupes = (1..10).map { i ->
            createComment(
                id = "dupe-$i",
                text = "Identical copy pasted spam comment text",
                likes = 100,
                clusterSize = 5
            )
        }
        val uniqueComments = (1..100).map { i ->
            createComment(
                id = "unique-$i",
                text = "Unique varied thoughtful comment number $i",
                likes = 10,
                clusterSize = 1
            )
        }

        val all = dupes + uniqueComments
        val planned = EvidencePlanner.planEvidence(all, isCalibrationRun = false)

        val selectedDupeCount = planned.count { it.commentId.startsWith("dupe-") }
        // Duplicates should be strongly suppressed, finishing at approximately 100 useful comments
        assertEquals(0, selectedDupeCount)
        assertEquals(100, planned.size)
    }

    @Test
    fun calibrationModeRemainsUnchanged() {
        val comments = (1..60).map { i ->
            createComment(id = "c-$i", text = "Comment $i for calibration testing.", likes = i)
        }
        val calib = EvidencePlanner.planEvidence(comments, isCalibrationRun = true)
        assertEquals(60, calib.size)
    }

    private fun createComment(
        id: String,
        text: String,
        likes: Int = 0,
        replies: Int = 0,
        sourceCommentId: String? = "src-$id",
        parentSourceCommentId: String? = null,
        clusterSize: Int = 1
    ): CommentEntity = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "scope:1",
        sourceCommentId = sourceCommentId,
        parentSourceCommentId = parentSourceCommentId,
        videoId = "video-1",
        importId = "import-test",
        authorDisplayName = "Author $id",
        originalText = text,
        normalizedText = text.lowercase(),
        publishedAtEpochMillis = 1000L,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fp-$id",
        processingState = ProcessingState.COMPLETED,
        currentStage = ProcessingStage.DONE,
        category = "praise",
        sentiment = "positive",
        relevanceScore = 0.9,
        processingConfigVersion = 3L,
        leaseOwner = null,
        leaseExpiresAt = null,
        nextAttemptAt = null,
        attemptCountLocal = 0,
        attemptCountJev = 0,
        attemptCountGlm = 0,
        lastErrorCode = null,
        lastErrorSummary = null,
        createdAt = 1000L,
        updatedAt = 1000L,
        likeCount = likes,
        declaredReplyCount = replies,
        nearDuplicateClusterSize = clusterSize
    )
}
