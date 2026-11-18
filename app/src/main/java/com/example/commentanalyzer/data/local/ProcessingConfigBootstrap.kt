package com.example.commentanalyzer.data.local

import androidx.room.withTransaction
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity

object ProcessingConfigBootstrap {
    const val CONFIG_VERSION_V1 = 1L
    const val CONFIG_VERSION_V2 = 2L
    const val CONFIG_VERSION_V3 = 3L
    const val CURRENT_CONFIG_VERSION = 3L
    const val JEV_MODEL = "jev-1.14.0"
    const val LEGACY_JEV_MODEL = "typesafe/jev-1.14"
    const val GLM_MODEL = "z-ai/glm-5.3-flash"
    const val JEV_REJECT_THRESHOLD = 0.3
    const val JEV_PASS_THRESHOLD = 0.7

    fun normalizeJevModel(model: String): String = when (model) {
        LEGACY_JEV_MODEL -> JEV_MODEL
        else -> model
    }

    fun createDefaultV2Config(createdAt: Long = System.currentTimeMillis()): ProcessingConfigEntity =
        ProcessingConfigEntity(
            configVersion = CONFIG_VERSION_V2,
            jevModelRequested = JEV_MODEL,
            jevRejectThreshold = JEV_REJECT_THRESHOLD,
            jevPassThreshold = JEV_PASS_THRESHOLD,
            jevAutoRejectNonEnglish = false,
            glmModelRequested = GLM_MODEL,
            enableJevGate = true,
            signalRankerVersion = 1,
            adapterJevEnabled = false,
            adapterGlmEnabled = false,
            adapterYoutubeEnabled = false,
            youtubeKeyCiphertext = null,
            openrouterKeyCiphertext = null,
            openrouterReferer = null,
            createdAt = createdAt,
            isActive = true
        )

    fun createDefaultV3Config(createdAt: Long = System.currentTimeMillis()): ProcessingConfigEntity =
        ProcessingConfigEntity(
            configVersion = CONFIG_VERSION_V3,
            jevModelRequested = JEV_MODEL,
            jevRejectThreshold = JEV_REJECT_THRESHOLD,
            jevPassThreshold = JEV_PASS_THRESHOLD,
            jevAutoRejectNonEnglish = false,
            glmModelRequested = GLM_MODEL,
            enableJevGate = false,
            signalRankerVersion = 2,
            adapterJevEnabled = false,
            adapterGlmEnabled = false,
            adapterYoutubeEnabled = false,
            youtubeKeyCiphertext = null,
            openrouterKeyCiphertext = null,
            openrouterReferer = null,
            createdAt = createdAt,
            isActive = true
        )

    suspend fun bootstrapActiveConfig(
        database: AppDatabase,
        targetVersion: Long = CURRENT_CONFIG_VERSION
    ): ProcessingConfigEntity =
        database.withTransaction {
            val dao = database.processingConfigDao()
            val existing = dao.getConfigByVersion(targetVersion)
            if (existing != null) {
                val needsModelNormalization = existing.jevModelRequested == LEGACY_JEV_MODEL
                val targetModel = if (needsModelNormalization) JEV_MODEL else existing.jevModelRequested
                if (!existing.isActive || needsModelNormalization) {
                    dao.deactivateAllExcept(targetVersion)
                    val updated = existing.copy(
                        isActive = true,
                        jevModelRequested = targetModel
                    )
                    dao.insertConfig(updated)
                    updated
                } else {
                    existing
                }
            } else {
                dao.deactivateAllExcept(targetVersion)
                val newConfig = when (targetVersion) {
                    CONFIG_VERSION_V3 -> createDefaultV3Config()
                    else -> createDefaultV2Config()
                }
                dao.insertConfig(newConfig)
                newConfig
            }
        }
}
