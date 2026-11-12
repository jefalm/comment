package com.example.commentanalyzer

import android.app.Application
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.ProcessingConfigBootstrap
import com.example.commentanalyzer.data.local.RoomYouTubeImportPageSink
import com.example.commentanalyzer.data.security.SecureCredentialStore
import com.example.commentanalyzer.ingestion.file.TxtExportImporter
import com.example.commentanalyzer.ingestion.youtube.YouTubeApiKeyProvider
import com.example.commentanalyzer.ingestion.youtube.YouTubeCommentImporter
import com.example.commentanalyzer.network.youtube.YouTubeApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CommentAnalyzerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.create(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            ProcessingConfigBootstrap.bootstrapActiveConfig(database)
        }
    }
    val secureCredentialStore: SecureCredentialStore by lazy { SecureCredentialStore(this) }
    val container: AppContainer by lazy {
        ProductionAppContainer(
            database = database,
            openRouterKeyProvider = {
                secureCredentialStore.getApiKey(OPENROUTER_API_KEY_NAME)
            },
            remoteProcessingConsentProvider = {
                secureCredentialStore.getBoolean(OPENROUTER_CONSENT_KEY, false)
            }
        )
    }
    val txtExportImporter: TxtExportImporter by lazy { TxtExportImporter(database) }
    val youtubeImportPageSink: RoomYouTubeImportPageSink by lazy { RoomYouTubeImportPageSink(database) }
    val youtubeCommentImporter: YouTubeCommentImporter by lazy {
        YouTubeCommentImporter(
            service = YouTubeApiService.create(),
            apiKeyProvider = YouTubeApiKeyProvider {
                secureCredentialStore.getApiKey(YOUTUBE_API_KEY_NAME)
            },
            pageSink = youtubeImportPageSink
        )
    }

    companion object {
        const val YOUTUBE_API_KEY_NAME = "youtube_data_api_key"
        const val OPENROUTER_API_KEY_NAME = "openrouter_api_key"
        const val OPENROUTER_CONSENT_KEY = "openrouter_remote_consent"
    }
}
