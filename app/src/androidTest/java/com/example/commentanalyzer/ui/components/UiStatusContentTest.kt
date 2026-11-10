package com.example.commentanalyzer.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.commentanalyzer.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiStatusContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateHasAccessibleProgress() {
        show(UiPresentationState.Loading)

        composeRule.onNodeWithTag("ui_status_loading_indicator").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsItsMessage() {
        show(UiPresentationState.Empty(R.string.ui_status_empty))

        composeRule.onNodeWithTag("ui_status_empty").assertIsDisplayed()
        composeRule.onNodeWithText("No content is available.").assertIsDisplayed()
    }

    @Test
    fun unavailableStateHasNoAction() {
        show(
            UiPresentationState.Unavailable(
                R.string.comments_unavailable_title,
                R.string.comments_unavailable_message
            ),
            onRetry = {}
        )

        composeRule.onNodeWithTag("ui_status_unavailable_title").assertIsDisplayed()
        composeRule.onNodeWithTag("ui_status_unavailable_message").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun recoverableErrorInvokesRetry() {
        var retryCount = 0
        show(UiPresentationState.Error(UiErrorKind.RECOVERABLE)) { retryCount++ }

        composeRule.onNodeWithTag("ui_status_error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun permanentErrorHasNoRetryAction() {
        show(UiPresentationState.Error(UiErrorKind.PERMANENT), onRetry = {})

        composeRule.onNodeWithTag("ui_status_error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun recoverableErrorRemainsAvailableWithLargeTextAndDarkTheme() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    UiStatusContent(
                        status = UiPresentationState.Error(UiErrorKind.RECOVERABLE),
                        onRetry = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("ui_status_error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    private fun show(
        status: UiPresentationState,
        onRetry: (() -> Unit)? = null
    ) {
        composeRule.setContent {
            MaterialTheme {
                UiStatusContent(status = status, onRetry = onRetry)
            }
        }
    }
}
