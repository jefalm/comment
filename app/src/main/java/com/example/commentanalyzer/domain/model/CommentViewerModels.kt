package com.example.commentanalyzer.domain.model

import java.time.LocalDateTime

data class VideoDetails(
    val title: String,
    val channel: String,
    val views: Long,
    val unlabeledStatisticRaw: String,
    val publishedAt: String,
    val publishedLocalDateTime: LocalDateTime,
    val totalCommentsCount: Int
)

data class CommentStatistics(
    val topLevelComments: Int,
    val totalLikes: Int,
    val avgCommentsPerDay: Double,
    val avgLikesPerComment: Double,
    val declaredReplyCount: Int
)

data class UiComment(
    val id: String,
    val author: String,
    val date: String,
    val text: String,
    val likes: Int,
    val repliesHint: String?,
    val declaredReplyCount: Int = 0,
    val publishedEpochMillis: Long? = null,
    val publishedLocalDateTime: LocalDateTime? = null,
    val replies: List<UiComment> = emptyList()
)

data class VideoCommentExportData(
    val videoDetails: VideoDetails,
    val statistics: CommentStatistics,
    val comments: List<UiComment>,
    val sourceType: SourceType = SourceType.TXT_EXPORT
)
