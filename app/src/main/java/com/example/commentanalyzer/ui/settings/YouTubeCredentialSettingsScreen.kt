package com.example.commentanalyzer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.commentanalyzer.R

@Composable
fun YouTubeCredentialSettingsScreen(
    hasYouTubeApiKey: Boolean,
    onManageApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
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