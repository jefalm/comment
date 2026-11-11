package com.example.commentanalyzer.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.commentanalyzer.AnalysisAppContainer
import com.example.commentanalyzer.CommentAnalyzerApplication
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CommentAnalyzerApplication ?: return Result.failure()
        val importId = inputData.getString(KEY_IMPORT_ID) ?: return Result.failure()
        val configVersion = inputData.getLong(KEY_CONFIG_VERSION, INVALID_CONFIG_VERSION)
        if (configVersion == INVALID_CONFIG_VERSION) return Result.failure()

        val hasKey = !app.secureCredentialStore.getApiKey(
            CommentAnalyzerApplication.OPENROUTER_API_KEY_NAME
        ).isNullOrBlank()
        val hasConsent = app.secureCredentialStore.getBoolean(
            CommentAnalyzerApplication.OPENROUTER_CONSENT_KEY,
            false
        )
        if (!hasKey || !hasConsent) {
            return Result.failure(Data.Builder().putString(KEY_ERROR, ERROR_NOT_READY).build())
        }

        val container = app.container as? AnalysisAppContainer ?: return Result.failure()
        val result = container.runAnalysis(importId, configVersion)
        if (result.remoteProcessingBlocked) {
            return Result.failure(Data.Builder().putString(KEY_ERROR, ERROR_NOT_READY).build())
        }
        if (app.database.processingBatchDao().hasPendingAnalysisBatches(
                importId,
                configVersion,
                System.currentTimeMillis()
            )
        ) {
            return Result.retry()
        }
        return Result.success(
            Data.Builder()
                .putBoolean(
                    KEY_NO_WORK,
                    result.attemptedJevBatches + result.attemptedGlmBatches == 0
                )
                .build()
        )
    }

    companion object {
        const val KEY_IMPORT_ID = "analysis_import_id"
        const val KEY_CONFIG_VERSION = "analysis_config_version"
        const val KEY_NO_WORK = "analysis_no_work"
        const val KEY_ERROR = "analysis_error"
        const val ERROR_NOT_READY = "remote_processing_not_ready"
        private const val INVALID_CONFIG_VERSION = Long.MIN_VALUE

        fun uniqueWorkName(importId: String, configVersion: Long): String {
            require(importId.isNotBlank())
            val scopedKey = "$importId\u001F$configVersion"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(scopedKey.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "comment_analysis_$digest"
        }

        fun enqueue(context: Context, importId: String, configVersion: Long) {
            val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_IMPORT_ID, importId)
                        .putLong(KEY_CONFIG_VERSION, configVersion)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(importId, configVersion),
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
