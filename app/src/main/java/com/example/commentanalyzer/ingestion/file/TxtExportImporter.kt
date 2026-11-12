package com.example.commentanalyzer.ingestion.file

import androidx.room.withTransaction
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.ProcessingConfigBootstrap
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.TimestampPrecision
import com.example.commentanalyzer.domain.fingerprint.FingerprintV2
import com.example.commentanalyzer.domain.model.ImportProgress
import com.example.commentanalyzer.domain.model.ImportRequest
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.normalize.TextNormalizer
import com.example.commentanalyzer.ingestion.CommentImporter
import java.io.File
import kotlinx.coroutines.CancellationException

class TxtExportImporter(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : CommentImporter {
    override suspend fun import(request: ImportRequest): ImportProgress {
        if (request.sourceType != SourceType.TXT_EXPORT) {
            return ImportProgress(request.importId, 0, 0, 0, 1, false, "unsupported_source", false)
        }
        return try {
            val file = File(request.sourceLocator)
            if (!file.isFile || file.length() > TxtExportParser.MAX_FILE_SIZE_BYTES) {
                return ImportProgress(request.importId, 0, 0, 0, 1, false, "invalid_txt_file", false)
            }
            val export = file.inputStream().use(TxtExportParser::parse)
            var inserted = 0L
            var deduplicated = 0L
            val now = clock()
            database.withTransaction {
                val previous = database.importDao().getImportById(request.importId)
                val activeConfig = database.processingConfigDao().getActiveConfig()
                val targetVersion = previous?.sourceConfigVersion
                    ?: activeConfig?.configVersion?.toInt()
                    ?: ProcessingConfigBootstrap.CURRENT_CONFIG_VERSION.toInt()
                val import = ImportEntity(
                    importId = request.importId,
                    sourceType = SourceType.TXT_EXPORT,
                    sourceLocator = file.name,
                    sourceScopeKey = request.sourceScopeKey,
                    status = ImportStatus.RUNNING,
                    includeReplies = false,
                    nextThreadPageToken = null,
                    activeParentCommentId = null,
                    nextReplyPageToken = null,
                    itemsSeen = previous?.itemsSeen ?: 0,
                    itemsInserted = previous?.itemsInserted ?: 0,
                    itemsDeduplicated = previous?.itemsDeduplicated ?: 0,
                    itemsFailed = 0,
                    startedAt = previous?.startedAt ?: now,
                    completedAt = null,
                    lastErrorCode = null,
                    videoTitle = export.videoDetails.title,
                    channelTitle = export.videoDetails.channel,
                    publishedAtRaw = export.videoDetails.publishedAt,
                    publishedLocalDateTimeRaw = export.videoDetails.publishedLocalDateTime.toString(),
                    viewCount = export.videoDetails.views,
                    declaredCommentCount = export.videoDetails.totalCommentsCount.toLong(),
                    unlabeledStatisticRaw = export.videoDetails.unlabeledStatisticRaw,
                    topLevelCommentCount = export.statistics.topLevelComments,
                    declaredReplyCount = export.statistics.declaredReplyCount,
                    totalLikes = export.statistics.totalLikes,
                    averageCommentsPerDay = export.statistics.avgCommentsPerDay,
                    averageLikesPerComment = export.statistics.avgLikesPerComment,
                    sourceConfigVersion = targetVersion
                )
                database.importDao().insertImport(import)
                val rows = export.comments.map { comment ->
                    val normalized = TextNormalizer.normalize(comment.text)
                    val fingerprint = FingerprintV2.compute(
                        sourceScopeKey = request.sourceScopeKey,
                        videoId = null,
                        normalizedText = normalized,
                        publishedAtRaw = comment.date,
                        authorDisplayName = comment.author
                    )
                    CommentEntity(
                        localId = "${request.importId}:$fingerprint",
                        sourceType = SourceType.TXT_EXPORT,
                        sourceScopeKey = request.sourceScopeKey,
                        sourceCommentId = null,
                        parentSourceCommentId = null,
                        videoId = null,
                        importId = request.importId,
                        authorDisplayName = comment.author,
                        originalText = comment.text,
                        normalizedText = normalized,
                        publishedAtEpochMillis = null,
                        publishedAtPrecision = TimestampPrecision.UNKNOWN,
                        fingerprintVersion = FingerprintV2.VERSION,
                        fingerprintSha256 = fingerprint,
                        processingState = ProcessingState.IMPORTED,
                        currentStage = ProcessingStage.INGESTION,
                        category = null,
                        sentiment = null,
                        relevanceScore = null,
                        processingConfigVersion = targetVersion.toLong(),
                        leaseOwner = null,
                        leaseExpiresAt = null,
                        nextAttemptAt = null,
                        attemptCountLocal = 0,
                        attemptCountJev = 0,
                        attemptCountGlm = 0,
                        lastErrorCode = null,
                        lastErrorSummary = null,
                        createdAt = now,
                        updatedAt = now,
                        likeCount = comment.likes,
                        declaredReplyCount = comment.declaredReplyCount,
                        sourcePublishedAtRaw = comment.date,
                        publishedAtLocalDateTimeRaw = comment.publishedLocalDateTime?.toString()
                    )
                }
                val rowIds = database.commentDao().insertCommentsIgnoringDuplicates(rows)
                inserted = rowIds.count { it != -1L }.toLong()
                deduplicated = rows.size.toLong() - inserted
                database.importDao().updateImport(
                    import.copy(
                        status = ImportStatus.COMPLETED,
                        itemsSeen = import.itemsSeen + rows.size,
                        itemsInserted = import.itemsInserted + inserted,
                        itemsDeduplicated = import.itemsDeduplicated + deduplicated,
                        completedAt = now
                    )
                )
            }
            ImportProgress(request.importId, export.comments.size.toLong(), inserted, deduplicated, 0, true, null, false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: TxtParseException) {
            ImportProgress(request.importId, 0, 0, 0, 1, false, "txt_${failure.reasonCode}", false)
        } catch (_: Exception) {
            ImportProgress(request.importId, 0, 0, 0, 1, false, "txt_import_failed", false)
        }
    }
}
