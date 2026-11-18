package com.example.commentanalyzer.ui.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisReadinessTest {

    @Test
    fun nullConfigVersionIsNotReady() {
        assertFalse(
            computeConfigReadiness(
                configVersion = null,
                hasOpenRouter = true,
                hasTypeSafe = true,
                hasConsent = true
            )
        )
    }

    @Test
    fun v3ReadyWithOpenRouterAndConsentWithoutTypeSafe() {
        assertTrue(
            computeConfigReadiness(
                configVersion = 3L,
                hasOpenRouter = true,
                hasTypeSafe = false,
                hasConsent = true
            )
        )
    }

    @Test
    fun v3BlockedWithoutConsent() {
        assertFalse(
            computeConfigReadiness(
                configVersion = 3L,
                hasOpenRouter = true,
                hasTypeSafe = true,
                hasConsent = false
            )
        )
    }

    @Test
    fun v3BlockedWithoutOpenRouterKey() {
        assertFalse(
            computeConfigReadiness(
                configVersion = 3L,
                hasOpenRouter = false,
                hasTypeSafe = true,
                hasConsent = true
            )
        )
    }

    @Test
    fun v2BlockedWithoutTypeSafeKey() {
        assertFalse(
            computeConfigReadiness(
                configVersion = 2L,
                hasOpenRouter = true,
                hasTypeSafe = false,
                hasConsent = true
            )
        )
    }

    @Test
    fun v2ReadyWithTypeSafeAndOpenRouterAndConsent() {
        assertTrue(
            computeConfigReadiness(
                configVersion = 2L,
                hasOpenRouter = true,
                hasTypeSafe = true,
                hasConsent = true
            )
        )
    }

    @Test
    fun v2BlockedWithoutConsent() {
        assertFalse(
            computeConfigReadiness(
                configVersion = 2L,
                hasOpenRouter = true,
                hasTypeSafe = true,
                hasConsent = false
            )
        )
    }

    @Test
    fun v2BlockedWithoutOpenRouter() {
        assertFalse(
            computeConfigReadiness(
                configVersion = 2L,
                hasOpenRouter = false,
                hasTypeSafe = true,
                hasConsent = true
            )
        )
    }

    @Test
    fun filterAndOrderEvidenceCommentsFiltersImportAndPreservesCitationOrder() {
        val c1 = dummyComment("c1", "import-1")
        val c2 = dummyComment("c2", "import-1")
        val c3 = dummyComment("c3", "import-2") // Different import
        val c4 = dummyComment("c4", "import-1")

        val rawComments = listOf(c4, c1, c3, c2) // Random order from DB
        val citedCommentIds = listOf("c2", "c4", "c1", "c999") // Non-db order, includes missing ID

        val ordered = filterAndOrderEvidenceComments(
            rawComments = rawComments,
            importId = "import-1",
            citedCommentIds = citedCommentIds
        )

        // Only import-1 comments, ordered by citedCommentIds, missing c999 omitted
        org.junit.Assert.assertEquals(listOf("c2", "c4", "c1"), ordered.map { it.localId })
    }

    private fun dummyComment(id: String, importId: String) =
        com.example.commentanalyzer.data.local.entity.CommentEntity(
            localId = id,
            sourceType = com.example.commentanalyzer.domain.model.SourceType.YOUTUBE_API,
            sourceScopeKey = "scope:1",
            sourceCommentId = "src-$id",
            parentSourceCommentId = null,
            videoId = "v1",
            importId = importId,
            authorDisplayName = "Author",
            originalText = "text",
            normalizedText = "text",
            publishedAtEpochMillis = 1000L,
            publishedAtPrecision = null,
            fingerprintVersion = 1,
            fingerprintSha256 = "fp-$id",
            processingState = com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED,
            currentStage = com.example.commentanalyzer.data.local.entity.ProcessingStage.DONE,
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
            updatedAt = 1000L
        )
}
