package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comment_syntheses",
    indices = [
        Index(value = ["importId", "configVersion"], unique = true)
    ]
)
data class CommentSynthesisEntity(
    @PrimaryKey
    val synthesisId: String,
    val importId: String,
    val configVersion: Long,
    val modelRequested: String,
    val modelResolved: String?,
    val provider: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
    val payloadJson: String,
    val createdAt: Long
)
