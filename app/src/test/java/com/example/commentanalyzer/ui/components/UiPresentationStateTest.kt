package com.example.commentanalyzer.ui.components

import com.example.commentanalyzer.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UiPresentationStateTest {
    @Test
    fun unavailableAndErrorStatesMapToTheirSuppliedCopy() {
        val unavailable = UiPresentationState.Unavailable(
            titleRes = R.string.comments_unavailable_title,
            messageRes = R.string.comments_unavailable_message
        )
        val error = UiPresentationState.Error(messageRes = R.string.ui_status_error)

        assertEquals(
            UiStatusCopy(
                titleRes = R.string.comments_unavailable_title,
                messageRes = R.string.comments_unavailable_message
            ),
            unavailable.toStatusCopy()
        )
        assertEquals(UiStatusCopy(messageRes = R.string.ui_status_error), error.toStatusCopy())
    }
}
