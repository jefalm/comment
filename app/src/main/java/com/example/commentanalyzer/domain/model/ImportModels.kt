package com.example.commentanalyzer.domain.model

data class ImportRequest(
    val importId: String,
    val sourceType: SourceType,
    val sourceLocator: String,
    val sourceScopeKey: String,
    val includeReplies: Boolean,
    val nextThreadPageToken: String? = null,
    val activeParentCommentId: String? = null,
    val nextReplyPageToken: String? = null
)

data class ImportProgress(
    val importId: String,
    val itemsSeen: Long,
    val itemsInserted: Long,
    val itemsDeduplicated: Long,
    val itemsFailed: Long,
    val completed: Boolean,
    val lastErrorCode: String?,
    val retryable: Boolean? = null,
    val retryAfterEpochMillis: Long? = null
)
