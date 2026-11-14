package com.example.commentanalyzer.data.local

data class PipelineMetrics(
    val discoveredCount: Int = 0,
    val locallyRejectedCount: Int = 0,
    val locallyReviewedCount: Int = 0,
    val locallyPassedCount: Int = 0,
    val jevRejectedCount: Int = 0,
    val jevReviewedCount: Int = 0,
    val jevPassedCount: Int = 0,
    val glmQueuedCount: Int = 0,
    val glmCompletedCount: Int = 0,
    val retryingCount: Int = 0,
    val failedCount: Int = 0,
    val jevRequestCount: Int = 0,
    val glmRequestCount: Int = 0,
    val synthesisRequestCount: Int = 0,
    val queueTimeMs: Long = 0L,
    val networkTimeMs: Long = 0L,
    val validationTimeMs: Long = 0L,
    val roomCommitTimeMs: Long = 0L,
    val timeToFirstResultMs: Long = 0L,
    val totalRunTimeMs: Long = 0L,
    val requestItemCount: Int = 0,
    val requestSerializedBytes: Long = 0L,
    val estimatedTokens: Long = 0L,
    val actualTokens: Long = 0L,
    val costUsd: Double = 0.0,
    val modelResolved: String? = null,
    val httpStatuses: List<Int> = emptyList(),
    val retriesCount: Int = 0,
    val splitsCount: Int = 0,
    val cancellationsCount: Int = 0,
    val leaseRecoveriesCount: Int = 0
)

interface TelemetryRecorder {
    fun recordEvent(event: TelemetryEvent)
    fun getMetrics(): PipelineMetrics
}

sealed interface TelemetryEvent {
    data class Discovered(val count: Int) : TelemetryEvent
    data class LocalFilter(val rejected: Int, val reviewed: Int, val passed: Int) : TelemetryEvent
    data class JevBatch(
        val requested: Int,
        val completed: Int,
        val rejected: Int,
        val passed: Int,
        val networkTimeMs: Long,
        val bytes: Long,
        val estTokens: Long,
        val actTokens: Long,
        val cost: Double,
        val httpStatus: Int,
        val model: String?
    ) : TelemetryEvent
    data class GlmBatch(
        val requested: Int,
        val completed: Int,
        val networkTimeMs: Long,
        val bytes: Long,
        val estTokens: Long,
        val actTokens: Long,
        val cost: Double,
        val httpStatus: Int,
        val model: String?
    ) : TelemetryEvent
    data class Synthesis(
        val requestCount: Int,
        val networkTimeMs: Long,
        val actTokens: Long,
        val cost: Double,
        val httpStatus: Int
    ) : TelemetryEvent
    data class Timing(
        val queueMs: Long,
        val validationMs: Long,
        val roomCommitMs: Long,
        val timeToFirstResultMs: Long,
        val totalMs: Long
    ) : TelemetryEvent
    data class Retry(val count: Int) : TelemetryEvent
    data class Split(val count: Int) : TelemetryEvent
    data class Cancellation(val count: Int) : TelemetryEvent
    data class LeaseRecovery(val count: Int) : TelemetryEvent
    data class Failure(val count: Int) : TelemetryEvent
}

class InMemoryTelemetryRecorder : TelemetryRecorder {
    private var discovered = 0
    private var locRejected = 0
    private var locReviewed = 0
    private var locPassed = 0
    private var jevRejected = 0
    private var jevReviewed = 0
    private var jevPassed = 0
    private var glmQueued = 0
    private var glmCompleted = 0
    private var retrying = 0
    private var failed = 0
    private var jevReqs = 0
    private var glmReqs = 0
    private var synthReqs = 0
    private var queueMs = 0L
    private var netMs = 0L
    private var valMs = 0L
    private var roomMs = 0L
    private var firstResultMs = 0L
    private var totalMs = 0L
    private var reqItems = 0
    private var reqBytes = 0L
    private var estTokens = 0L
    private var actTokens = 0L
    private var costUsd = 0.0
    private var model: String? = null
    private val httpStatuses = mutableListOf<Int>()
    private var retries = 0
    private var splits = 0
    private var cancels = 0
    private var leaseRecs = 0

    override fun recordEvent(event: TelemetryEvent) {
        when (event) {
            is TelemetryEvent.Discovered -> discovered += event.count
            is TelemetryEvent.LocalFilter -> {
                locRejected += event.rejected
                locReviewed += event.reviewed
                locPassed += event.passed
            }
            is TelemetryEvent.JevBatch -> {
                jevReqs++
                reqItems += event.requested
                jevReviewed += (event.completed - event.rejected - event.passed).coerceAtLeast(0)
                jevRejected += event.rejected
                jevPassed += event.passed
                glmQueued += event.passed
                netMs += event.networkTimeMs
                reqBytes += event.bytes
                estTokens += event.estTokens
                actTokens += event.actTokens
                costUsd += event.cost
                httpStatuses.add(event.httpStatus)
                if (event.model != null) model = event.model
            }
            is TelemetryEvent.GlmBatch -> {
                glmReqs++
                reqItems += event.requested
                glmCompleted += event.completed
                netMs += event.networkTimeMs
                reqBytes += event.bytes
                estTokens += event.estTokens
                actTokens += event.actTokens
                costUsd += event.cost
                httpStatuses.add(event.httpStatus)
                if (event.model != null) model = event.model
            }
            is TelemetryEvent.Synthesis -> {
                synthReqs += event.requestCount
                netMs += event.networkTimeMs
                actTokens += event.actTokens
                costUsd += event.cost
                httpStatuses.add(event.httpStatus)
            }
            is TelemetryEvent.Timing -> {
                queueMs += event.queueMs
                valMs += event.validationMs
                roomMs += event.roomCommitMs
                if (firstResultMs == 0L) firstResultMs = event.timeToFirstResultMs
                totalMs += event.totalMs
            }
            is TelemetryEvent.Retry -> retries += event.count
            is TelemetryEvent.Split -> splits += event.count
            is TelemetryEvent.Cancellation -> cancels += event.count
            is TelemetryEvent.LeaseRecovery -> leaseRecs += event.count
            is TelemetryEvent.Failure -> failed += event.count
        }
    }

    override fun getMetrics(): PipelineMetrics = PipelineMetrics(
        discoveredCount = discovered,
        locallyRejectedCount = locRejected,
        locallyReviewedCount = locReviewed,
        locallyPassedCount = locPassed,
        jevRejectedCount = jevRejected,
        jevReviewedCount = jevReviewed,
        jevPassedCount = jevPassed,
        glmQueuedCount = glmQueued,
        glmCompletedCount = glmCompleted,
        retryingCount = retrying,
        failedCount = failed,
        jevRequestCount = jevReqs,
        glmRequestCount = glmReqs,
        synthesisRequestCount = synthReqs,
        queueTimeMs = queueMs,
        networkTimeMs = netMs,
        validationTimeMs = valMs,
        roomCommitTimeMs = roomMs,
        timeToFirstResultMs = firstResultMs,
        totalRunTimeMs = totalMs,
        requestItemCount = reqItems,
        requestSerializedBytes = reqBytes,
        estimatedTokens = estTokens,
        actualTokens = actTokens,
        costUsd = costUsd,
        modelResolved = model,
        httpStatuses = httpStatuses.toList(),
        retriesCount = retries,
        splitsCount = splits,
        cancellationsCount = cancels,
        leaseRecoveriesCount = leaseRecs
    )
}
