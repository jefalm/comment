package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.ColumnInfo
import androidx.paging.PagingSource
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.data.local.entity.ProcessingStage
import kotlinx.coroutines.flow.Flow

data class AnalysisProcessingCounts(
    @ColumnInfo(name = "totalComments") val totalComments: Long,
    @ColumnInfo(name = "locallyRejected") val locallyRejected: Long,
    @ColumnInfo(name = "jevRejected") val jevRejected: Long,
    @ColumnInfo(name = "completed") val completed: Long,
    @ColumnInfo(name = "retrying") val retrying: Long,
    @ColumnInfo(name = "permanentlyFailed") val permanentlyFailed: Long,
    @ColumnInfo(name = "inProgress") val inProgress: Long
)

data class AnalysisDistributionCount(
    val value: String,
    val count: Long
)

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCommentsIgnoringDuplicates(comments: List<CommentEntity>): List<Long>

    @Query("SELECT * FROM comments WHERE localId = :localId")
    suspend fun getCommentById(localId: String): CommentEntity?

    @Query("SELECT * FROM comments WHERE localId IN (:localIds)")
    suspend fun getCommentsByIds(localIds: List<String>): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE importId = :importId")
    suspend fun getCommentsForImport(importId: String): List<CommentEntity>

    @Query(
        """
        SELECT * FROM comments
        WHERE importId = :importId
            AND processingConfigVersion = :configVersion
            AND processingState = 'IMPORTED'
            AND currentStage = 'INGESTION'
        ORDER BY localId
        LIMIT :limit
        """
    )
    suspend fun getImportedCommentsForAnalysis(
        importId: String,
        configVersion: Long,
        limit: Int
    ): List<CommentEntity>

    @Query(
        """
        SELECT * FROM comments
        WHERE importId = :importId
            AND processingConfigVersion = :configVersion
            AND processingState = :state
            AND currentStage = :stage
            AND (:state != 'RETRY_WAIT' OR nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY localId
        LIMIT :limit
        """
    )
    suspend fun getCommentsForAnalysisStage(
        importId: String,
        configVersion: Long,
        state: ProcessingState,
        stage: ProcessingStage,
        now: Long,
        limit: Int
    ): List<CommentEntity>

    @Query(
        """
        SELECT * FROM comments
        WHERE importId = :importId
            AND processingConfigVersion = :configVersion
            AND processingState IN ('READY_GLM', 'COMPLETED', 'JEV_REJECTED', 'MANUAL_REVIEW')
        ORDER BY localId
        """
    )
    suspend fun getCommentsForSynthesis(importId: String, configVersion: Long): List<CommentEntity>

    @Query(
        """
        SELECT
            COUNT(*) as totalComments,
            SUM(CASE WHEN processingState = 'LOCAL_REJECTED' THEN 1 ELSE 0 END) as locallyRejected,
            SUM(CASE WHEN processingState = 'JEV_REJECTED' THEN 1 ELSE 0 END) as jevRejected,
            SUM(CASE WHEN processingState = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
            SUM(CASE WHEN processingState = 'RETRY_WAIT' THEN 1 ELSE 0 END) as retrying,
            SUM(CASE WHEN processingState = 'FAILED_PERMANENT' THEN 1 ELSE 0 END) as permanentlyFailed,
            SUM(CASE WHEN processingState NOT IN ('LOCAL_REJECTED', 'JEV_REJECTED', 'COMPLETED', 'FAILED_PERMANENT') THEN 1 ELSE 0 END) as inProgress
        FROM comments
        WHERE importId = :importId AND processingConfigVersion = :configVersion
        """
    )
    suspend fun getAnalysisProcessingCounts(importId: String, configVersion: Long): AnalysisProcessingCounts?

    @Query(
        """
        SELECT category as value, COUNT(*) as count
        FROM comments
        WHERE importId = :importId AND processingConfigVersion = :configVersion AND category IS NOT NULL
        GROUP BY category
        ORDER BY count DESC
        """
    )
    suspend fun getCategoryDistribution(importId: String, configVersion: Long): List<AnalysisDistributionCount>

    @Query(
        """
        SELECT sentiment as value, COUNT(*) as count
        FROM comments
        WHERE importId = :importId AND processingConfigVersion = :configVersion AND sentiment IS NOT NULL
        GROUP BY sentiment
        ORDER BY count DESC
        """
    )
    suspend fun getSentimentDistribution(importId: String, configVersion: Long): List<AnalysisDistributionCount>

    @Query("SELECT DISTINCT processingConfigVersion FROM comments WHERE importId = :importId ORDER BY processingConfigVersion DESC")
    fun observeAnalysisConfigVersions(importId: String): Flow<List<Long>>

    @Query(
        """
        SELECT c.* FROM comments c
        LEFT JOIN analysis_results r ON c.localId = r.commentId AND r.processingConfigVersion = c.processingConfigVersion AND r.stage = :stage
        WHERE c.importId = :importId
            AND c.processingConfigVersion = :configVersion
            AND (
                (:stage = 'LOCAL_FILTER' AND c.processingState = 'IMPORTED' AND c.currentStage = 'INGESTION')
                OR (:stage = 'JEV_GATE' AND c.processingState = 'READY_JEV' AND c.currentStage = 'JEV_GATE')
                OR (:stage = 'GLM_ANALYSIS' AND c.processingState = 'READY_GLM' AND c.currentStage = 'GLM_ANALYSIS')
            )
        ORDER BY c.localId
        LIMIT :limit
        """
    )
    suspend fun getCommentsNeedingStage(
        importId: String,
        configVersion: Long,
        stage: com.example.commentanalyzer.data.local.entity.ResultStage,
        limit: Int
    ): List<CommentEntity>

    @Update
    suspend fun updateComments(comments: List<CommentEntity>): Int

    @Query(
        """
        SELECT * FROM comments
        WHERE importId = :importId
            AND (:query = '' OR originalText LIKE '%' || :query || '%' COLLATE NOCASE)
            AND (
                :relationship = -1
                OR (:relationship = 0 AND parentSourceCommentId IS NULL)
                OR (:relationship = 1 AND parentSourceCommentId IS NOT NULL)
            )
            AND (:hasCategories = 0 OR category IN (:categories))
            AND (:hasSentiments = 0 OR sentiment IN (:sentiments))
            AND (
                :stateFilter = 0
                OR (:stateFilter = 1 AND (category IS NULL OR sentiment IS NULL))
                OR (:stateFilter = 2 AND processingState IN ('MANUAL_REVIEW', 'LOCAL_REJECTED', 'JEV_REJECTED'))
                OR (:stateFilter = 3 AND processingState = 'RETRY_WAIT')
                OR (:stateFilter = 4 AND processingState = 'FAILED_PERMANENT')
            )
            AND (:hasRepliesOnly = 0 OR COALESCE(declaredReplyCount, 0) > 0 OR EXISTS (SELECT 1 FROM comments r WHERE r.parentSourceCommentId = comments.sourceCommentId))
        ORDER BY
            -- Threads view ordering: root comments first by sort, then their replies chronologically
            CASE
                WHEN :viewMode = 1 THEN
                    CASE WHEN parentSourceCommentId IS NULL THEN publishedAtEpochMillis ELSE (SELECT p.publishedAtEpochMillis FROM comments p WHERE p.sourceCommentId = comments.parentSourceCommentId) END
                ELSE 0
            END DESC,
            CASE
                WHEN :viewMode = 1 THEN
                    CASE WHEN parentSourceCommentId IS NULL THEN localId ELSE (SELECT p.localId FROM comments p WHERE p.sourceCommentId = comments.parentSourceCommentId) END
                ELSE ''
            END ASC,
            CASE
                WHEN :viewMode = 2 AND :sort = 0 THEN publishedAtEpochMillis
                WHEN :viewMode = 2 AND :sort = 1 THEN COALESCE(likeCount, 0)
                WHEN :viewMode = 2 AND :sort = 2 THEN COALESCE(declaredReplyCount, 0)
                WHEN :viewMode = 2 AND :sort = 3 THEN COALESCE(sentimentConfidence, 0.0)
                WHEN :viewMode = 2 AND :sort = 4 THEN (SELECT COUNT(*) FROM comments c2 WHERE c2.importId = :importId AND c2.parentSourceCommentId = comments.sourceCommentId)
                ELSE publishedAtEpochMillis
            END DESC,
            publishedAtEpochMillis DESC,
            localId ASC
        """
    )
    fun pagingComments(
        importId: String,
        query: String,
        relationship: Int,
        categories: List<String>,
        hasCategories: Int,
        sentiments: List<String>,
        hasSentiments: Int,
        stateFilter: Int,
        hasRepliesOnly: Int,
        viewMode: Int,
        sort: Int
    ): PagingSource<Int, CommentEntity>

    @Query("SELECT * FROM comments WHERE processingState = :state ORDER BY publishedAtEpochMillis DESC")
    suspend fun getCommentsByState(state: ProcessingState): List<CommentEntity>
}
