package com.example.commentanalyzer.domain.ranking

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureExtractorTest {

    @Test
    fun prepareCorpusStatsDeduplicatesRepeatedTokensWithinSameComment() {
        val comment1 = createComment(id = "c-1", text = "test test test test")
        val comment2 = createComment(id = "c-2", text = "other comment")
        val stats = FeatureExtractor.prepareCorpusStats(listOf(comment1, comment2))
        
        assertEquals(1, stats.docFreqs["test"])
    }

    @Test
    fun prepareCorpusStatsUsesWholeWordTokensWithoutSubstringCollisions() {
        val comment1 = createComment(id = "c-1", text = "scar")
        val comment2 = createComment(id = "c-2", text = "car")
        val stats = FeatureExtractor.prepareCorpusStats(listOf(comment1, comment2))

        assertEquals(1, stats.docFreqs["car"])
        assertEquals(1, stats.docFreqs["scar"])
    }

    @Test
    fun preparedReplyCountsPreserveExpectedThreadSize() {
        val root = createComment(id = "c-root", sourceCommentId = "src-root", parentSourceCommentId = null)
        val reply1 = createComment(id = "c-r1", sourceCommentId = "src-r1", parentSourceCommentId = "src-root")
        val reply2 = createComment(id = "c-r2", sourceCommentId = "src-r2", parentSourceCommentId = "src-root")

        val stats = FeatureExtractor.prepareCorpusStats(listOf(root, reply1, reply2))
        val features = FeatureExtractor.extract(root, stats)

        assertEquals(3.0, features.threadSize, 1e-6)
    }

    @Test
    fun extractionWithSamePreparedStatsIsDeterministic() {
        val comments = (1..5).map { i -> createComment(id = "c-$i", text = "comment $i text") }
        val stats = FeatureExtractor.prepareCorpusStats(comments)

        val run1 = FeatureExtractor.extract(comments[0], stats)
        val run2 = FeatureExtractor.extract(comments[0], stats)

        assertEquals(run1, run2)
    }

    @Test
    fun corpusStatsAccumulationIsOrderIndependentAndMatchesBatchPreparation() {
        val comments = (1..300).map { i ->
            createComment(
                id = "c-$i",
                text = "comment $i rare${i % 17} shared",
                parentSourceCommentId = if (i % 4 == 0) "src-c-1" else null
            )
        }

        val singleShot = FeatureExtractor.prepareCorpusStats(comments)

        val shuffledAccumulator = CorpusStatsAccumulator()
        comments.reversed().forEach { shuffledAccumulator.add(it.originalText, it.parentSourceCommentId) }
        val accumulated = shuffledAccumulator.build()

        assertEquals(singleShot, accumulated)
        assertEquals(300.0, accumulated.nImport, 1e-9)
    }

    @Test
    fun corpusStatsAccumulatedInBoundedPagesMatchWholeImportStats() {
        val comments = (1..300).map { i ->
            createComment(id = "c-$i", text = "comment $i rare${i % 17} shared")
        }

        val wholeImport = FeatureExtractor.prepareCorpusStats(comments)

        val pagedAccumulator = CorpusStatsAccumulator()
        comments.chunked(250).forEach { page ->
            page.forEach { pagedAccumulator.add(it.originalText, it.parentSourceCommentId) }
        }
        val paged = pagedAccumulator.build()

        assertEquals(wholeImport, paged)
        assertEquals(
            FeatureExtractor.extract(comments.first(), wholeImport),
            FeatureExtractor.extract(comments.first(), paged)
        )
    }

    private fun createComment(
        id: String,
        text: String = "sample text",
        sourceCommentId: String? = "src-$id",
        parentSourceCommentId: String? = null
    ): CommentEntity = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE_API,
        sourceScopeKey = "scope:1",
        sourceCommentId = sourceCommentId,
        parentSourceCommentId = parentSourceCommentId,
        videoId = "video-1",
        importId = "import-test",
        authorDisplayName = "Author",
        originalText = text,
        normalizedText = text.lowercase(),
        publishedAtEpochMillis = 1000L,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fp-$id",
        processingState = ProcessingState.COMPLETED,
        currentStage = ProcessingStage.DONE,
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
        createdAt = 1000L,
        updatedAt = 1000L,
        likeCount = 0,
        declaredReplyCount = 0
    )
}
