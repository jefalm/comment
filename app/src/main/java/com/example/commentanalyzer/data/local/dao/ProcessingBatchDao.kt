package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.ColumnInfo
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchItemEntity
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import kotlinx.coroutines.flow.Flow

data class AnalysisUsageTotals(
    @ColumnInfo(name = "inputTokens") val inputTokens: Long?,
    @ColumnInfo(name = "outputTokens") val outputTokens: Long?,
    @ColumnInfo(name = "costUsd") val costUsd: Double?
)

@Dao
interface ProcessingBatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: ProcessingBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatchItems(items: List<ProcessingBatchItemEntity>)

    @Query("SELECT * FROM processing_batches WHERE batchId = :batchId")
    suspend fun getBatchById(batchId: String): ProcessingBatchEntity?

    @Query(
        """
        SELECT * FROM processing_batches
        WHERE importId = :importId
            AND processingConfigVersion = :configVersion
            AND stage = :stage
            AND (
                status = 'PLANNED'
                OR (status = 'RETRY_WAIT' AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now))
                OR (status = 'IN_FLIGHT' AND (leaseExpiresAt IS NULL OR leaseExpiresAt <= :now))
            )
        ORDER BY createdAt, batchId
        LIMIT :limit
        """
    )
    suspend fun getRunnableBatches(
        importId: String,
        configVersion: Long,
        stage: ProcessingStage,
        now: Long,
        limit: Int
    ): List<ProcessingBatchEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM processing_batches
            WHERE importId = :importId
                AND processingConfigVersion = :configVersion
                AND stage IN ('JEV_GATE', 'GLM_ANALYSIS')
                AND (
                    status = 'RETRY_WAIT'
                    OR (status = 'IN_FLIGHT' AND (leaseExpiresAt IS NULL OR leaseExpiresAt > :now))
                )
        )
        """
    )
    suspend fun hasPendingAnalysisBatches(
        importId: String,
        configVersion: Long,
        now: Long
    ): Boolean

    @Query(
        """
        SELECT SUM(inputTokens) AS inputTokens,
            SUM(outputTokens) AS outputTokens,
            SUM(costUsd) AS costUsd
        FROM processing_batches
        WHERE importId = :importId
            AND processingConfigVersion = :configVersion
            AND status = 'SUCCEEDED'
            AND stage IN ('JEV_GATE', 'GLM_ANALYSIS')
        """
    )
    fun observeAnalysisUsageTotals(importId: String, configVersion: Long): Flow<AnalysisUsageTotals>

    @Query("SELECT * FROM processing_batch_items WHERE batchId = :batchId ORDER BY ordinal")
    suspend fun getItemsForBatch(batchId: String): List<ProcessingBatchItemEntity>

    @Update
    suspend fun updateBatch(batch: ProcessingBatchEntity): Int

    @Update
    suspend fun updateBatchItems(items: List<ProcessingBatchItemEntity>): Int
}
