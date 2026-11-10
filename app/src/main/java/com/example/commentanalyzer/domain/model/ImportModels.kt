package com.example.commentanalyzer.domain.model

data class ImportRequest(
    val importId: String,
    val sourceType: SourceType,
    val sourceLocator: String,
    val sourceScopeKey: String,
    val includeReplies: Boolean
)

data class ImportProgress(
    val importId: String,
    val itemsSeen: Long,
    val itemsInserted: Long,
    val itemsDeduplicated: Long,
    val itemsFailed: Long,
    val completed: Boolean,
    val lastErrorCode: String?
)
