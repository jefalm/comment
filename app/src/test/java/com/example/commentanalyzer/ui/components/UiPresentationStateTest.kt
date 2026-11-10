package com.example.commentanalyzer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPresentationStateTest {
    @Test
    fun retryabilityMapsToTheMatchingSafeErrorKind() {
        assertEquals(UiErrorKind.RECOVERABLE, UiErrorKind.fromRetryable(retryable = true))
        assertEquals(UiErrorKind.PERMANENT, UiErrorKind.fromRetryable(retryable = false))
    }
}
