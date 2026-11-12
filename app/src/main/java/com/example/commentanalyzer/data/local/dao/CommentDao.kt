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
            SUM(CASE WHEN processingState = 'COMPLETED' AND EXISTS (SELECT 1 FROM analysis_results r WHERE r.commentId = comments.localId AND r.processingConfigVersion = comments.processingConfigVersion AND r.stage = 'GLM_ANALYSIS') THEN 1 ELSE 0 END) as completed,
            SUM(CASE WHEN processingState = 'RETRY_WAIT' THEN 1 ELSE 0 END) as retrying,
            SUM(CASE WHEN processingState = 'FAILED_PERMANENT' THEN 1 ELSE 0 END) as permanentlyFailed,
            SUM(CASE WHEN processingState IN ('JEV_IN_PROGRESS', 'GLM_IN_PROGRESS') THEN 1 ELSE 0 END) as inProgress
        FROM comments
        WHERE importId = :importId AND processingConfigVersion = :configVersion
        """
    )
    fun observeAnalysisProcessingCounts(importId: String, configVersion: Long): Flow<AnalysisProcessingCounts>

    @Query(
        """
        SELECT category as value, COUNT(*) as count
        FROM comments c
        WHERE c.importId = :importId
            AND c.processingConfigVersion = :configVersion
            AND c.processingState = 'COMPLETED'
            AND c.category IS NOT NULL
            AND c.category IN ('praise', 'criticism', 'feedback', 'question', 'spam', 'other')
            AND EXISTS (
                SELECT 1 FROM analysis_results r
                WHERE r.commentId = c.localId
                    AND r.processingConfigVersion = c.processingConfigVersion
                    AND r.stage = 'GLM_ANALYSIS'
            )
        GROUP BY category
        ORDER BY count DESC
        """
    )
    fun observeAnalysisCategoryCounts(importId: String, configVersion: Long): Flow<List<AnalysisDistributionCount>>

    @Query(
        """
        SELECT sentiment as value, COUNT(*) as count
        FROM comments c
        WHERE c.importId = :importId
            AND c.processingConfigVersion = :configVersion
            AND c.processingState = 'COMPLETED'
            AND c.sentiment IS NOT NULL
            AND c.sentiment IN ('positive', 'negative', 'neutral')
            AND EXISTS (
                SELECT 1 FROM analysis_results r
                WHERE r.commentId = c.localId
                    AND r.processingConfigVersion = c.processingConfigVersion
                    AND r.stage = 'GLM_ANALYSIS'
            )
        GROUP BY sentiment
        ORDER BY count DESC
        """
    )
    fun observeAnalysisSentimentCounts(importId: String, configVersion: Long): Flow<List<AnalysisDistributionCount>>

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
            AND (
                :query = ''
                OR localId = :query
                OR originalText LIKE '%' || :query || '%' COLLATE NOCASE
            )
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
            -- Threads view ordering:
            -- 1) Thread anchor: if root, own publishedAtEpochMillis; if reply, parent's publishedAtEpochMillis (or own if orphan)
            CASE
                WHEN :viewMode = 1 THEN
                    CASE
                        WHEN parentSourceCommentId IS NULL THEN publishedAtEpochMillis
                        ELSE COALESCE((SELECT p.publishedAtEpochMillis FROM comments p WHERE p.sourceCommentId = comments.parentSourceCommentId), publishedAtEpochMillis)
                    END
                ELSE 0
            END DESC,
            -- 2) Thread identity: group root and its replies together by root sourceCommentId (or parentSourceCommentId)
            CASE
                WHEN :viewMode = 1 THEN
                    COALESCE(parentSourceCommentId, sourceCommentId)
                ELSE ''
            END ASC,
            -- 3) Root precedes its replies (root has parentSourceCommentId IS NULL -> 0, replies -> 1)
            CASE
                WHEN :viewMode = 1 THEN
                    CASE WHEN parentSourceCommentId IS NULL THEN 0 ELSE 1 END
                ELSE 0
            END ASC,
            -- 4) Replies chronological ascending within root
            CASE
                WHEN :viewMode = 1 AND parentSourceCommentId IS NOT NULL THEN publishedAtEpochMillis
                ELSE 0
            END ASC,
            -- Categories view ordering: group size ordering (:sort = 4)
            CASE
                WHEN :viewMode = 2 AND :sort = 4 THEN
                    (SELECT COUNT(*) FROM comments c_cat WHERE c_cat.importId = :importId AND c_cat.category IS NOT NULL AND c_cat.category = comments.category)
                ELSE 0
            END DESC,
            -- Needs Attention ordering (:sort = 3)
            -- Priority: criticism > feedback > question > others
            CASE
                WHEN :sort = 3 THEN
                    CASE category
                        WHEN 'criticism' THEN 1
                        WHEN 'feedback' THEN 2
                        WHEN 'question' THEN 3
                        ELSE 4
                    END
                ELSE 0
            END ASC,
            -- General sort ordering
            CASE
                WHEN :sort = 1 THEN COALESCE(likeCount, 0)
                WHEN :sort = 2 THEN COALESCE(declaredReplyCount, 0)
                ELSE 0
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
