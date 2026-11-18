package com.example.commentanalyzer.domain.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun ExtractedFeatures(
    logLikes: Number,
    logReplies: Number,
    wordCount: Number,
    questionMarkCount: Number,
    hasTimestamp: Number,
    hasPercentage: Number,
    hasCurrency: Number,
    isReply: Number,
    rareTokenScore: Number,
    hasDiscourseMarker: Number,
    nearDuplicateClusterSize: Number,
    charCount: Number = 0.0,
    sentenceCount: Number = 0.0,
    threadSize: Number = 0.0,
    uniqueTokenRatio: Number = 0.0
): ExtractedFeatures = ExtractedFeatures(
    logLikes = logLikes.toDouble(),
    logReplies = logReplies.toDouble(),
    wordCount = wordCount.toDouble(),
    questionMarkCount = questionMarkCount.toDouble(),
    hasTimestamp = hasTimestamp.toDouble(),
    hasPercentage = hasPercentage.toDouble(),
    hasCurrency = hasCurrency.toDouble(),
    isReply = isReply.toDouble(),
    rareTokenScore = rareTokenScore.toDouble(),
    hasDiscourseMarker = hasDiscourseMarker.toDouble(),
    nearDuplicateClusterSize = nearDuplicateClusterSize.toDouble(),
    charCount = charCount.toDouble(),
    sentenceCount = sentenceCount.toDouble(),
    threadSize = threadSize.toDouble(),
    uniqueTokenRatio = uniqueTokenRatio.toDouble()
)

class SignalRankerV2GoldenTest {
    private lateinit var ranker: SignalRanker

    private val jsonContent = """
        {
          "version": 2,
          "calibrated": true,
          "description": "Phase 0B exposure-conditioned citation ranker; L2 logistic regression on 1,080 valid exposed rows across 36 imports; import-grouped validation; provisional until prospective calibration-mode telemetry.",
          "intercept": 0.18740307493388506,
          "features": {
            "logLikes": {
              "mean": 1.0628667407407408,
              "std": 1.6269442951390571,
              "coefficient": 0.08370804488896154
            },
            "logReplies": {
              "mean": 0.3363988953703703,
              "std": 0.8398665977495049,
              "coefficient": 0.0660536160966951
            },
            "wordCount": {
              "mean": 21.344444444444445,
              "std": 47.2177370419001,
              "coefficient": 0.04546640907700602
            },
            "questionMarkCount": {
              "mean": 0.23055555555555557,
              "std": 0.645204293419467,
              "coefficient": 0.02557169578637152
            }
          }
        }
    """.trimIndent()

    @Before
    fun setUp() { ranker = SignalRanker.fromJson(jsonContent) }

    @Test
    fun metadataAndFeatureSetAreFrozen() {
        assertEquals(2, ranker.spec.version)
        assertTrue(ranker.spec.calibrated)
        assertEquals(setOf("logLikes", "logReplies", "wordCount", "questionMarkCount"), ranker.spec.features.keys)
    }

