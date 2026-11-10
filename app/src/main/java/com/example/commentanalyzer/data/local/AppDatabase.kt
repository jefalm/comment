package com.example.commentanalyzer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE comments ADD COLUMN likeCount INTEGER")
                database.execSQL("ALTER TABLE comments ADD COLUMN declaredReplyCount INTEGER")
                database.execSQL("ALTER TABLE comments ADD COLUMN sourcePublishedAtRaw TEXT")
                database.execSQL("ALTER TABLE comments ADD COLUMN sourceUpdatedAtRaw TEXT")
                database.execSQL("ALTER TABLE comments ADD COLUMN publishedAtLocalDateTimeRaw TEXT")
                database.execSQL("ALTER TABLE imports ADD COLUMN videoId TEXT")
                database.execSQL("ALTER TABLE imports ADD COLUMN videoTitle TEXT")
                database.execSQL("ALTER TABLE imports ADD COLUMN channelTitle TEXT")
                database.execSQL("ALTER TABLE imports ADD COLUMN publishedAtRaw TEXT")
                database.execSQL("ALTER TABLE imports ADD COLUMN viewCount INTEGER")
                database.execSQL("ALTER TABLE imports ADD COLUMN likeCount INTEGER")
                database.execSQL("ALTER TABLE imports ADD COLUMN declaredCommentCount INTEGER")
                database.execSQL("ALTER TABLE imports ADD COLUMN retryable INTEGER")
                database.execSQL("ALTER TABLE imports ADD COLUMN unlabeledStatisticRaw TEXT")
                database.execSQL("ALTER TABLE imports ADD COLUMN publishedLocalDateTimeRaw TEXT")
                database.execSQL("ALTER TABLE imports ADD COLUMN topLevelCommentCount INTEGER")
                database.execSQL("ALTER TABLE imports ADD COLUMN declaredReplyCount INTEGER")
                database.execSQL("ALTER TABLE imports ADD COLUMN totalLikes INTEGER")
                database.execSQL("ALTER TABLE imports ADD COLUMN averageCommentsPerDay REAL")
                database.execSQL("ALTER TABLE imports ADD COLUMN averageLikesPerComment REAL")
                database.execSQL("ALTER TABLE imports ADD COLUMN sourceConfigVersion INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "comment-analyzer.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
