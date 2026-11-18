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
        fun fromJson(json: String): SignalRanker {
            val spec = Gson().fromJson(json, RankerSpec::class.java)
            require(spec != null) { "Invalid ranker spec JSON" }
            return SignalRanker(spec)
        }
    }
}
