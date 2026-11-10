package com.example.commentanalyzer.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.commentanalyzer.CommentAnalyzerApplication
import com.example.commentanalyzer.domain.model.ImportRequest
import com.example.commentanalyzer.domain.model.SourceType
import java.util.concurrent.TimeUnit

class CommentAnalyzerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CommentAnalyzerApplication ?: return Result.failure()
        val importId = inputData.getString(KEY_IMPORT_ID) ?: return Result.failure()
        val saved = app.database.importDao().getImportById(importId)
        val request = ImportRequest(
            importId = importId,
            sourceType = SourceType.YOUTUBE_API,
            sourceLocator = saved?.videoId ?: inputData.getString(KEY_SOURCE_LOCATOR).orEmpty(),
            sourceScopeKey = saved?.sourceScopeKey ?: inputData.getString(KEY_SOURCE_SCOPE).orEmpty(),
            includeReplies = saved?.includeReplies ?: inputData.getBoolean(KEY_INCLUDE_REPLIES, false),
            nextThreadPageToken = saved?.nextThreadPageToken ?: inputData.getString(KEY_THREAD_PAGE_TOKEN),
            activeParentCommentId = saved?.activeParentCommentId ?: inputData.getString(KEY_REPLY_PARENT_ID),
            nextReplyPageToken = saved?.nextReplyPageToken ?: inputData.getString(KEY_REPLY_PAGE_TOKEN)
        )
        val progress = app.youtubeCommentImporter.import(request)
        return when {
            progress.completed -> Result.success()
            progress.retryable == true -> Result.retry()
            else -> Result.failure(Data.Builder().putString(KEY_ERROR_CODE, progress.lastErrorCode).build())
        }
    }

    companion object {
        const val WORK_NAME_INGESTION = "comment_analyzer_ingestion_work"
        const val WORK_NAME_COORDINATOR = "comment_analyzer_coordinator_work"
        const val KEY_IMPORT_ID = "import_id"
        const val KEY_SOURCE_LOCATOR = "source_locator"
        const val KEY_SOURCE_SCOPE = "source_scope"
        const val KEY_INCLUDE_REPLIES = "include_replies"
        const val KEY_THREAD_PAGE_TOKEN = "thread_page_token"
        const val KEY_REPLY_PARENT_ID = "reply_parent_id"
        const val KEY_REPLY_PAGE_TOKEN = "reply_page_token"
        const val KEY_ERROR_CODE = "error_code"

        fun uniqueImportWorkName(importId: String) = "youtube_import_$importId"

        fun enqueue(context: Context, request: ImportRequest) {
            val input = Data.Builder()
                .putString(KEY_IMPORT_ID, request.importId)
                .putString(KEY_SOURCE_LOCATOR, request.sourceLocator)
                .putString(KEY_SOURCE_SCOPE, request.sourceScopeKey)
                .putBoolean(KEY_INCLUDE_REPLIES, request.includeReplies)
                .putString(KEY_THREAD_PAGE_TOKEN, request.nextThreadPageToken)
                .putString(KEY_REPLY_PARENT_ID, request.activeParentCommentId)
                .putString(KEY_REPLY_PAGE_TOKEN, request.nextReplyPageToken)
                .build()
            val work = OneTimeWorkRequestBuilder<CommentAnalyzerWorker>()
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueImportWorkName(request.importId),
                ExistingWorkPolicy.KEEP,
                work
            )
        }

        fun cancel(context: Context, importId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueImportWorkName(importId))
        }
    }
}
