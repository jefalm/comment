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

    data class Error(
        @StringRes val messageRes: Int = R.string.ui_status_error
    ) : UiPresentationState
}

data class UiStatusCopy(
    @StringRes val titleRes: Int? = null,
    @StringRes val messageRes: Int
)

internal fun UiPresentationState.toStatusCopy(): UiStatusCopy = when (this) {
    UiPresentationState.Loading -> UiStatusCopy(messageRes = R.string.ui_status_loading)
    is UiPresentationState.Empty -> UiStatusCopy(messageRes = messageRes)
    is UiPresentationState.Unavailable -> UiStatusCopy(titleRes, messageRes)
    is UiPresentationState.Error -> UiStatusCopy(messageRes = messageRes)
}

data class UiStatusAction(
    @StringRes val labelRes: Int,
    val onClick: () -> Unit
)

@Composable
fun UiStatusContent(
    status: UiPresentationState,
    modifier: Modifier = Modifier,
    action: UiStatusAction? = null
) {
    val copy = status.toStatusCopy()
    val loadingDescription = stringResource(R.string.ui_status_loading_description)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (status == UiPresentationState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .testTag("ui_status_loading_indicator")
                    .semantics { contentDescription = loadingDescription }
            )
        }
        copy.titleRes?.let { titleRes ->
            Text(
                text = stringResource(titleRes),
                modifier = Modifier.testTag("ui_status_title"),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Text(
            text = stringResource(copy.messageRes),
            modifier = Modifier.testTag("ui_status_message"),
            style = MaterialTheme.typography.bodyLarge,
            color = if (status is UiPresentationState.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        action?.let {
            Button(onClick = it.onClick) {
                Text(stringResource(it.labelRes))
            }
        }
    }
}
