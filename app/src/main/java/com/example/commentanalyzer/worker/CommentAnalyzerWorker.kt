package com.example.commentanalyzer.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CommentAnalyzerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return Result.success()
    }

    companion object {
        const val WORK_NAME_INGESTION = "comment_analyzer_ingestion_work"
        const val WORK_NAME_COORDINATOR = "comment_analyzer_coordinator_work"
    }
}
