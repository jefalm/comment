package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.commentanalyzer.data.local.entity.ImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImport(importEntity: ImportEntity)

    @Update
    suspend fun updateImport(importEntity: ImportEntity)

    @Query("SELECT * FROM imports WHERE importId = :importId")
    suspend fun getImportById(importId: String): ImportEntity?

    @Query("SELECT * FROM imports WHERE importId = :importId")
    fun observeImportById(importId: String): Flow<ImportEntity?>

    @Query("SELECT * FROM imports WHERE sourceType = 'YOUTUBE_API' AND (status IN ('CANCELLED', 'PAUSED') OR (status = 'FAILED' AND retryable = 1)) ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestResumableYouTubeImport(): ImportEntity?
}
