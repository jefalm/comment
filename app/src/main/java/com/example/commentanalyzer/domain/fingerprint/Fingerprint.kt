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

object FingerprintV2 {
    const val VERSION = 2

    fun compute(
        sourceScopeKey: String,
        videoId: String?,
        normalizedText: String,
        publishedAtRaw: String?,
        authorDisplayName: String?
    ): String {
        val payload = listOf(
            sourceScopeKey,
            videoId.orEmpty(),
            normalizedText,
            publishedAtRaw.orEmpty(),
            authorDisplayName.orEmpty()
        ).joinToString("\u001F")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
