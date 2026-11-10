package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.commentanalyzer.domain.model.SourceType

@Entity(tableName = "imports")
data class ImportEntity(
    @PrimaryKey
    val importId: String,
    val sourceType: SourceType,
    val sourceLocator: String,
    val sourceScopeKey: String,
    val status: ImportStatus,
    val includeReplies: Boolean,
    val nextThreadPageToken: String?,
    val activeParentCommentId: String?,
    val nextReplyPageToken: String?,
    val itemsSeen: Long,
    val itemsInserted: Long,
    val itemsDeduplicated: Long,
    val itemsFailed: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val lastErrorCode: String?
)
