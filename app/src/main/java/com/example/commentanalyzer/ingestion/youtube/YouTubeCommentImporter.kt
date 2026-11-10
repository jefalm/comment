package com.example.commentanalyzer.ingestion.youtube

import com.example.commentanalyzer.domain.model.ImportProgress
import com.example.commentanalyzer.domain.model.ImportRequest
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.ingestion.CommentImporter
import com.example.commentanalyzer.network.dto.youtube.CommentObject
import com.example.commentanalyzer.network.dto.youtube.YouTubeApiErrorResponse
import com.example.commentanalyzer.network.dto.youtube.VideoItem
import com.example.commentanalyzer.network.youtube.YouTubeApiService
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException

fun interface YouTubeApiKeyProvider {
    fun getApiKey(): String?
}

data class YouTubeVideoPreview(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val publishedAt: String?,
    val viewCount: Long?,
    val likeCount: Long?,
    val commentCount: Long?
)

data class YouTubeImportedComment(
    val sourceCommentId: String,
    val parentSourceCommentId: String?,
    val videoId: String,
    val isTopLevel: Boolean,
    val authorDisplayName: String?,
    val text: String,
    val likeCount: Int?,
    val publishedAt: String?,
    val updatedAt: String?,
    val declaredReplyCount: Int?
)

data class PersistedPageCounts(
    val inserted: Long,
    val deduplicated: Long
)

interface YouTubeImportPageSink {
    suspend fun prepareImport(request: ImportRequest, video: YouTubeVideoPreview)

    suspend fun beginImport(request: ImportRequest, video: YouTubeVideoPreview)

    suspend fun persistThreadPage(
        importId: String,
        video: YouTubeVideoPreview,
        consumedPageToken: String?,
        nextPageToken: String?,
        comments: List<YouTubeImportedComment>
    ): PersistedPageCounts

    suspend fun persistReplyPage(
        importId: String,
        parentCommentId: String,
        consumedPageToken: String?,
        nextPageToken: String?,
        replies: List<YouTubeImportedComment>,
        threadComplete: Boolean
    ): PersistedPageCounts

    suspend fun markImportCompleted(importId: String)

    suspend fun markImportFailed(importId: String, code: String, retryable: Boolean)

    suspend fun markImportCancelled(importId: String)
}

class YouTubeImportException(
    val code: String,
    val retryable: Boolean
) : Exception(code)

