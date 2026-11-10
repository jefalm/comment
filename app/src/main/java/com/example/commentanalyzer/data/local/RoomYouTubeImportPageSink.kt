package com.example.commentanalyzer.data.local

import androidx.room.withTransaction
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.TimestampPrecision
import com.example.commentanalyzer.domain.fingerprint.FingerprintV1
import com.example.commentanalyzer.domain.model.ImportRequest
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.normalize.TextNormalizer
import com.example.commentanalyzer.ingestion.youtube.PersistedPageCounts
import com.example.commentanalyzer.ingestion.youtube.YouTubeCommentImporter
import com.example.commentanalyzer.ingestion.youtube.YouTubeImportedComment
import com.example.commentanalyzer.ingestion.youtube.YouTubeImportPageSink
import com.example.commentanalyzer.ingestion.youtube.YouTubeVideoPreview
import java.security.MessageDigest
import java.time.Instant

class RoomYouTubeImportPageSink(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : YouTubeImportPageSink {
    override suspend fun prepareImport(request: ImportRequest, video: YouTubeVideoPreview) {
        database.withTransaction {
            val existing = database.importDao().getImportById(request.importId)
            if (existing == null) {
                database.importDao().insertImport(
                    ImportEntity(
                        importId = request.importId,
                        sourceType = SourceType.YOUTUBE_API,
                        sourceLocator = video.videoId,
                        sourceScopeKey = request.sourceScopeKey,
                        status = ImportStatus.CREATED,
                        includeReplies = request.includeReplies,
                        nextThreadPageToken = request.nextThreadPageToken,
                        activeParentCommentId = request.activeParentCommentId,
                        nextReplyPageToken = request.nextReplyPageToken,
                        itemsSeen = 0,
                        itemsInserted = 0,
                        itemsDeduplicated = 0,
                        itemsFailed = 0,
                        startedAt = clock(),
                        completedAt = null,
                        lastErrorCode = null,
                        videoId = video.videoId,
                        videoTitle = video.title,
                        channelTitle = video.channelTitle,
                        publishedAtRaw = video.publishedAt,
                        viewCount = video.viewCount,
                        likeCount = video.likeCount,
                        declaredCommentCount = video.commentCount
                    )
                )
            } else {
                require(existing.sourceType == SourceType.YOUTUBE_API)
                require(existing.sourceLocator == video.videoId)
                database.importDao().updateImport(
                    existing.copy(
                        includeReplies = request.includeReplies,
                        videoTitle = video.title,
                        channelTitle = video.channelTitle,
                        publishedAtRaw = video.publishedAt,
                        viewCount = video.viewCount,
                        likeCount = video.likeCount,
                        declaredCommentCount = video.commentCount
                    )
                )
            }
        }
    }

    override suspend fun beginImport(request: ImportRequest, video: YouTubeVideoPreview) {
        prepareImport(request, video)
        database.withTransaction {
            val import = requireImport(request.importId)
            database.importDao().updateImport(import.copy(status = ImportStatus.RUNNING, lastErrorCode = null, retryable = null))
        }
    }

    override suspend fun persistThreadPage(
        importId: String,
        video: YouTubeVideoPreview,
        consumedPageToken: String?,
        nextPageToken: String?,
        comments: List<YouTubeImportedComment>
    ): PersistedPageCounts = database.withTransaction {
        val import = requireImport(importId)
        check(import.videoId == video.videoId)
        val rows = comments.map { it.toEntity(import) }
        val rowIds = if (rows.isEmpty()) emptyList() else database.commentDao().insertCommentsIgnoringDuplicates(rows)
        val inserted = rowIds.count { it != -1L }.toLong()
        val deduplicated = rows.size.toLong() - inserted
        database.importDao().updateImport(
            import.copy(
                status = ImportStatus.RUNNING,
                nextThreadPageToken = nextPageToken,
                activeParentCommentId = null,
                nextReplyPageToken = null,
                itemsSeen = import.itemsSeen + comments.size,
                itemsInserted = import.itemsInserted + inserted,
                itemsDeduplicated = import.itemsDeduplicated + deduplicated,
                lastErrorCode = null,
                retryable = null
            )
        )
        PersistedPageCounts(inserted, deduplicated)
    }

    override suspend fun persistReplyPage(
        importId: String,
        parentCommentId: String,
        consumedPageToken: String?,
        nextPageToken: String?,
        replies: List<YouTubeImportedComment>,
        threadComplete: Boolean
    ): PersistedPageCounts = database.withTransaction {
        val import = requireImport(importId)
        val rows = replies.map { it.toEntity(import) }
        val rowIds = if (rows.isEmpty()) emptyList() else database.commentDao().insertCommentsIgnoringDuplicates(rows)
        val inserted = rowIds.count { it != -1L }.toLong()
        val deduplicated = rows.size.toLong() - inserted
        database.importDao().updateImport(
            import.copy(
                status = ImportStatus.RUNNING,
                activeParentCommentId = parentCommentId.takeUnless { threadComplete },
                nextReplyPageToken = nextPageToken.takeUnless { threadComplete },
                itemsSeen = import.itemsSeen + replies.size,
                itemsInserted = import.itemsInserted + inserted,
                itemsDeduplicated = import.itemsDeduplicated + deduplicated
            )
        )
        PersistedPageCounts(inserted, deduplicated)
    }

    override suspend fun markImportCompleted(importId: String) {
        updateImport(importId) { it.copy(status = ImportStatus.COMPLETED, completedAt = clock(), lastErrorCode = null, retryable = false) }
    }

    override suspend fun markImportFailed(importId: String, code: String, retryable: Boolean) {
        updateImport(importId) {
            it.copy(
                status = ImportStatus.FAILED,
                itemsFailed = it.itemsFailed + 1,
                lastErrorCode = code,
                retryable = retryable
            )
        }
    }

    override suspend fun markImportCancelled(importId: String) {
        updateImport(importId) { it.copy(status = ImportStatus.CANCELLED, lastErrorCode = "cancelled", retryable = false) }
    }

    private suspend fun requireImport(importId: String): ImportEntity =
        database.importDao().getImportById(importId) ?: error("Import run is not initialized")

    private suspend fun updateImport(importId: String, transform: (ImportEntity) -> ImportEntity) {
        database.withTransaction {
            val import = database.importDao().getImportById(importId) ?: return@withTransaction
            database.importDao().updateImport(transform(import))
        }
    }

    private fun YouTubeImportedComment.toEntity(import: ImportEntity): CommentEntity {
        val normalizedText = TextNormalizer.normalize(text)
        val publishedMillis = publishedAt.toEpochMillisOrNull()
        val localId = stableLocalId(import.importId, sourceCommentId)
        val sourceScope = import.sourceScopeKey
        return CommentEntity(
            localId = localId,
            sourceType = SourceType.YOUTUBE_API,
            sourceScopeKey = sourceScope,
            sourceCommentId = sourceCommentId,
            parentSourceCommentId = parentSourceCommentId,
            videoId = videoId,
            importId = import.importId,
            authorDisplayName = authorDisplayName,
            originalText = text,
            normalizedText = normalizedText,
            publishedAtEpochMillis = publishedMillis,
            publishedAtPrecision = publishedAt?.let { if (it.contains('.')) TimestampPrecision.MILLISECOND else TimestampPrecision.SECOND },
            fingerprintVersion = FingerprintV1.VERSION,
            fingerprintSha256 = FingerprintV1.compute(sourceScope, videoId, normalizedText, publishedMillis, authorDisplayName),
            processingState = ProcessingState.IMPORTED,
            currentStage = ProcessingStage.INGESTION,
            category = null,
            sentiment = null,
            relevanceScore = null,
            processingConfigVersion = import.sourceConfigVersion.toLong(),
            leaseOwner = null,
            leaseExpiresAt = null,
            nextAttemptAt = null,
            attemptCountLocal = 0,
            attemptCountJev = 0,
            attemptCountGlm = 0,
            lastErrorCode = null,
            lastErrorSummary = null,
            createdAt = clock(),
            updatedAt = clock(),
            likeCount = likeCount,
            declaredReplyCount = declaredReplyCount,
            sourcePublishedAtRaw = publishedAt,
            sourceUpdatedAtRaw = updatedAt
        )
    }

    private fun String?.toEpochMillisOrNull(): Long? = try {
        this?.let { Instant.parse(it).toEpochMilli() }
    } catch (_: Exception) {
        null
    }

    private fun stableLocalId(importId: String, sourceCommentId: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$importId\u001F$sourceCommentId".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
