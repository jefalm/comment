package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.RetryScheduleEntity

@Dao
interface RetryScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSchedule(schedule: RetryScheduleEntity)

    @Query("SELECT * FROM retry_schedule WHERE singletonKey = 'singleton'")
    suspend fun getSchedule(): RetryScheduleEntity?
}
