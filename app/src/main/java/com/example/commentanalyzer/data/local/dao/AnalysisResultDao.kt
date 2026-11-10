package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.AnalysisResultEntity

@Dao
interface AnalysisResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: AnalysisResultEntity)

    @Query("SELECT * FROM analysis_results WHERE commentId = :commentId AND processingConfigVersion = :configVersion")
    suspend fun getResultsForComment(commentId: String, configVersion: Long): List<AnalysisResultEntity>
}
