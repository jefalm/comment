package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
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
    val lastErrorCode: String?,
    val videoId: String? = null,
    val videoTitle: String? = null,
    val channelTitle: String? = null,
    val publishedAtRaw: String? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val declaredCommentCount: Long? = null,
    val retryable: Boolean? = null,
    val unlabeledStatisticRaw: String? = null,
    val publishedLocalDateTimeRaw: String? = null,
    val topLevelCommentCount: Int? = null,
    val declaredReplyCount: Int? = null,
    val totalLikes: Int? = null,
    val averageCommentsPerDay: Double? = null,
    val averageLikesPerComment: Double? = null,
    @ColumnInfo(defaultValue = "1")
    val sourceConfigVersion: Int = 1
)
