package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.ProcessingBatchEntity
import com.example.commentanalyzer.data.local.entity.ProcessingBatchItemEntity

@Dao
interface ProcessingBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: ProcessingBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatchItems(items: List<ProcessingBatchItemEntity>)

    @Query("SELECT * FROM processing_batches WHERE batchId = :batchId")
    suspend fun getBatchById(batchId: String): ProcessingBatchEntity?
}
