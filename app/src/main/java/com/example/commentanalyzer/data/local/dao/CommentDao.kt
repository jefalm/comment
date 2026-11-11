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
        SELECT COUNT(*) AS totalComments,
            COALESCE(SUM(CASE WHEN c.processingState = 'LOCAL_REJECTED' THEN 1 ELSE 0 END), 0) AS locallyRejected,
            COALESCE(SUM(CASE WHEN c.processingState = 'JEV_REJECTED' THEN 1 ELSE 0 END), 0) AS jevRejected,
            COALESCE(SUM(CASE WHEN c.processingState = 'COMPLETED' AND EXISTS (
                SELECT 1 FROM analysis_results r
                WHERE r.commentId = c.localId
                    AND r.processingConfigVersion = :configVersion
                    AND r.stage = 'GLM_ANALYSIS'
            ) THEN 1 ELSE 0 END), 0) AS completed,
            COALESCE(SUM(CASE WHEN c.processingState = 'RETRY_WAIT' THEN 1 ELSE 0 END), 0) AS retrying,
            COALESCE(SUM(CASE WHEN c.processingState = 'FAILED_PERMANENT' THEN 1 ELSE 0 END), 0) AS permanentlyFailed,
            COALESCE(SUM(CASE WHEN c.processingState IN ('JEV_IN_PROGRESS', 'GLM_IN_PROGRESS') THEN 1 ELSE 0 END), 0) AS inProgress
        FROM comments c
        WHERE c.importId = :importId
            AND c.processingConfigVersion = :configVersion
        """
    )
    fun observeAnalysisProcessingCounts(
        importId: String,
        configVersion: Long
    ): Flow<AnalysisProcessingCounts>

    @Query(
        """
        SELECT DISTINCT processingConfigVersion
        FROM comments
        WHERE importId = :importId
        ORDER BY processingConfigVersion DESC
        """
    )
    fun observeAnalysisConfigVersions(importId: String): Flow<List<Long>>

    @Query(
        """
        SELECT c.category AS value, COUNT(*) AS count
        FROM comments c
        WHERE c.importId = :importId
            AND c.processingConfigVersion = :configVersion
            AND c.category IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM analysis_results r
                WHERE r.commentId = c.localId
                    AND r.processingConfigVersion = :configVersion
                    AND r.stage = 'GLM_ANALYSIS'
            )
        GROUP BY c.category
        ORDER BY c.category
        """
    )
    fun observeAnalysisCategoryCounts(
        importId: String,
        configVersion: Long
    ): Flow<List<AnalysisDistributionCount>>

    @Query(
        """
        SELECT c.sentiment AS value, COUNT(*) AS count
        FROM comments c
        WHERE c.importId = :importId
            AND c.processingConfigVersion = :configVersion
            AND c.sentiment IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM analysis_results r
                WHERE r.commentId = c.localId
                    AND r.processingConfigVersion = :configVersion
                    AND r.stage = 'GLM_ANALYSIS'
            )
        GROUP BY c.sentiment
        ORDER BY c.sentiment
        """
    )
    fun observeAnalysisSentimentCounts(
        importId: String,
        configVersion: Long
    ): Flow<List<AnalysisDistributionCount>>

    @Update
    suspend fun updateComment(comment: CommentEntity): Int

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
                WHEN :viewMode = 1 THEN
                    CASE WHEN parentSourceCommentId IS NULL THEN 0 ELSE 1 END
                ELSE 0
            END ASC,
            CASE
                WHEN :viewMode = 1 THEN
                    publishedAtEpochMillis
                ELSE 0
            END ASC,

            -- Categories view ordering: group by category group size descending, then category name
            CASE WHEN :viewMode = 2 AND :sort = 4 THEN (SELECT COUNT(*) FROM comments c2 WHERE c2.importId = :importId AND COALESCE(c2.category, 'Unclassified') = COALESCE(comments.category, 'Unclassified')) END DESC,
            CASE WHEN :viewMode = 2 THEN COALESCE(category, 'Unclassified') END ASC,

            -- Standard sorts
            CASE WHEN :sort = 0 THEN publishedAtEpochMillis END DESC,
            CASE WHEN :sort = 1 THEN publishedAtEpochMillis END ASC,
            CASE WHEN :sort = 2 THEN COALESCE(declaredReplyCount, 0) END DESC,
            CASE WHEN :sort = 2 THEN COALESCE(likeCount, 0) END DESC,
            CASE WHEN :sort = 2 THEN publishedAtEpochMillis END DESC,
            CASE WHEN :sort = 3 THEN
                CASE category
                    WHEN 'criticism' THEN 1
                    WHEN 'feedback' THEN 2
                    WHEN 'question' THEN 3
                    WHEN 'negative sentiment' THEN 4
                    WHEN 'reply count descending' THEN 5
                    WHEN 'like count descending' THEN 6
                    WHEN 'published timestamp descending' THEN 7
                    ELSE 8
                END
            END ASC,
            CASE WHEN :sort = 3 THEN
                CASE sentiment
                    WHEN 'negative' THEN 1
                    WHEN 'neutral' THEN 2
                    WHEN 'positive' THEN 3
                    ELSE 4
                END
            END ASC,
            CASE WHEN :sort = 3 THEN COALESCE(declaredReplyCount, 0) END DESC,
            CASE WHEN :sort = 3 THEN COALESCE(likeCount, 0) END DESC,
            CASE WHEN :sort = 3 THEN publishedAtEpochMillis END DESC,
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

    @Query("UPDATE comments SET processingState = :newState, currentStage = :newStage, updatedAt = :updatedAt WHERE localId = :localId AND processingState = :expectedState")
    suspend fun updateStateCas(localId: String, expectedState: ProcessingState, newState: ProcessingState, newStage: ProcessingStage, updatedAt: Long): Int

    @Query("SELECT * FROM comments WHERE processingState = :state ORDER BY publishedAtEpochMillis DESC")
    suspend fun getCommentsByState(state: ProcessingState): List<CommentEntity>

    @Query("UPDATE comments SET leaseOwner = :owner, leaseExpiresAt = :expiresAt, updatedAt = :updatedAt WHERE localId IN (:localIds) AND (leaseExpiresAt IS NULL OR leaseExpiresAt < :currentTime)")
    suspend fun claimComments(localIds: List<String>, owner: String, expiresAt: Long, currentTime: Long, updatedAt: Long): Int

    @Query("UPDATE comments SET processingState = :fallbackState, leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :updatedAt WHERE leaseExpiresAt IS NOT NULL AND leaseExpiresAt < :currentTime AND processingState IN (:activeStates)")
    suspend fun reclaimExpiredLeases(currentTime: Long, fallbackState: ProcessingState, activeStates: List<ProcessingState>, updatedAt: Long): Int
}
