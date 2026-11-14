package com.example.commentanalyzer.domain.filter

import com.example.commentanalyzer.domain.fingerprint.FingerprintV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalFilterQualityCorpusTest {

    data class CorpusItem(
        val id: String,
        val text: String,
        val category: String,
        val expectedOutcome: FilterOutcome
    )

    private val fixedCorpus = listOf(
        // 1-2 character valuable comments
        CorpusItem("c1", "ok", "short_valuable", FilterOutcome.PASS),
        CorpusItem("c2", "hi", "short_valuable", FilterOutcome.PASS),
        CorpusItem("c3", "!", "short_valuable", FilterOutcome.PASS),
        CorpusItem("c4", "?", "short_valuable", FilterOutcome.PASS),

        // Short questions
        CorpusItem("c5", "Why?", "short_question", FilterOutcome.PASS),
        CorpusItem("c6", "How so?", "short_question", FilterOutcome.PASS),

        // Criticism / disagreement / negative sentiment
        CorpusItem("c7", "I completely disagree with this conclusion.", "criticism_dissent", FilterOutcome.PASS),
        CorpusItem("c8", "This implementation has severe flaws and bugs.", "criticism_dissent", FilterOutcome.PASS),

        // Minority / unpopular viewpoints without viewpoint-based rejection
        CorpusItem("c9", "Actually, popular opinion on this is totally wrong.", "minority_viewpoint", FilterOutcome.PASS),
        CorpusItem("c10", "Most people miss the hidden downside here.", "minority_viewpoint", FilterOutcome.PASS),

        // Toxic-only abuse versus harsh substantive criticism
        CorpusItem("c11", "Your analysis misses the core performance bottleneck.", "harsh_substantive_criticism", FilterOutcome.PASS),
        CorpusItem("c15", "You have no idea what you are talking about, idiot.", "toxic_only_insult", FilterOutcome.PASS),

        // Spam / promotion
        CorpusItem("c16", "Check out my channel for free crypto https://spam.com https://spam.com/1 https://spam.com/2 https://spam.com/3", "obvious_spam_promotion", FilterOutcome.REVIEW),

        // Incoherent / meaningless trash
        CorpusItem("c12", "", "incoherent_trash", FilterOutcome.REJECT),
        CorpusItem("c13", "aaaaaaaaaaaa", "incoherent_trash", FilterOutcome.REJECT),

        // Link-heavy and ambiguous content
        CorpusItem("c14", "Check out https://a.com https://b.com https://c.com https://d.com", "link_heavy", FilterOutcome.REVIEW)
    )

    @Test
    fun testCorpusEvaluation() {
        for (item in fixedCorpus) {
            val result = LocalFilter.evaluate(item.text)
            assertEquals("Failed for corpus item ${item.id} ('${item.text}') [category=${item.category}]", item.expectedOutcome, result.outcome)
        }
    }

    @Test
    fun testExactDuplicateVsDistinctRecords() {
        val text = "Same substantive critique."
        val scope = "txt:run-corpus"
        val timestamp = "11/9/2026, 1:00:00 PM"
        val distinctTimestamp = "11/9/2026, 2:00:00 PM"

        val fp1 = FingerprintV2.compute(scope, null, text, timestamp, "Alice")
        val fp2 = FingerprintV2.compute(scope, null, text, timestamp, "Alice")
        val fpDistinct = FingerprintV2.compute(scope, null, text, distinctTimestamp, "Alice")

        // Exact duplicates produce identical fingerprints
        assertEquals(fp1, fp2)
        // Same text with distinct timestamps produce distinct records (fingerprints)
        assertNotEquals(fp1, fpDistinct)
    }
}
