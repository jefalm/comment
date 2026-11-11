package com.example.commentanalyzer

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.example.commentanalyzer.network.youtube.YouTubeApiService
import com.example.commentanalyzer.ui.comments.CommentsScreen
import com.example.commentanalyzer.ui.comments.CommentsViewModel
import com.example.commentanalyzer.ui.analysis.AnalysisViewModel
import com.example.commentanalyzer.ui.navigation.CommentAnalyzerNavigation
import com.example.commentanalyzer.ui.settings.YouTubeCredentialSettingsScreen
import com.example.commentanalyzer.ui.viewer.CommentViewerScreen
import com.example.commentanalyzer.ui.viewer.CommentViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CommentViewerViewModel = viewModel()
                    val commentsViewModel: CommentsViewModel = viewModel()
                    val analysisViewModel: AnalysisViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    CommentAnalyzerNavigation(
                        homeContent = {
                            CommentViewerScreen(
                                viewModel = viewModel,
                                onManageApiKey = { showYouTubeApiKeyDialog(viewModel) }
                            )
                        },
                        commentsContent = { CommentsScreen(commentsViewModel) },
                        analysisViewModel = analysisViewModel,
                        settingsContent = {
                            YouTubeCredentialSettingsScreen(
                                hasYouTubeApiKey = uiState.hasYouTubeApiKey,
                                onManageApiKey = { showYouTubeApiKeyDialog(viewModel) },
                                hasOpenRouterKey = uiState.hasOpenRouterKey,
                                openrouterKeyMask = uiState.openrouterKeyMask,
                                onManageOpenRouterKey = { showOpenRouterApiKeyDialog(viewModel) },
                                remoteProcessingConsent = uiState.remoteProcessingConsent,
                                onRemoteProcessingConsentChange = { viewModel.setRemoteProcessingConsent(it) },
                                isRemoteProcessingReady = uiState.isRemoteProcessingReady
                            )
                        }
                    )
                }
            }
        }
    }

    private fun showYouTubeApiKeyDialog(viewModel: CommentViewerViewModel) {
        val app = application as CommentAnalyzerApplication
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Paste API key"
            contentDescription = "YouTube Data API key"
            maxLines = 1
            isSaveEnabled = false
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val deleteButton = Button(this).apply { text = "Delete saved key" }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
            addView(input)
            addView(deleteButton)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("YouTube API key")
            .setMessage("The key is stored using Android encrypted preferences.")
            .setView(content)
            .setNegativeButton("Close", null)
            .setPositiveButton("Save or replace", null)
            .setNeutralButton("Test key", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = input.text.toString()
                if (candidate.isBlank()) {
                    Toast.makeText(this, "Enter a key first", Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        app.secureCredentialStore.saveApiKey(YOUTUBE_API_KEY_NAME, candidate)
                    }.onSuccess {
                        viewModel.refreshApiKeyStatus()
                        Toast.makeText(this, "Key saved", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }.onFailure {
                        Toast.makeText(this, "Secure key storage is unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val candidate = input.text.toString().ifBlank {
                    runCatching { app.secureCredentialStore.getApiKey(YOUTUBE_API_KEY_NAME) }.getOrNull().orEmpty()
                }
                if (candidate.isBlank()) {
                    Toast.makeText(this, "Enter a key to test", Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch {
                        val valid = runCatching {
                            withContext(Dispatchers.IO) {
                                YouTubeApiService.create().videos(videoId = "dQw4w9WgXcQ", apiKey = candidate).isSuccessful
                            }
                        }.getOrDefault(false)
                        Toast.makeText(
                            this@MainActivity,
                            if (valid) "Key accepted" else "Key could not be validated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            deleteButton.setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Delete the saved YouTube API key?")
                    .setNegativeButton("Keep key", null)
                    .setPositiveButton("Delete") { _, _ ->
                        runCatching { app.secureCredentialStore.clearApiKey(YOUTUBE_API_KEY_NAME) }
                            .onSuccess {
                                input.text?.clear()
                                viewModel.refreshApiKeyStatus()
                                Toast.makeText(this, "Key deleted", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(this, "Secure key storage is unavailable", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .show()
            }
        }
        dialog.show()
    }

    private fun showOpenRouterApiKeyDialog(viewModel: CommentViewerViewModel) {
        val app = application as CommentAnalyzerApplication
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Paste OpenRouter API key"
            contentDescription = "OpenRouter API key"
            maxLines = 1
            isSaveEnabled = false
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val deleteButton = Button(this).apply { text = "Delete saved OpenRouter key" }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
            addView(input)
            addView(deleteButton)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("OpenRouter API key")
            .setMessage("The OpenRouter key is stored securely. Remote processing requires both a key and explicit consent.")
            .setView(content)
            .setNegativeButton("Close", null)
            .setPositiveButton("Save or replace", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val candidate = input.text.toString()
                if (candidate.isBlank()) {
                    Toast.makeText(this, "Enter a key first", Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        app.secureCredentialStore.saveApiKey(OPENROUTER_API_KEY_NAME, candidate)
                    }.onSuccess {
                        viewModel.refreshOpenRouterStatus()
                        Toast.makeText(this, "OpenRouter key saved", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }.onFailure {
                        Toast.makeText(this, "Secure key storage is unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            deleteButton.setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Delete the saved OpenRouter API key?")
                    .setNegativeButton("Keep key", null)
                    .setPositiveButton("Delete") { _, _ ->
                        runCatching { app.secureCredentialStore.clearApiKey(OPENROUTER_API_KEY_NAME) }
                            .onSuccess {
                                input.text?.clear()
                                viewModel.refreshOpenRouterStatus()
                                Toast.makeText(this, "OpenRouter key deleted", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(this, "Secure key storage is unavailable", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .show()
            }
        }
        dialog.show()
    }

    private companion object {
        const val YOUTUBE_API_KEY_NAME = CommentAnalyzerApplication.YOUTUBE_API_KEY_NAME
        const val OPENROUTER_API_KEY_NAME = CommentAnalyzerApplication.OPENROUTER_API_KEY_NAME
    }
}
