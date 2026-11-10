package com.example.commentanalyzer.domain

import com.example.commentanalyzer.domain.fingerprint.FingerprintV1
import com.example.commentanalyzer.domain.fingerprint.FingerprintV2
import com.example.commentanalyzer.domain.normalize.AuthorNormalizer
import com.example.commentanalyzer.domain.normalize.TextNormalizer
import com.example.commentanalyzer.domain.normalize.TimestampNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizationAndFingerprintTest {

    @Test
    fun testTextNormalization() {
        val raw = "\uFEFF  Hello\r\n  World!  \t "
        val normalized = TextNormalizer.normalize(raw)
        assertEquals("Hello World!", normalized)
    }

    @Test
    fun testAuthorNormalization() {
        assertEquals("Alice", AuthorNormalizer.normalize("  Alice  "))
        assertEquals("[Unknown]", AuthorNormalizer.normalize(null))
        assertEquals("[Unknown]", AuthorNormalizer.normalize("   "))
    }

    @Test
    fun testTimestampNormalization() {
        val millis = TimestampNormalizer.parseToEpochMillis("2023-01-01T12:00:00Z")
        assertNotNull(millis)
        assertEquals(1672574400000L, millis)
    }

    @Test
    fun testFingerprintDeterministicHash() {
        val hash1 = FingerprintV1.compute(
            sourceScopeKey = "scope:youtube:video:v1",
            videoId = "v1",
            normalizedText = "great video",
            publishedAtEpochMillis = 1000L,
            authorDisplayName = "Alice"
        )
        val hash2 = FingerprintV1.compute(
            sourceScopeKey = "scope:youtube:video:v1",
            videoId = "v1",
            normalizedText = "great video",
            publishedAtEpochMillis = 1000L,
            authorDisplayName = "Alice"
        )
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 hex length
    }

    @Test
    fun txtFingerprintUsesRawLocalTimestampWithoutTimezoneConversion() {
        val first = FingerprintV2.compute("txt:run-1", null, "same text", "11/9/2026, 1:00:00 PM", "Alice")
        val same = FingerprintV2.compute("txt:run-1", null, "same text", "11/9/2026, 1:00:00 PM", "Alice")
        val differentTime = FingerprintV2.compute("txt:run-1", null, "same text", "11/9/2026, 2:00:00 PM", "Alice")

        assertEquals(first, same)
        assertTrue(first != differentTime)
        assertEquals(64, first.length)
    }
}