    @Test
    fun twentyFiveGoldenVectorsMatch() {
        data class GoldenVector(val id: Int, val f: ExtractedFeatures, val expectedScore: Double, val expectedTags: String)
        val vectors = listOf(
            GoldenVector(1, ExtractedFeatures(
                logLikes = 0, logReplies = 0.17, wordCount = 0,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.04, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 2, charCount = 0,
                sentenceCount = 2, threadSize = 2, uniqueTokenRatio = 0.3
            ), 0.0899400428803679, ""),
            GoldenVector(2, ExtractedFeatures(
                logLikes = 0.31, logReplies = 0.34, wordCount = 3,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.08, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 3, charCount = 25,
                sentenceCount = 3, threadSize = 3, uniqueTokenRatio = 0.4
            ), 0.122148724072823, ""),
            GoldenVector(3, ExtractedFeatures(
                logLikes = 0.62, logReplies = 0.51, wordCount = 8,
                questionMarkCount = 1, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.12, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 4, charCount = 80,
                sentenceCount = 4, threadSize = 4, uniqueTokenRatio = 0.5
            ), 0.195916711036012, "question"),
            GoldenVector(4, ExtractedFeatures(
                logLikes = 0.93, logReplies = 0.68, wordCount = 15,
                questionMarkCount = 2, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.16, hasDiscourseMarker = 1,
                nearDuplicateClusterSize = 1, charCount = 200,
                sentenceCount = 5, threadSize = 5, uniqueTokenRatio = 0.6
            ), 0.271610517063196, "debate,question"),
            GoldenVector(5, ExtractedFeatures(
                logLikes = 1.24, logReplies = 0.85, wordCount = 24,
                questionMarkCount = 0, hasTimestamp = 1,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.2, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 2, charCount = 900,
                sentenceCount = 6, threadSize = 6, uniqueTokenRatio = 0.7
            ), 0.230329682034159, "timestamp"),
            GoldenVector(6, ExtractedFeatures(
                logLikes = 1.55, logReplies = 1.02, wordCount = 40,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.24, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 3, charCount = 0,
                sentenceCount = 1, threadSize = 7, uniqueTokenRatio = 0.8
            ), 0.275056187142583, ""),
            GoldenVector(7, ExtractedFeatures(
                logLikes = 1.86, logReplies = 1.19, wordCount = 75,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 1, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.28, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 4, charCount = 25,
                sentenceCount = 2, threadSize = 8, uniqueTokenRatio = 0.9
            ), 0.338077973358964, "metrics"),
            GoldenVector(8, ExtractedFeatures(
                logLikes = 2.17, logReplies = 1.36, wordCount = 150,
                questionMarkCount = 1, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.32, hasDiscourseMarker = 1,
                nearDuplicateClusterSize = 1, charCount = 80,
                sentenceCount = 3, threadSize = 1, uniqueTokenRatio = 0.2
            ), 0.479249627561992, "debate,question"),
            GoldenVector(9, ExtractedFeatures(
                logLikes = 2.48, logReplies = 0, wordCount = 300,
                questionMarkCount = 2, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.36, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 2, charCount = 200,
                sentenceCount = 4, threadSize = 2, uniqueTokenRatio = 0.3
            ), 0.572308443020436, "question"),
            GoldenVector(10, ExtractedFeatures(
                logLikes = 2.79, logReplies = 0.17, wordCount = 0,
                questionMarkCount = 0, hasTimestamp = 1,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.4, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 3, charCount = 900,
                sentenceCount = 5, threadSize = 3, uniqueTokenRatio = 0.4
            ), 0.233488562604112, "timestamp"),
            GoldenVector(11, ExtractedFeatures(
                logLikes = 3.1, logReplies = 0.34, wordCount = 3,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 1, isReply = 0,
                rareTokenScore = 0.44, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 4, charCount = 0,
                sentenceCount = 6, threadSize = 4, uniqueTokenRatio = 0.5
            ), 0.265697243796567, "metrics"),
            GoldenVector(12, ExtractedFeatures(
                logLikes = 3.41, logReplies = 0.51, wordCount = 8,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.48, hasDiscourseMarker = 1,
                nearDuplicateClusterSize = 1, charCount = 25,
                sentenceCount = 1, threadSize = 5, uniqueTokenRatio = 0.6
            ), 0.299831744053017, "debate"),
            GoldenVector(13, ExtractedFeatures(
                logLikes = 3.72, logReplies = 0.68, wordCount = 15,
                questionMarkCount = 1, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.52, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 2, charCount = 80,
                sentenceCount = 2, threadSize = 6, uniqueTokenRatio = 0.7
            ), 0.375525550080202, "question"),
            GoldenVector(14, ExtractedFeatures(
                logLikes = 4.03, logReplies = 0.85, wordCount = 24,
                questionMarkCount = 2, hasTimestamp = 0,
                hasPercentage = 1, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.56, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 3, charCount = 200,
                sentenceCount = 3, threadSize = 7, uniqueTokenRatio = 0.8
            ), 0.453145175171382, "metrics,question"),
            GoldenVector(15, ExtractedFeatures(
                logLikes = 4.34, logReplies = 1.02, wordCount = 40,
                questionMarkCount = 0, hasTimestamp = 1,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.6, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 4, charCount = 900,
                sentenceCount = 4, threadSize = 8, uniqueTokenRatio = 0.9
            ), 0.418604706866328, "timestamp"),
            GoldenVector(16, ExtractedFeatures(
                logLikes = 4.65, logReplies = 1.19, wordCount = 75,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.64, hasDiscourseMarker = 1,
                nearDuplicateClusterSize = 1, charCount = 0,
                sentenceCount = 5, threadSize = 1, uniqueTokenRatio = 0.2
            ), 0.481626493082709, "debate"),
            GoldenVector(17, ExtractedFeatures(
                logLikes = 4.96, logReplies = 1.36, wordCount = 150,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.68, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 2, charCount = 25,
                sentenceCount = 6, threadSize = 2, uniqueTokenRatio = 0.3
            ), 0.583164660578997, ""),
            GoldenVector(18, ExtractedFeatures(
                logLikes = 5.27, logReplies = 0, wordCount = 300,
                questionMarkCount = 1, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.72, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 3, charCount = 80,
                sentenceCount = 1, threadSize = 3, uniqueTokenRatio = 0.4
            ), 0.676223476037442, "novel,question"),
            GoldenVector(19, ExtractedFeatures(
                logLikes = 5.58, logReplies = 0.17, wordCount = 0,
                questionMarkCount = 2, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.76, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 4, charCount = 200,
                sentenceCount = 2, threadSize = 4, uniqueTokenRatio = 0.5
            ), 0.456304055741336, "novel,question"),
            GoldenVector(20, ExtractedFeatures(
                logLikes = 5.89, logReplies = 0.34, wordCount = 3,
                questionMarkCount = 0, hasTimestamp = 1,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.8, hasDiscourseMarker = 1,
                nearDuplicateClusterSize = 1, charCount = 900,
                sentenceCount = 3, threadSize = 5, uniqueTokenRatio = 0.6
            ), 0.409245763520312, "debate,novel,timestamp"),
            GoldenVector(21, ExtractedFeatures(
                logLikes = 6.2, logReplies = 0.51, wordCount = 8,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 1, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.84, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 2, charCount = 0,
                sentenceCount = 4, threadSize = 6, uniqueTokenRatio = 0.7
            ), 0.443380263776762, "metrics,novel"),
            GoldenVector(22, ExtractedFeatures(
                logLikes = 6.51, logReplies = 0.68, wordCount = 15,
                questionMarkCount = 0, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 1, isReply = 1,
                rareTokenScore = 0.88, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 3, charCount = 25,
                sentenceCount = 5, threadSize = 7, uniqueTokenRatio = 0.8
            ), 0.479440583097207, "metrics,novel"),
            GoldenVector(23, ExtractedFeatures(
                logLikes = 6.82, logReplies = 0.85, wordCount = 24,
                questionMarkCount = 1, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 0.92, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 4, charCount = 80,
                sentenceCount = 6, threadSize = 8, uniqueTokenRatio = 0.9
            ), 0.557060208188387, "novel,question"),
            GoldenVector(24, ExtractedFeatures(
                logLikes = 7.13, logReplies = 1.02, wordCount = 40,
                questionMarkCount = 2, hasTimestamp = 0,
                hasPercentage = 0, hasCurrency = 0, isReply = 1,
                rareTokenScore = 0.96, hasDiscourseMarker = 1,
                nearDuplicateClusterSize = 1, charCount = 200,
                sentenceCount = 1, threadSize = 1, uniqueTokenRatio = 0.2
            ), 0.641420200003551, "debate,novel,question"),
            GoldenVector(25, ExtractedFeatures(
                logLikes = 7.44, logReplies = 1.19, wordCount = 75,
                questionMarkCount = 0, hasTimestamp = 1,
                hasPercentage = 0, hasCurrency = 0, isReply = 0,
                rareTokenScore = 1, hasDiscourseMarker = 0,
                nearDuplicateClusterSize = 2, charCount = 900,
                sentenceCount = 2, threadSize = 2, uniqueTokenRatio = 0.3
            ), 0.625175012806453, "novel,timestamp")
        )
        vectors.forEach { v ->
            val result = ranker.score(v.f)
            assertEquals("Vector ${v.id} score mismatch", v.expectedScore, result.score, 1e-9)
            assertEquals("Vector ${v.id} tag mismatch", v.expectedTags, result.signalTags)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedVersionFailsClosed() {
        SignalRanker.forVersion(999)
    }

    @Test
    fun versionSelectionLoadsExpectedVersions() {
        val v1 = SignalRanker.forVersion(1)
        assertEquals(1, v1.spec.version)
        val v2 = SignalRanker.forVersion(2)
        assertEquals(2, v2.spec.version)
    }
}
