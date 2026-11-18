package com.example.commentanalyzer.domain.ranking

import com.google.gson.Gson

data class FeatureSpec(
    val mean: Double,
    val std: Double,
    val coefficient: Double
)

data class RankerSpec(
    val version: Int,
    val calibrated: Boolean,
    val description: String,
    val intercept: Double,
    val features: Map<String, FeatureSpec>
)

data class RankerResult(
    val score: Double,
    val rankerVersion: Int,
    val signalTags: String
)

class SignalRanker(val spec: RankerSpec) {

    fun score(features: ExtractedFeatures): RankerResult {
        val featureMap = features.toMap()
        var total = spec.intercept
        for ((name, fSpec) in spec.features) {
            val rawVal = featureMap[name] ?: 0.0
            val stdVal = if (fSpec.std > 0.0) fSpec.std else 1.0
            val normalized = (rawVal - fSpec.mean) / stdVal
            total += fSpec.coefficient * normalized
        }

        val tags = mutableListOf<String>()
        if (features.hasDiscourseMarker > 0.5) tags.add("debate")
        if (features.hasPercentage > 0.5 || features.hasCurrency > 0.5) tags.add("metrics")
        if (features.rareTokenScore >= 0.7) tags.add("novel")
        if (features.questionMarkCount > 0.0) tags.add("question")
        if (features.hasTimestamp > 0.5) tags.add("timestamp")

        val sortedTags = tags.sorted().joinToString(",")

        return RankerResult(
            score = total,
            rankerVersion = spec.version,
            signalTags = sortedTags
        )
    }

    companion object {
        const val DEFAULT_VERSION = 1

        private val V1_SPEC_JSON = """
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

        private val V2_SPEC_JSON = """
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

        fun getSpecJson(version: Int): String = when (version) {
            1 -> V1_SPEC_JSON
            2 -> V2_SPEC_JSON
            else -> throw IllegalArgumentException("Unsupported signal ranker version: $version")
        }

        fun forVersion(version: Int = DEFAULT_VERSION): SignalRanker {
            return fromJson(getSpecJson(version))
        }

        fun fromJson(json: String): SignalRanker {
            val spec = Gson().fromJson(json, RankerSpec::class.java)
            require(spec != null) { "Invalid ranker spec JSON" }
            return SignalRanker(spec)
        }
    }
}
