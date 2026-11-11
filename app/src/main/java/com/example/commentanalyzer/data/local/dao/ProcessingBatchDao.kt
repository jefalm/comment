package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchItemEntity

@Dao
interface ProcessingBatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: ProcessingBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatchItems(items: List<ProcessingBatchItemEntity>)

    @Query("SELECT * FROM processing_batches WHERE batchId = :batchId")
    suspend fun getBatchById(batchId: String): ProcessingBatchEntity?

    @Query("SELECT * FROM processing_batch_items WHERE batchId = :batchId ORDER BY ordinal")
    suspend fun getItemsForBatch(batchId: String): List<ProcessingBatchItemEntity>

    @Update
    suspend fun updateBatch(batch: ProcessingBatchEntity): Int

    @Update
    suspend fun updateBatchItems(items: List<ProcessingBatchItemEntity>): Int
}
