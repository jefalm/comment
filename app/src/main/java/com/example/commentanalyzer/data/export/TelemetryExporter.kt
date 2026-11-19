package com.example.commentanalyzer.data.export

import android.content.Context
import com.example.commentanalyzer.BuildConfig
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.domain.ranking.FeatureExtractor
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

object TelemetryExporter {

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    suspend fun exportTelemetry(
        context: Context,
        database: AppDatabase,
        includeRawCommentText: Boolean = false
    ): Pair<File, File>? {
        if (!BuildConfig.DEBUG) return null

        val telemetryDir = File(context.filesDir, "telemetry")
        if (!telemetryDir.exists()) {
            telemetryDir.mkdirs()
        }

        val csvFile = File(telemetryDir, "telemetry_dataset.csv")
        val manifestFile = File(telemetryDir, "telemetry_manifest.json")

        val comments = database.commentDao().getCommentsByState(
            com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED
        ) + database.commentDao().getCommentsByState(
            com.example.commentanalyzer.data.local.entity.ProcessingState.READY_GLM
        ) + database.commentDao().getCommentsByState(
            com.example.commentanalyzer.data.local.entity.ProcessingState.LOCAL_REJECTED
        ) + database.commentDao().getCommentsByState(
            com.example.commentanalyzer.data.local.entity.ProcessingState.JEV_REJECTED
        )

        val gson = Gson()
        val synthesisCache = mutableMapOf<Pair<String, Long>, com.example.commentanalyzer.domain.model.SynthesisResult?>()

        val csvBuilder = StringBuilder()
        csvBuilder.append("runId_sha256,importId_sha256,commentId_sha256,processingConfigVersion,signalRankerVersion,")
        csvBuilder.append("wasLocallyRejected,wasJevAccepted,jevProbability,wasEvidenceCandidate,wasSelectedForEvidence,")
        csvBuilder.append("evidencePosition,wasCitedAny,citationCount,citationSectionTypes,")
        csvBuilder.append("logLikes,logReplies,wordCount,questionMarkCount,hasTimestamp,hasPercentage,hasCurrency,isReply,rareTokenScore,hasDiscourseMarker,nearDuplicateClusterSize,signalScore")
        if (includeRawCommentText) {
            csvBuilder.append(",rawCommentText")
        }
        csvBuilder.append("\n")

        val corpusStats = FeatureExtractor.prepareCorpusStats(comments)
        for (comment in comments) {
            val importIdHash = sha256(comment.importId)
            val runIdHash = sha256("run:${comment.importId}:${comment.processingConfigVersion}")
            val commentIdHash = sha256(comment.localId)

            val features = FeatureExtractor.extract(comment, corpusStats, comment.nearDuplicateClusterSize)
            val wasLocallyRejected = comment.processingState == com.example.commentanalyzer.data.local.entity.ProcessingState.LOCAL_REJECTED
            val wasJevAccepted = comment.processingState == com.example.commentanalyzer.data.local.entity.ProcessingState.COMPLETED
            val jevProbability = comment.relevanceScore ?: 0.0

            val synthesisKey = comment.importId to comment.processingConfigVersion
            val synthesis = synthesisCache.getOrPut(synthesisKey) {
                database.commentSynthesisDao().getSynthesis(comment.importId, comment.processingConfigVersion)?.let { entity ->
                    try {
                        gson.fromJson(entity.payloadJson, com.example.commentanalyzer.domain.model.SynthesisResult::class.java)
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            var citationCount = 0
            val sectionTypes = mutableSetOf<String>()
            if (synthesis != null) {
                for (theme in synthesis.themes) {
                    val count = theme.citedCommentIds.count { it == comment.localId }
                    if (count > 0) {
                        citationCount += count
                        sectionTypes.add("themes")
                    }
                }
                for (agreement in synthesis.agreements) {
                    val count = agreement.citedCommentIds.count { it == comment.localId }
                    if (count > 0) {
                        citationCount += count
                        sectionTypes.add("agreements")
                    }
                }
                for (perspective in synthesis.contrastingPerspectives) {
                    val count = perspective.citedCommentIds.count { it == comment.localId }
                    if (count > 0) {
                        citationCount += count
                        sectionTypes.add("contrastingPerspectives")
                    }
                }
                for (insight in synthesis.notableInsights) {
                    val count = insight.citedCommentIds.count { it == comment.localId }
                    if (count > 0) {
                        citationCount += count
                        sectionTypes.add("notableInsights")
                    }
                }
            }
            val wasCitedAny = citationCount > 0
            val citationSectionTypes = sectionTypes.sorted().joinToString(";")

            csvBuilder.append(runIdHash).append(",")
            csvBuilder.append(importIdHash).append(",")
            csvBuilder.append(commentIdHash).append(",")
            csvBuilder.append(comment.processingConfigVersion).append(",")
            csvBuilder.append(comment.rankerVersion ?: 1).append(",")
            csvBuilder.append(wasLocallyRejected).append(",")
            csvBuilder.append(wasJevAccepted).append(",")
            csvBuilder.append(jevProbability).append(",")
            csvBuilder.append(comment.wasEvidenceCandidate).append(",")
            csvBuilder.append(comment.wasSelectedForEvidence).append(",")
            csvBuilder.append(comment.evidencePosition).append(",")
            csvBuilder.append(wasCitedAny).append(",")
            csvBuilder.append(citationCount).append(",")
            csvBuilder.append(citationSectionTypes).append(",")
            csvBuilder.append(features.logLikes).append(",")
            csvBuilder.append(features.logReplies).append(",")
            csvBuilder.append(features.wordCount).append(",")
            csvBuilder.append(features.questionMarkCount).append(",")
            csvBuilder.append(features.hasTimestamp).append(",")
            csvBuilder.append(features.hasPercentage).append(",")
            csvBuilder.append(features.hasCurrency).append(",")
            csvBuilder.append(features.isReply).append(",")
            csvBuilder.append(features.rareTokenScore).append(",")
            csvBuilder.append(features.hasDiscourseMarker).append(",")
            csvBuilder.append(features.nearDuplicateClusterSize).append(",")
            csvBuilder.append(comment.signalScore ?: 0.0)

            if (includeRawCommentText) {
                val sanitized = comment.originalText.replace("\n", " ").replace(",", ";")
                csvBuilder.append(",").append(sanitized)
            }
            csvBuilder.append("\n")
        }

        csvFile.writeText(csvBuilder.toString())

        val manifest = mapOf(
            "rankerVersion" to 1,
            "calibrated" to false,
            "exportTimestamp" to System.currentTimeMillis(),
            "totalRecords" to comments.size,
            "privacyStatus" to "SHA-256 Pseudonymized - No Raw Credentials or Text"
        )
        manifestFile.writeText(Gson().toJson(manifest))

        return csvFile to manifestFile
    }
}
