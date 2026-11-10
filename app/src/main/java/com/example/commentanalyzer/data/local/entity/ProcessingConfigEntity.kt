package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processing_configs")
data class ProcessingConfigEntity(
    @PrimaryKey
    val configVersion: Long,
    val jevModelRequested: String,
    val jevRejectThreshold: Double,
    val jevPassThreshold: Double,
    val jevAutoRejectNonEnglish: Boolean = false,
    val glmModelRequested: String,
    val adapterJevEnabled: Boolean = false,
    val adapterGlmEnabled: Boolean = false,
    val adapterYoutubeEnabled: Boolean = false,
    val youtubeKeyCiphertext: String?,
    val openrouterKeyCiphertext: String?,
    val openrouterReferer: String?,
    val createdAt: Long,
    val isActive: Boolean
)
