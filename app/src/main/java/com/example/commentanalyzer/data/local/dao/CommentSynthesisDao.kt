package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.CommentSynthesisEntity

@Dao
interface CommentSynthesisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynthesis(synthesis: CommentSynthesisEntity)

    @Query("SELECT * FROM comment_syntheses WHERE importId = :importId AND configVersion = :configVersion LIMIT 1")
    suspend fun getSynthesis(importId: String, configVersion: Long): CommentSynthesisEntity?
}
