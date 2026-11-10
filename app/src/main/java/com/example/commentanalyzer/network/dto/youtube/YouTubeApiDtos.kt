package com.example.commentanalyzer.network.dto.youtube

data class VideoListResponse(
    val items: List<VideoItem>?
)

data class VideoItem(
    val id: String,
    val snippet: VideoSnippet?,
    val statistics: VideoStatistics?
)

data class VideoSnippet(
    val title: String?,
    val channelTitle: String?,
    val publishedAt: String?
)

data class VideoStatistics(
    val viewCount: String?,
    val likeCount: String?,
    val commentCount: String?
)

data class CommentThreadListResponse(
    val nextPageToken: String?,
    val items: List<CommentThreadItem>?
)

data class CommentThreadItem(
    val id: String,
    val snippet: ThreadSnippet?,
    val replies: ThreadReplies?
)

data class ThreadSnippet(
    val videoId: String?,
    val topLevelComment: CommentObject?,
    val totalReplyCount: Int?
)

data class ThreadReplies(
    val comments: List<CommentObject>?
)

data class CommentObject(
    val id: String,
    val snippet: CommentSnippet?
)

data class CommentSnippet(
    val videoId: String?,
    val textDisplay: String?,
    val authorDisplayName: String?,
    val publishedAt: String?,
    val updatedAt: String?,
    val parentId: String?,
    val likeCount: Int?
)

data class CommentListResponse(
    val nextPageToken: String?,
    val items: List<CommentObject>?
)

data class YouTubeApiErrorResponse(
    val error: YouTubeApiError?
)

data class YouTubeApiError(
    val code: Int?,
    val errors: List<YouTubeApiErrorDetail>?
)

data class YouTubeApiErrorDetail(
    val reason: String?
)
