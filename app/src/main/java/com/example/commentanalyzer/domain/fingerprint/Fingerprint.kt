package com.example.commentanalyzer.domain.fingerprint

import java.security.MessageDigest

object FingerprintV1 {
    const val VERSION = 1

    fun compute(
        sourceScopeKey: String,
        videoId: String?,
        normalizedText: String,
        publishedAtEpochMillis: Long?,
        authorDisplayName: String?
    ): String {
        val vid = videoId ?: ""
        val pub = publishedAtEpochMillis?.toString() ?: ""
        val author = authorDisplayName ?: ""

        val payload = "$sourceScopeKey\u001F$vid\u001F$normalizedText\u001F$pub\u001F$author"

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
