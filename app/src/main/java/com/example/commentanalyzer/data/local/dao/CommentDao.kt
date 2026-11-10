package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ProcessingStage

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Query("SELECT * FROM comments WHERE localId = :localId")
    suspend fun getCommentById(localId: String): CommentEntity?

    @Query("UPDATE comments SET processingState = :newState, currentStage = :newStage, updatedAt = :updatedAt WHERE localId = :localId AND processingState = :expectedState")
    suspend fun updateStateCas(localId: String, expectedState: ProcessingState, newState: ProcessingState, newStage: ProcessingStage, updatedAt: Long): Int

    @Query("SELECT * FROM comments WHERE processingState = :state ORDER BY publishedAtEpochMillis DESC")
    suspend fun getCommentsByState(state: ProcessingState): List<CommentEntity>

    @Query("UPDATE comments SET leaseOwner = :owner, leaseExpiresAt = :expiresAt, updatedAt = :updatedAt WHERE localId IN (:localIds) AND (leaseExpiresAt IS NULL OR leaseExpiresAt < :currentTime)")
    suspend fun claimComments(localIds: List<String>, owner: String, expiresAt: Long, currentTime: Long, updatedAt: Long): Int

    @Query("UPDATE comments SET processingState = :fallbackState, leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :updatedAt WHERE leaseExpiresAt IS NOT NULL AND leaseExpiresAt < :currentTime AND processingState IN (:activeStates)")
    suspend fun reclaimExpiredLeases(currentTime: Long, fallbackState: ProcessingState, activeStates: List<ProcessingState>, updatedAt: Long): Int
}
