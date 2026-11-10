package com.example.commentanalyzer.domain.retry

import kotlin.math.min
import kotlin.random.Random

enum class RetryDisposition {
    RETRYABLE, PERMANENT, SPLIT_REQUIRED
}

data class RetryDecision(
    val disposition: RetryDisposition,
    val delayMillis: Long
)

object RetryPolicy {
    private const val BASE_DELAY_MILLIS = 30_000L
    private const val MAX_DELAY_MILLIS = 6 * 3600_1000L
    private const val MAX_ATTEMPTS = 6

    fun evaluate(statusCode: Int, attemptCount: Int, serverRetryAfterSeconds: Long? = null, random: Random = Random.Default): RetryDecision {
        if (attemptCount >= MAX_ATTEMPTS) {
            return RetryDecision(RetryDisposition.PERMANENT, 0L)
        }

        val disposition = when (statusCode) {
            401, 403, 400, 422 -> RetryDisposition.PERMANENT
            413 -> RetryDisposition.SPLIT_REQUIRED
            429, 500, 502, 503, 504 -> RetryDisposition.RETRYABLE
            else -> if (statusCode in 500..599) RetryDisposition.RETRYABLE else RetryDisposition.PERMANENT
        }

        if (disposition != RetryDisposition.RETRYABLE) {
            return RetryDecision(disposition, 0L)
        }

        val exponentialDelay = BASE_DELAY_MILLIS * (1 shl (attemptCount - 1).coerceAtLeast(0))
        val cappedDelay = min(exponentialDelay, MAX_DELAY_MILLIS.toLong())
        val jitteredDelay = random.nextLong(0L, cappedDelay + 1)

        val serverDelayMillis = (serverRetryAfterSeconds ?: 0L) * 1000L
        val finalDelay = maxOf(jitteredDelay, serverDelayMillis)

        return RetryDecision(RetryDisposition.RETRYABLE, finalDelay)
    }
}