class YouTubeCommentImporter(
    private val service: YouTubeApiService,
    private val apiKeyProvider: YouTubeApiKeyProvider,
    private val pageSink: YouTubeImportPageSink,
    private val gson: Gson = Gson()
) : CommentImporter {
    suspend fun preview(sourceLocator: String): YouTubeVideoPreview {
        val videoId = YouTubeVideoIdParser.parse(sourceLocator)
            ?: throw YouTubeImportException("invalid_video_identifier", false)
        val key = apiKeyProvider.getApiKey()?.takeIf(String::isNotBlank)
            ?: throw YouTubeImportException("youtube_api_key_missing", false)
        val response = try {
            service.videos(videoId = videoId, apiKey = key)
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: IOException) {
            throw YouTubeImportException("network_unavailable", true)
        }
        if (!response.isSuccessful) throw classifyFailure(response.code(), response.errorBody()?.string())
        val item = response.body()?.items?.singleOrNull()
            ?: throw YouTubeImportException("video_unavailable", false)
        return item.toPreview(videoId)
    }

    override suspend fun import(request: ImportRequest): ImportProgress {
        if (request.sourceType != SourceType.YOUTUBE_API && request.sourceType != SourceType.YOUTUBE) {
            return failed(request.importId, "unsupported_source", false)
        }
        var itemsSeen = 0L
        var itemsInserted = 0L
        var itemsDeduplicated = 0L
        var itemsFailed = 0L
        return try {
            val video = preview(request.sourceLocator)
            val apiKey = apiKeyProvider.getApiKey()?.takeIf(String::isNotBlank)
                ?: throw YouTubeImportException("youtube_api_key_missing", false)
            pageSink.beginImport(request, video)
            var nextPageToken = request.nextThreadPageToken
            do {
                val consumedPageToken = nextPageToken
                val response = try {
                    service.commentThreads(videoId = video.videoId, pageToken = consumedPageToken, apiKey = apiKey)
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (_: IOException) {
                    throw YouTubeImportException("network_unavailable", true)
                }
                if (!response.isSuccessful) throw classifyFailure(response.code(), response.errorBody()?.string())
                val page = response.body() ?: throw YouTubeImportException("malformed_response", false)
                val threads = page.items ?: throw YouTubeImportException("malformed_response", false)
                val records = mutableListOf<YouTubeImportedComment>()
                for (thread in threads) {
                    val snippet = thread.snippet ?: throw YouTubeImportException("malformed_response", false)
                    val topLevel = snippet.topLevelComment ?: throw YouTubeImportException("malformed_response", false)
                    val topSnippet = topLevel.snippet ?: throw YouTubeImportException("malformed_response", false)
                    if (snippet.videoId != null && snippet.videoId != video.videoId) {
                        throw YouTubeImportException("malformed_response", false)
                    }
                    val totalReplyCount = snippet.totalReplyCount
                    if (request.includeReplies && totalReplyCount == null) {
                        throw YouTubeImportException("malformed_response", false)
                    }
                    if (topSnippet.parentId != null) throw YouTubeImportException("malformed_response", false)
                    records += topLevel.toImportedComment(video.videoId, totalReplyCount, null)
                    val embeddedReplies = thread.replies?.comments.orEmpty()
                    val uniqueEmbedded = embeddedReplies.distinctBy(CommentObject::id)
                    if (request.includeReplies) {
                        records += uniqueEmbedded.map { it.toImportedComment(video.videoId, null, topLevel.id) }
                    }
                    itemsSeen += 1 + uniqueEmbedded.size

                    if (request.includeReplies && totalReplyCount != null && uniqueEmbedded.size < totalReplyCount) {
                        val replyIds = uniqueEmbedded.mapTo(mutableSetOf(), CommentObject::id)
                        var replyPageToken = if (request.activeParentCommentId == topLevel.id) request.nextReplyPageToken else null
                        do {
                            val consumedReplyToken = replyPageToken
                            val replyResponse = try {
                                service.comments(parentId = topLevel.id, pageToken = consumedReplyToken, apiKey = apiKey)
                            } catch (_: CancellationException) {
                                throw CancellationException()
                            } catch (_: IOException) {
                                throw YouTubeImportException("network_unavailable", true)
                            }
                            if (!replyResponse.isSuccessful) throw classifyFailure(replyResponse.code(), replyResponse.errorBody()?.string())
                            val replyPage = replyResponse.body() ?: throw YouTubeImportException("malformed_response", false)
                            val replyItems = replyPage.items ?: throw YouTubeImportException("malformed_response", false)
                            val uniquePage = replyItems.distinctBy(CommentObject::id)
                            val newReplies = uniquePage.filter { replyIds.add(it.id) }
                            itemsSeen += uniquePage.size
                            if (newReplies.any { it.snippet?.videoId != null && it.snippet.videoId != video.videoId }) {
                                throw YouTubeImportException("malformed_response", false)
                            }
                            replyPageToken = replyPage.nextPageToken
                            val complete = replyPageToken == null
                            val counts = pageSink.persistReplyPage(
                                importId = request.importId,
                                parentCommentId = topLevel.id,
                                consumedPageToken = consumedReplyToken,
                                nextPageToken = replyPageToken,
                                replies = newReplies.map { it.toImportedComment(video.videoId, null, topLevel.id) },
                                threadComplete = complete && replyIds.size >= totalReplyCount
                            )
                            itemsInserted += counts.inserted
                            itemsDeduplicated += counts.deduplicated
                            if (complete && replyIds.size < totalReplyCount) {
                                throw YouTubeImportException("incomplete_reply_set", true)
                            }
                        } while (replyPageToken != null)
                    }
                }
                nextPageToken = page.nextPageToken
                val counts = pageSink.persistThreadPage(
                    importId = request.importId,
                    video = video,
                    consumedPageToken = consumedPageToken,
                    nextPageToken = nextPageToken,
                    comments = records
                )
                itemsInserted += counts.inserted
                itemsDeduplicated += counts.deduplicated
            } while (nextPageToken != null)

            pageSink.markImportCompleted(request.importId)
            ImportProgress(request.importId, itemsSeen, itemsInserted, itemsDeduplicated, itemsFailed, true, null, false)
        } catch (cancelled: CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { pageSink.markImportCancelled(request.importId) }
            }
            throw cancelled
        } catch (failure: YouTubeImportException) {
            failed(request.importId, failure.code, failure.retryable, itemsSeen, itemsInserted, itemsDeduplicated, itemsFailed)
        } catch (_: IOException) {
            failed(request.importId, "network_unavailable", true, itemsSeen, itemsInserted, itemsDeduplicated, itemsFailed)
        } catch (_: Exception) {
            failed(request.importId, "malformed_response", false, itemsSeen, itemsInserted, itemsDeduplicated, itemsFailed)
        }
    }

    private suspend fun failed(
        importId: String,
        code: String,
        retryable: Boolean,
        itemsSeen: Long = 0,
        itemsInserted: Long = 0,
        itemsDeduplicated: Long = 0,
        itemsFailed: Long = 1
    ): ImportProgress {
        runCatching { pageSink.markImportFailed(importId, code, retryable) }
        return ImportProgress(
            importId = importId,
            itemsSeen = itemsSeen,
            itemsInserted = itemsInserted,
            itemsDeduplicated = itemsDeduplicated,
            itemsFailed = itemsFailed,
            completed = false,
            lastErrorCode = code,
            retryable = retryable
        )
    }

    private fun classifyFailure(statusCode: Int, body: String?): YouTubeImportException {
        val error = try {
            gson.fromJson(body.orEmpty().take(64 * 1024), YouTubeApiErrorResponse::class.java)?.error
        } catch (_: Exception) {
            null
        }
        val reasons = error?.errors.orEmpty().mapNotNull { it.reason }.toSet()
        val reason = when {
            "keyInvalid" in reasons || "keyExpired" in reasons -> "invalid_api_key"
            "ipRefererBlocked" in reasons || "keyRestricted" in reasons -> "restricted_api_key"
            "accessNotConfigured" in reasons -> "youtube_api_not_enabled"
            "quotaExceeded" in reasons || "dailyLimitExceeded" in reasons -> "quota_exhausted"
            "commentsDisabled" in reasons || "commentThreadDisabled" in reasons -> "comments_disabled"
            "notFound" in reasons -> "video_unavailable"
            "rateLimitExceeded" in reasons || "userRateLimitExceeded" in reasons -> "rate_limited"
            statusCode == 404 -> "video_unavailable"
            statusCode in 500..599 -> "youtube_server_error"
            statusCode == 429 -> "rate_limited"
            statusCode == 400 -> "invalid_api_request"
            statusCode == 403 -> "youtube_access_denied"
            else -> "youtube_request_failed"
        }
        val retryable = reason == "quota_exhausted" || reason == "rate_limited" || reason == "youtube_server_error"
        return YouTubeImportException(reason, retryable)
    }

    private fun VideoItem.toPreview(requestedVideoId: String): YouTubeVideoPreview {
        val videoSnippet = snippet ?: throw YouTubeImportException("malformed_response", false)
        val title = videoSnippet.title?.takeIf(String::isNotBlank)
            ?: throw YouTubeImportException("malformed_response", false)
        val channelTitle = videoSnippet.channelTitle?.takeIf(String::isNotBlank)
            ?: throw YouTubeImportException("malformed_response", false)
        if (id != requestedVideoId) throw YouTubeImportException("malformed_response", false)
        val stats = statistics
        return YouTubeVideoPreview(
            videoId = id,
            title = title,
            channelTitle = channelTitle,
            publishedAt = videoSnippet.publishedAt,
            viewCount = stats?.viewCount?.toLongOrNull(),
            likeCount = stats?.likeCount?.toLongOrNull(),
            commentCount = stats?.commentCount?.toLongOrNull()
        )
    }

    private fun CommentObject.toImportedComment(
        videoId: String,
        replyCount: Int?,
        expectedParentId: String?
    ): YouTubeImportedComment {
        val commentSnippet = snippet ?: throw YouTubeImportException("malformed_response", false)
        val text = commentSnippet.textDisplay ?: throw YouTubeImportException("malformed_response", false)
        if (id.isBlank() || (expectedParentId != null && commentSnippet.parentId != null && commentSnippet.parentId != expectedParentId)) {
            throw YouTubeImportException("malformed_response", false)
        }
        return YouTubeImportedComment(
            sourceCommentId = id,
            parentSourceCommentId = expectedParentId ?: commentSnippet.parentId,
            videoId = commentSnippet.videoId ?: videoId,
            isTopLevel = expectedParentId == null,
            authorDisplayName = commentSnippet.authorDisplayName,
            text = text,
            likeCount = commentSnippet.likeCount,
            publishedAt = commentSnippet.publishedAt,
            updatedAt = commentSnippet.updatedAt,
            declaredReplyCount = replyCount
        )
    }
}
