package com.example.commentanalyzer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.commentanalyzer.R

@Composable
fun YouTubeCredentialSettingsScreen(
    hasYouTubeApiKey: Boolean,
    onManageApiKey: () -> Unit,
    hasOpenRouterKey: Boolean,
    openrouterKeyMask: String,
    onManageOpenRouterKey: () -> Unit,
    remoteProcessingConsent: Boolean,
    onRemoteProcessingConsentChange: (Boolean) -> Unit,
    isRemoteProcessingReady: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        // YouTube API Key Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.settings_youtube_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (hasYouTubeApiKey) R.string.settings_youtube_configured
                        else R.string.settings_youtube_not_configured
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onManageApiKey) {
                    Text(stringResource(R.string.settings_youtube_manage))
                }
            }
        }

        // OpenRouter & Remote Processing Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.settings_openrouter_title), style = MaterialTheme.typography.titleMedium)
                Text(openrouterKeyMask, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onManageOpenRouterKey) {
                    Text(stringResource(R.string.settings_openrouter_manage))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_remote_processing_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_remote_processing_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = remoteProcessingConsent,
                        onCheckedChange = onRemoteProcessingConsentChange
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isRemoteProcessingReady) "Status: Ready for remote analysis" else "Status: Not ready (missing key or consent)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isRemoteProcessingReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
