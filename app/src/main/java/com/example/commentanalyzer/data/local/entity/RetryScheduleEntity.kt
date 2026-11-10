package com.example.commentanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "retry_schedule")
data class RetryScheduleEntity(
    @PrimaryKey
    val singletonKey: String = "singleton",
    val nextWakeupEpochMillis: Long?,
    val updatedAt: Long
)
