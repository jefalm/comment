package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.AnalysisResultEntity

@Dao
interface AnalysisResultDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResult(result: AnalysisResultEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResults(results: List<AnalysisResultEntity>)

    @Query("SELECT * FROM analysis_results WHERE commentId = :commentId AND processingConfigVersion = :configVersion")
    suspend fun getResultsForComment(commentId: String, configVersion: Long): List<AnalysisResultEntity>

    @Query("SELECT * FROM analysis_results WHERE batchId = :batchId ORDER BY commentId")
    suspend fun getResultsForBatch(batchId: String): List<AnalysisResultEntity>
}
