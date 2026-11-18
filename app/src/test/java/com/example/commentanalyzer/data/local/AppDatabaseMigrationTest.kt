package com.example.commentanalyzer.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    @Test
    fun migrationTwoToThreeAddsAnalysisMetadataWithoutDroppingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("analysis-migration-test.db")
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(database: SupportSQLiteDatabase) {
                            database.execSQL(
                                "CREATE TABLE processing_batches (" +
                                    "batchId TEXT NOT NULL PRIMARY KEY, stage TEXT NOT NULL, " +
                                    "status TEXT NOT NULL, nextAttemptAt INTEGER, parentBatchId TEXT)"
                            )
                            database.execSQL(
                                "CREATE TABLE analysis_results (" +
                                    "resultId TEXT NOT NULL PRIMARY KEY, commentId TEXT NOT NULL, " +
                                    "processingConfigVersion INTEGER NOT NULL, stage TEXT NOT NULL, " +
                                    "createdAt INTEGER NOT NULL)"
                            )
                            database.execSQL(
                                "INSERT INTO processing_batches(batchId, stage, status) " +
                                    "VALUES('existing-batch', 'JEV_GATE', 'SUCCEEDED')"
                            )
                            database.execSQL(
                                "INSERT INTO analysis_results(resultId, commentId, processingConfigVersion, stage, createdAt) " +
                                    "VALUES('existing-result', 'comment-1', 1, 'JEV_GATE', 10)"
                            )
                        }

                        override fun onUpgrade(
                            database: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )

        val database = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_2_3.migrate(database)

            database.query(
                "SELECT batchId, importId, startedAt, completedAt, failedAt, responseSchemaVersion " +
                    "FROM processing_batches"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("existing-batch", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
            }
            database.query("SELECT resultId, batchId, confidence FROM analysis_results").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("existing-result", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN (" +
                    "'index_processing_batches_importId_stage_status_nextAttemptAt', " +
                    "'index_analysis_results_batchId_commentId')"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase("analysis-migration-test.db")
        }
    }

    @Test
    fun migrationThreeToFourAddsJevConfidenceColumnsWithoutDroppingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("analysis-migration-3-4-test.db")
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(database: SupportSQLiteDatabase) {
                            database.execSQL(
                                "CREATE TABLE comments (" +
                                    "localId TEXT NOT NULL PRIMARY KEY, category TEXT, sentiment TEXT)"
                            )
                            database.execSQL(
                                "CREATE TABLE analysis_results (" +
                                    "resultId TEXT NOT NULL PRIMARY KEY, commentId TEXT NOT NULL, " +
                                    "category TEXT, sentiment TEXT)"
                            )
                            database.execSQL(
                                "INSERT INTO comments(localId, category, sentiment) " +
                                    "VALUES('comment-1', 'praise', 'positive')"
                            )
                            database.execSQL(
                                "INSERT INTO analysis_results(resultId, commentId, category, sentiment) " +
                                    "VALUES('res-1', 'comment-1', 'praise', 'positive')"
                            )
                        }

                        override fun onUpgrade(
                            database: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )

        val database = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_3_4.migrate(database)

            database.query(
                "SELECT localId, category, sentiment, categoryConfidence, sentimentConfidence FROM comments"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("comment-1", cursor.getString(0))
                assertEquals("praise", cursor.getString(1))
                assertEquals("positive", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
            database.query(
                "SELECT resultId, commentId, category, sentiment, categoryConfidence, sentimentConfidence FROM analysis_results"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("res-1", cursor.getString(0))
                assertEquals("comment-1", cursor.getString(1))
                assertEquals("praise", cursor.getString(2))
                assertEquals("positive", cursor.getString(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
            }
        } finally {
            helper.close()
            context.deleteDatabase("analysis-migration-3-4-test.db")
        }
    }

    @Test
    fun migrationFiveToSixAddsSignalRankerColumnsWithoutDroppingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("analysis-migration-5-6-test.db")
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(database: SupportSQLiteDatabase) {
                            database.execSQL(
                                "CREATE TABLE processing_configs (" +
                                    "configVersion INTEGER NOT NULL PRIMARY KEY, jevModelRequested TEXT NOT NULL, " +
                                    "jevRejectThreshold REAL NOT NULL, jevPassThreshold REAL NOT NULL, " +
                                    "glmModelRequested TEXT NOT NULL, createdAt INTEGER NOT NULL, isActive INTEGER NOT NULL)"
                            )
                            database.execSQL(
                                "CREATE TABLE comments (" +
                                    "localId TEXT NOT NULL PRIMARY KEY, originalText TEXT NOT NULL)"
                            )
                            database.execSQL(
                                "INSERT INTO processing_configs(configVersion, jevModelRequested, jevRejectThreshold, jevPassThreshold, glmModelRequested, createdAt, isActive) " +
                                    "VALUES(2, 'jev-1.14.0', 0.3, 0.7, 'glm-5.3', 100, 1)"
                            )
                            database.execSQL(
                                "INSERT INTO comments(localId, originalText) VALUES('comment-1', 'Hello world')"
                            )
                        }

                        override fun onUpgrade(
                            database: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )

        val database = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_5_6.migrate(database)

            database.query("SELECT configVersion, enableJevGate, signalRankerVersion FROM processing_configs").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2L, cursor.getLong(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
            }

            database.query(
                "SELECT localId, signalScore, rankerVersion, signalTags, wasEvidenceCandidate, wasSelectedForEvidence, evidencePosition, nearDuplicateClusterSize FROM comments"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("comment-1", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertEquals(0, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
                assertEquals(-1, cursor.getInt(6))
                assertEquals(1, cursor.getInt(7))
            }
        } finally {
            helper.close()
            context.deleteDatabase("analysis-migration-5-6-test.db")
        }
    }
}
