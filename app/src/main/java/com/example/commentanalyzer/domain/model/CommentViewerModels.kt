package com.example.commentanalyzer.domain.model

data class VideoDetails(
    val title: String,
    val channel: String,
    val views: Long,
    val likes: Long,
    val publishedAt: String,
    val totalCommentsCount: Int
)

data class CommentStatistics(
    val topLevelComments: Int,
    val totalLikes: Int,
    val avgCommentsPerDay: Double,
    val avgLikesPerComment: Double
)

data class UiComment(
    val id: String,
    val author: String,
    val date: String,
    val text: String,
    val likes: Int,
    val repliesHint: String?,
    val publishedEpochMillis: Long = 0L,
    val replies: List<UiComment> = emptyList()
)

data class VideoCommentExportData(
    val videoDetails: VideoDetails,
    val statistics: CommentStatistics,
    val comments: List<UiComment>
)
