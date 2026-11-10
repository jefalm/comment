package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "analysis_results",
    foreignKeys = [
        ForeignKey(
            entity = CommentEntity::class,
            parentColumns = ["localId"],
            childColumns = ["commentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["commentId", "processingConfigVersion", "stage"]),
        Index(value = ["createdAt"])
    ]
)
data class AnalysisResultEntity(
    @PrimaryKey
    val resultId: String,
    val commentId: String,
    val stage: ResultStage,
    val processingConfigVersion: Long,
    val modelRequested: String,
    val modelResolved: String?,
    val provider: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
    val passed: Boolean?,
    val probability: Double?,
    val category: String?,
    val sentiment: String?,
    val relevanceScore: Double?,
    val summary: String?,
    val rawPayloadJson: String?,
    val createdAt: Long
)
