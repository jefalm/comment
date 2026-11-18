package com.example.commentanalyzer.domain.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

class SignalRankerGoldenTest {

    private lateinit var ranker: SignalRanker

    private val jsonContent = """
        {
          "version": 1,
          "calibrated": false,
          "description": "Un-calibrated baseline heuristic weights prior to Phase 0B offline regression",
          "intercept": 0.0,
          "features": {
            "logLikes": { "mean": 1.0, "std": 1.0, "coefficient": 0.35 },
            "logReplies": { "mean": 0.5, "std": 1.0, "coefficient": 0.40 },
            "wordCount": { "mean": 18.0, "std": 15.0, "coefficient": 0.25 },
            "questionMarkCount": { "mean": 0.2, "std": 0.5, "coefficient": 0.30 },
            "hasTimestamp": { "mean": 0.05, "std": 0.22, "coefficient": 0.20 },
            "hasPercentage": { "mean": 0.03, "std": 0.17, "coefficient": 0.15 },
            "hasCurrency": { "mean": 0.03, "std": 0.17, "coefficient": 0.15 },
            "isReply": { "mean": 0.3, "std": 0.46, "coefficient": 0.10 },
            "rareTokenScore": { "mean": 0.5, "std": 0.3, "coefficient": 0.30 },
            "hasDiscourseMarker": { "mean": 0.15, "std": 0.36, "coefficient": 0.25 },
            "nearDuplicateClusterSize": { "mean": 1.1, "std": 0.5, "coefficient": -0.35 }
          }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        ranker = SignalRanker.fromJson(jsonContent)
    }

    @Test
    fun testRankerMetadata() {
        assertEquals(1, ranker.spec.version)
        assertFalse(ranker.spec.calibrated)
    }

    @Test
    fun testTwentyFiveGoldenVectors() {
        data class GoldenVector(
            val id: Int,
            val features: ExtractedFeatures,
            val expectedScore: Double,
            val expectedTags: String
        )

        fun manualScore(f: ExtractedFeatures): Double {
            var total = 0.0
            total += 0.35 * ((f.logLikes - 1.0) / 1.0)
            total += 0.40 * ((f.logReplies - 0.5) / 1.0)
            total += 0.25 * ((f.wordCount - 18.0) / 15.0)
            total += 0.30 * ((f.questionMarkCount - 0.2) / 0.5)
            total += 0.20 * ((f.hasTimestamp - 0.05) / 0.22)
            total += 0.15 * ((f.hasPercentage - 0.03) / 0.17)
            total += 0.15 * ((f.hasCurrency - 0.03) / 0.17)
            total += 0.10 * ((f.isReply - 0.3) / 0.46)
            total += 0.30 * ((f.rareTokenScore - 0.5) / 0.3)
            total += 0.25 * ((f.hasDiscourseMarker - 0.15) / 0.36)
            total += -0.35 * ((f.nearDuplicateClusterSize - 1.1) / 0.5)
            return total
        }

        val vectors = (1..25).map { i ->
            val f = ExtractedFeatures(
                logLikes = i * 0.2,
                logReplies = i * 0.1,
                wordCount = i * 2.0,
                questionMarkCount = if (i % 3 == 0) 1.0 else 0.0,
                hasTimestamp = if (i % 5 == 0) 1.0 else 0.0,
                hasPercentage = if (i % 7 == 0) 1.0 else 0.0,
                hasCurrency = if (i % 11 == 0) 1.0 else 0.0,
                isReply = if (i % 2 == 0) 1.0 else 0.0,
                rareTokenScore = (i * 0.03).coerceAtMost(1.0),
                hasDiscourseMarker = if (i % 4 == 0) 1.0 else 0.0,
                nearDuplicateClusterSize = (i % 3 + 1).toDouble()
            )

            val tags = mutableListOf<String>()
            if (f.hasDiscourseMarker > 0.5) tags.add("debate")
            if (f.hasPercentage > 0.5 || f.hasCurrency > 0.5) tags.add("metrics")
            if (f.rareTokenScore >= 0.7) tags.add("novel")
            if (f.questionMarkCount > 0.0) tags.add("question")
            if (f.hasTimestamp > 0.5) tags.add("timestamp")

            GoldenVector(
                id = i,
                features = f,
                expectedScore = manualScore(f),
                expectedTags = tags.sorted().joinToString(",")
            )
        }

        for (v in vectors) {
            val res = ranker.score(v.features)
            assertEquals("Vector ${v.id} score mismatch", v.expectedScore, res.score, 1e-6)
            assertEquals("Vector ${v.id} tags mismatch", v.expectedTags, res.signalTags)
        }
    }
}
