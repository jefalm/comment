package com.example.commentanalyzer.data.local

import com.example.commentanalyzer.data.local.entity.*
import com.example.commentanalyzer.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityUnitTest {

    @Test
    fun testCommentEntityCreation() {
        val comment = CommentEntity(
            localId = "c-1",
            sourceType = SourceType.YOUTUBE,
            sourceScopeKey = "scope-1",
            sourceCommentId = "sc-1",
            parentSourceCommentId = null,
            videoId = "v-1",
            importId = "imp-1",
            authorDisplayName = "Alice",
            originalText = "Hello",
            normalizedText = "hello",
            publishedAtEpochMillis = 1000L,
            publishedAtPrecision = TimestampPrecision.MILLISECOND,
            fingerprintVersion = 1,
            fingerprintSha256 = "hash1",
            processingState = ProcessingState.IMPORTED,
            currentStage = ProcessingStage.INGESTION,
            category = null,
            sentiment = null,
            relevanceScore = null,
            processingConfigVersion = 1L,
            leaseOwner = null,
            leaseExpiresAt = null,
            nextAttemptAt = null,
            attemptCountLocal = 0,
            attemptCountJev = 0,
            attemptCountGlm = 0,
            lastErrorCode = null,
            lastErrorSummary = null,
            createdAt = 12345L,
            updatedAt = 12345L
        )
        assertEquals("c-1", comment.localId)
        assertEquals(SourceType.YOUTUBE, comment.sourceType)
        assertEquals(ProcessingState.IMPORTED, comment.processingState)
    }

    @Test
    fun testProcessingConfigEntityDefaults() {
        val config = ProcessingConfigEntity(
            configVersion = 1L,
            jevModelRequested = "typesafe/jev-1.14",
            jevRejectThreshold = 0.3,
            jevPassThreshold = 0.7,
            jevAutoRejectNonEnglish = false,
            glmModelRequested = "typesafe/glm-5.3",
            adapterJevEnabled = false,
            adapterGlmEnabled = false,
            adapterYoutubeEnabled = false,
            youtubeKeyCiphertext = null,
            openrouterKeyCiphertext = null,
            openrouterReferer = null,
            createdAt = 12345L,
            isActive = true
        )
        assertEquals(1L, config.configVersion)
        assertEquals(true, config.isActive)
        assertEquals(false, config.adapterJevEnabled)
    }

    @Test
    fun testRetryScheduleEntityDefaults() {
        val schedule = RetryScheduleEntity(
            nextWakeupEpochMillis = 5000L,
            updatedAt = 12345L
        )
        assertEquals("singleton", schedule.singletonKey)
        assertEquals(5000L, schedule.nextWakeupEpochMillis)
    }
}
