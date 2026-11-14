package com.example.commentanalyzer.data.local

import androidx.room.withTransaction
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity

object ProcessingConfigBootstrap {
    const val CURRENT_CONFIG_VERSION = 2L
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
            configVersion = CURRENT_CONFIG_VERSION,
            jevModelRequested = JEV_MODEL,
            jevRejectThreshold = JEV_REJECT_THRESHOLD,
            jevPassThreshold = JEV_PASS_THRESHOLD,
            jevAutoRejectNonEnglish = false,
            glmModelRequested = GLM_MODEL,
            adapterJevEnabled = false,
            adapterGlmEnabled = false,
            adapterYoutubeEnabled = false,
            youtubeKeyCiphertext = null,
            openrouterKeyCiphertext = null,
            openrouterReferer = null,
            createdAt = createdAt,
            isActive = true
        )

    suspend fun bootstrapActiveConfig(database: AppDatabase): ProcessingConfigEntity =
        database.withTransaction {
            val dao = database.processingConfigDao()
            val existingV2 = dao.getConfigByVersion(CURRENT_CONFIG_VERSION)
            if (existingV2 != null) {
                val needsModelNormalization = existingV2.jevModelRequested == LEGACY_JEV_MODEL
                val targetModel = if (needsModelNormalization) JEV_MODEL else existingV2.jevModelRequested
                if (!existingV2.isActive || needsModelNormalization) {
                    dao.deactivateAllExcept(CURRENT_CONFIG_VERSION)
                    val updated = existingV2.copy(
                        isActive = true,
                        jevModelRequested = targetModel
                    )
                    dao.insertConfig(updated)
                    updated
                } else {
                    existingV2
                }
            } else {
                dao.deactivateAllExcept(CURRENT_CONFIG_VERSION)
                val newConfig = createDefaultV2Config()
                dao.insertConfig(newConfig)
                newConfig
            }
        }
}
