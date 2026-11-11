package com.example.commentanalyzer.ui.analysis

import com.example.commentanalyzer.data.local.dao.AnalysisProcessingCounts
import com.example.commentanalyzer.worker.AnalysisWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisActionControllerTest {
    @Test
    fun persistedCompletionCountsDistinguishSuccessPartialFailureAndNoEligibleWork() {
        assertEquals(
            AnalysisActionMessage.SUCCEEDED,
            classifyAnalysisCompletion(processing(completed = 4))
        )
        assertEquals(
            AnalysisActionMessage.PARTIAL,
            classifyAnalysisCompletion(processing(completed = 2, failed = 1))
        )
        assertEquals(
            AnalysisActionMessage.ANALYSIS_FAILED,
            classifyAnalysisCompletion(processing(failed = 3))
        )
        assertEquals(
            AnalysisActionMessage.NO_ELIGIBLE_COMMENTS,
            classifyAnalysisCompletion(processing(total = 5, locallyRejected = 5))
        )
    }

    @Test
    fun pendingPersistedWorkDoesNotReportCompletion() {
        assertEquals(
            null,
            classifyAnalysisCompletion(processing(retrying = 1))
        )
    }

    @Test
    fun notReadyActionDoesNotEnqueueWork() {
        var enqueueCount = 0
        var message: AnalysisActionMessage? = null
        val controller = controller(
            ready = false,
            enqueue = { _, _ -> enqueueCount++ },
            onMessage = { message = it }
        )

        assertFalse(controller.start(IMPORT_ID, CONFIG_VERSION))
        assertEquals(0, enqueueCount)
        assertEquals(AnalysisActionMessage.NOT_READY, message)
    }

    @Test
    fun repeatedAnalyzeActionsEnqueueOncePerImportAndConfigScope() {
        val enqueuedScopes = mutableListOf<Pair<String, Long>>()
        val controller = controller(
            enqueue = { importId, version -> enqueuedScopes += importId to version }
        )

        assertTrue(controller.start(IMPORT_ID, CONFIG_VERSION))
        assertFalse(controller.start(IMPORT_ID, CONFIG_VERSION))
        assertTrue(controller.start(IMPORT_ID, CONFIG_VERSION + 1))

        assertEquals(
            listOf(IMPORT_ID to CONFIG_VERSION, IMPORT_ID to (CONFIG_VERSION + 1)),
            enqueuedScopes
        )
    }

    @Test
    fun uniqueWorkNameIsStableAndScopedByImportAndVersion() {
        val original = AnalysisWorker.uniqueWorkName(IMPORT_ID, CONFIG_VERSION)

        assertEquals(original, AnalysisWorker.uniqueWorkName(IMPORT_ID, CONFIG_VERSION))
        assertNotEquals(original, AnalysisWorker.uniqueWorkName(IMPORT_ID, CONFIG_VERSION + 1))
        assertNotEquals(original, AnalysisWorker.uniqueWorkName("other-import", CONFIG_VERSION))
    }

    private fun controller(
        ready: Boolean = true,
        enqueue: (String, Long) -> Unit = { _, _ -> },
        onMessage: (AnalysisActionMessage?) -> Unit = {}
    ) = AnalysisActionController(
        isReady = { ready },
        enqueue = enqueue,
        onStateChanged = { _, _, _, message -> onMessage(message) }
    )

    private fun processing(
        total: Long = 0,
        locallyRejected: Long = 0,
        jevRejected: Long = 0,
        completed: Long = 0,
        retrying: Long = 0,
        failed: Long = 0,
        inProgress: Long = 0
    ) = AnalysisProcessingCounts(
        totalComments = total,
        locallyRejected = locallyRejected,
        jevRejected = jevRejected,
        completed = completed,
        retrying = retrying,
        permanentlyFailed = failed,
        inProgress = inProgress
    )

    private companion object {
        const val IMPORT_ID = "analysis-test-import"
        const val CONFIG_VERSION = 4L
    }
}
