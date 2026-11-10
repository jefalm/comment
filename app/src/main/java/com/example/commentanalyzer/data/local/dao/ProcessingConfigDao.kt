package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity

@Dao
interface ProcessingConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ProcessingConfigEntity)

    @Query("SELECT * FROM processing_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConfig(): ProcessingConfigEntity?

    @Query("SELECT * FROM processing_configs WHERE configVersion = :version")
    suspend fun getConfigByVersion(version: Long): ProcessingConfigEntity?
}
