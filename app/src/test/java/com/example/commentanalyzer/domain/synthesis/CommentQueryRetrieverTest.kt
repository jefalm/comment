package com.example.commentanalyzer.domain.synthesis

import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentQueryRetrieverTest {

    private fun createComment(
        id: String,
        importId: String = "import-1",
        configVersion: Long = 3L,
        text: String = "Test comment",
        signalScore: Double? = 0.5,
        publishedAt: Long = 1000L,
        sourceCommentId: String? = null,
        parentSourceCommentId: String? = null
    ): CommentEntity = CommentEntity(
        localId = id,
        sourceType = SourceType.YOUTUBE,
        sourceScopeKey = "video-1",
        sourceCommentId = sourceCommentId ?: id,
        parentSourceCommentId = parentSourceCommentId,
        videoId = "video-1",
        importId = importId,
        authorDisplayName = "User $id",
        originalText = text,
        normalizedText = text.lowercase(),
        publishedAtEpochMillis = publishedAt,
        publishedAtPrecision = null,
        fingerprintVersion = 1,
        fingerprintSha256 = "fp-$id",
        processingState = ProcessingState.READY_GLM,
        currentStage = ProcessingStage.GLM_ANALYSIS,
        category = "feedback",
        sentiment = "neutral",
        relevanceScore = 1.0,
        processingConfigVersion = configVersion,
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
        signalScore = signalScore
    )

    @Test
    fun importAndConfigScopeIsolation() {
        val comments = listOf(
            createComment("c-1", importId = "import-1", configVersion = 3L, text = "battery life issue"),
            createComment("c-2", importId = "import-2", configVersion = 3L, text = "battery life issue"),
            createComment("c-3", importId = "import-1", configVersion = 2L, text = "battery life issue")
        )

        val retrieved = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "battery life",
            candidateComments = comments
        )

        assertEquals(1, retrieved.size)
        assertEquals("c-1", retrieved.first().localId)
    }

    @Test
    fun deterministicOrder() {
        val comments = (1..30).map { i ->
            createComment("c-$i", text = "great discussion about performance and battery", signalScore = i * 0.1)
        }

        val run1 = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "performance",
            candidateComments = comments
        )
        val run2 = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "performance",
            candidateComments = comments
        )

        assertEquals(run1.map { it.localId }, run2.map { it.localId })
    }

    @Test
    fun lexicalRelevance() {
        val comments = listOf(
            createComment("c-noise", text = "completely unrelated comment about cooking dinner"),
            createComment("c-match", text = "the camera performance in low light is incredible")
        )

        val retrieved = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "camera low light",
            candidateComments = comments
        )

        assertEquals("c-match", retrieved.first().localId)
    }

    @Test
    fun signalBoostRanksHigherForEqualLexicalMatch() {
        val comments = listOf(
            createComment("c-low-signal", text = "microphone audio quality", signalScore = 0.1),
            createComment("c-high-signal", text = "microphone audio quality", signalScore = 0.9)
        )

        val retrieved = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "microphone audio",
            candidateComments = comments
        )

        assertEquals("c-high-signal", retrieved.first().localId)
    }

    @Test
    fun citedBoostPrioritizesExistingSynthesisEvidence() {
        val comments = listOf(
            createComment("c-regular", text = "screen brightness outdoors", signalScore = 0.5),
            createComment("c-cited", text = "screen brightness outdoors", signalScore = 0.5)
        )

        val retrieved = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "screen brightness",
            candidateComments = comments,
            activeSynthesisCitedIds = setOf("c-cited")
        )

        assertEquals("c-cited", retrieved.first().localId)
    }

    @Test
    fun max120CommentsCap() {
        val comments = (1..150).map { i ->
            createComment("c-$i", text = "display refresh rate topic $i", signalScore = 0.5)
        }

        val retrieved = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "display refresh",
            candidateComments = comments
        )

        assertEquals(CommentQueryRetriever.MAX_QA_COMMENTS, retrieved.size)
        assertTrue(retrieved.size <= 120)
    }

    @Test
    fun max32KTokensCap() {
        val longText = "a".repeat(4000) // ~1000 tokens each
        val comments = (1..50).map { i ->
            createComment("c-$i", text = "$longText $i", signalScore = 0.5)
        }

        val retrieved = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "test",
            candidateComments = comments
        )

        val totalTokens = retrieved.sumOf { CommentQueryRetriever.estimateTokens(it.originalText) }
        assertTrue(totalTokens <= CommentQueryRetriever.MAX_QA_TOKENS)
    }

    @Test
    fun threadRootContextPreservedForReplies() {
        val root = createComment("root-1", text = "original question about pricing", sourceCommentId = "sc-root-1")
        val reply = createComment(
            "reply-1",
            text = "yes the pricing is too expensive compared to last year",
            sourceCommentId = "sc-reply-1",
            parentSourceCommentId = "sc-root-1"
        )

        val retrieved = CommentQueryRetriever.retrieveRelevantComments(
            importId = "import-1",
            configVersion = 3L,
            question = "pricing expensive",
            candidateComments = listOf(reply, root)
        )

        val ids = retrieved.map { it.localId }
        assertTrue(ids.contains("reply-1"))
        assertTrue(ids.contains("root-1"))
    }
}
