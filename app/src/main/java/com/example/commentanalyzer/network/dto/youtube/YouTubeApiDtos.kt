package com.example.commentanalyzer.network.dto.youtube

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
    val parentId: String?
)

data class CommentListResponse(
    val nextPageToken: String?,
    val items: List<CommentObject>?
)
