package com.example.commentanalyzer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.commentanalyzer.data.local.converter.Converters
import com.example.commentanalyzer.data.local.dao.*
import com.example.commentanalyzer.data.local.entity.*

@Database(
    entities = [
        CommentEntity::class,
        ImportEntity::class,
        ProcessingBatchEntity::class,
        ProcessingBatchItemEntity::class,
        AnalysisResultEntity::class,
        ProcessingConfigEntity::class,
        RetryScheduleEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commentDao(): CommentDao
    abstract fun importDao(): ImportDao
    abstract fun processingBatchDao(): ProcessingBatchDao
    abstract fun analysisResultDao(): AnalysisResultDao
    abstract fun processingConfigDao(): ProcessingConfigDao
    abstract fun retryScheduleDao(): RetryScheduleDao
}
