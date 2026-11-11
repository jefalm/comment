package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processing_batches",
    indices = [
        Index(value = ["status", "nextAttemptAt"]),
        Index(value = ["parentBatchId"]),
        Index(value = ["importId", "stage", "status", "nextAttemptAt"])
    ]
)
data class ProcessingBatchEntity(
    @PrimaryKey
    val batchId: String,
    val stage: ProcessingStage,
    val status: BatchStatus,
    val parentBatchId: String?,
    val processingConfigVersion: Long,
    val modelRequested: String,
    val modelResolved: String?,
    val requestFingerprintSha256: String,
    val requestItemCount: Int,
    val requestUtf8Bytes: Long,
    val estimatedInputTokens: Long,
    val maxOutputTokens: Int?,
    val attemptCount: Int,
    val leaseOwner: String?,
    val leaseExpiresAt: Long?,
    val nextAttemptAt: Long?,
    val remoteRequestId: String?,
    val provider: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastErrorCode: String?,
    val lastErrorSummary: String?,
    val importId: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val failedAt: Long? = null,
    val responseSchemaVersion: Int? = null
)
