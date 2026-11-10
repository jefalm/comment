package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.commentanalyzer.domain.model.SourceType

@Entity(
    tableName = "comments",
    indices = [
        Index(value = ["processingState", "nextAttemptAt"]),
        Index(value = ["videoId", "processingState"]),
        Index(value = ["importId"]),
        Index(value = ["leaseExpiresAt"]),
        Index(value = ["publishedAtEpochMillis"]),
        Index(value = ["category"])
    ]
)
data class CommentEntity(
    @PrimaryKey
    val localId: String,
    val sourceType: SourceType,
    val sourceScopeKey: String,
    val sourceCommentId: String?,
    val parentSourceCommentId: String?,
    val videoId: String?,
    val importId: String,
    val authorDisplayName: String?,
    val originalText: String,
    val normalizedText: String,
    val publishedAtEpochMillis: Long?,
    val publishedAtPrecision: TimestampPrecision?,
    val fingerprintVersion: Int,
    val fingerprintSha256: String,
    val processingState: ProcessingState,
    val currentStage: ProcessingStage,
    val category: String?,
    val sentiment: String?,
    val relevanceScore: Double?,
    val processingConfigVersion: Long,
    val leaseOwner: String?,
    val leaseExpiresAt: Long?,
    val nextAttemptAt: Long?,
    val attemptCountLocal: Int,
    val attemptCountJev: Int,
    val attemptCountGlm: Int,
    val lastErrorCode: String?,
    val lastErrorSummary: String?,
    val createdAt: Long,
    val updatedAt: Long
)
