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
}
