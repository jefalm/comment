package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processing_batch_items",
    foreignKeys = [
        ForeignKey(
            entity = ProcessingBatchEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CommentEntity::class,
            parentColumns = ["localId"],
            childColumns = ["commentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["batchId", "commentId"], unique = true),
        Index(value = ["batchId", "requestItemId"], unique = true),
        Index(value = ["batchId", "ordinal"], unique = true),
        Index(value = ["commentId"])
    ]
)
data class ProcessingBatchItemEntity(
    @PrimaryKey
    val batchItemId: String,
    val batchId: String,
    val commentId: String,
    val ordinal: Int,
    val questionKey: String?,
    val requestItemId: String,
    val status: BatchItemStatus,
    val attemptCount: Int,
    val nextAttemptAt: Long?,
    val childBatchId: String?,
    val analysisResultId: String?,
    val lastErrorCode: String?,
    val createdAt: Long,
    val updatedAt: Long
)
