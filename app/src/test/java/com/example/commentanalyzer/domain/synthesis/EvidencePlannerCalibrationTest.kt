package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidencePlannerCalibrationTest {

    @Test
    fun normalNonCalibrationBehaviorUnchangedAndCappedAt30() {
        val comments = (1..60).map { i ->
            comment("c-$i", "Regular comment number $i with enough text.", likes = i)
        }
        val normalEvidence = EvidencePlanner.planEvidence(comments, isCalibrationRun = false)
        assertTrue(normalEvidence.size <= 30)
        assertEquals(30, normalEvidence.size)
    }

    @Test
    fun calibrationModeExceedsNormal30CapWhenSufficientCandidatesExist() {
        val comments = (1..100).map { i ->
            comment("c-$i", "Calibration comment text $i here.", likes = i)
        }
        val calibrationEvidence = EvidencePlanner.planEvidence(comments, isCalibrationRun = true)
        assertTrue("Calibration evidence should exceed 30 when 100 comments exist", calibrationEvidence.size > 30)
        assertEquals(100, calibrationEvidence.size)
    }

    @Test
    fun calibrationModePreservesHardTokenAndByteBounds() {
        val longText = "This is a repeating block of text with valuable domain discussion content. ".repeat(20)
        val comments = (1..300).map { i ->
            comment("c-$i", "Comment $i: $longText", likes = i)
        }
        val calibrationEvidence = EvidencePlanner.planEvidence(comments, isCalibrationRun = true)

        val totalBytes = calibrationEvidence.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() }
        val totalTokens = calibrationEvidence.sumOf { (it.text.length / 4).coerceAtLeast(1).toLong() }

        assertTrue(totalBytes <= 128 * 1024L)
        assertTrue(totalTokens <= 16000L)
        assertTrue(calibrationEvidence.size in 1..250)
    }

    @Test
    fun calibrationModeIsDeterministicAcrossRuns() {
        val comments = (1..80).map { i ->
            val hasQuestion = if (i % 5 == 0) " What do you think?" else ""
            val rareWord = if (i % 7 == 0) " idiosyncratic vocabulary terminology" else ""
            comment("c-$i", "Comment $i with content.$hasQuestion$rareWord", likes = i % 10, replies = i % 4)
        }

        val run1 = EvidencePlanner.planEvidence(comments, isCalibrationRun = true)
        val run2 = EvidencePlanner.planEvidence(comments, isCalibrationRun = true)

        assertEquals(run1.map { it.commentId }, run2.map { it.commentId })
    }

    @Test
    fun calibrationModeShufflesFinalPromptOrderDifferentlyFromSortedOrder() {
        val comments = (1..50).map { i ->
            comment(String.format("c-%03d", i), "Comment $i informative content.", likes = i)
        }
        val result = EvidencePlanner.planEvidence(comments, isCalibrationRun = true)
        val ids = result.map { it.commentId }
        val sortedIds = ids.sorted()

        // Shuffled order should not be identical to sorted order
        assertNotEquals(sortedIds, ids)
    }

    private fun comment(
        id: String,
        text: String,
        likes: Int = 0,
        replies: Int = 0
    ): CommentEntity = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "scope:1",
        sourceCommentId = "source-$id",
        parentSourceCommentId = null,
        videoId = "video-1",
        importId = "import-calib",
        authorDisplayName = "Author",
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
        declaredReplyCount = replies
    )
}
