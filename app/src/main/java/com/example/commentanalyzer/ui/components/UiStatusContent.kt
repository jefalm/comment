package com.example.commentanalyzer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.commentanalyzer.R

sealed interface UiPresentationState {
    data object Loading : UiPresentationState

    data class Empty(@StringRes val messageRes: Int) : UiPresentationState

    data class Unavailable(
        @StringRes val titleRes: Int,
        @StringRes val messageRes: Int
    ) : UiPresentationState

    data class Error(val kind: UiErrorKind) : UiPresentationState
}

enum class UiErrorKind(
    val isRecoverable: Boolean,
    @StringRes val messageRes: Int
) {
    RECOVERABLE(true, R.string.ui_status_recoverable_error),
    PERMANENT(false, R.string.ui_status_permanent_error);

    companion object {
        fun fromRetryable(retryable: Boolean): UiErrorKind =
            if (retryable) RECOVERABLE else PERMANENT
    }
}

@Composable
fun UiStatusContent(
    status: UiPresentationState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (status) {
            UiPresentationState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .testTag("ui_status_loading_indicator")
                        .semantics { contentDescription = "Loading" }
                )
                Text(stringResource(R.string.ui_status_loading))
            }
            is UiPresentationState.Empty -> {
                Text(
                    text = stringResource(status.messageRes),
                    modifier = Modifier.testTag("ui_status_empty"),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            is UiPresentationState.Unavailable -> {
                Text(
                    text = stringResource(status.titleRes),
                    modifier = Modifier.testTag("ui_status_unavailable_title"),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(status.messageRes),
                    modifier = Modifier.testTag("ui_status_unavailable_message"),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            is UiPresentationState.Error -> {
                Text(
                    text = stringResource(status.kind.messageRes),
                    modifier = Modifier.testTag("ui_status_error"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                if (status.kind.isRecoverable && onRetry != null) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.ui_status_retry))
                    }
                }
            }
        }
    }
}
