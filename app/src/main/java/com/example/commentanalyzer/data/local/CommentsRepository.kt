package com.example.commentanalyzer.data.local

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.commentanalyzer.data.local.dao.CommentDao
import com.example.commentanalyzer.data.local.dao.CommentSynthesisDao
import com.example.commentanalyzer.data.local.dao.ImportDao
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.domain.model.SynthesisResult
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class CommentsRepository(
    private val commentDao: CommentDao,
    private val importDao: ImportDao,
    private val commentSynthesisDao: CommentSynthesisDao
) {
    private val gson = Gson()

    fun observeRecentImportsWithComments(): Flow<List<ImportEntity>> =
        importDao.observeRecentImportsWithComments()

    fun observeSynthesisCitedCommentIds(importId: String?): Flow<Set<String>> {
        if (importId == null) return flowOf(emptySet())
        return commentSynthesisDao.observeSynthesis(
            importId,
            ProcessingConfigBootstrap.CURRENT_CONFIG_VERSION
        ).map { entity ->
            entity?.let {
                try {
                    gson.fromJson(it.payloadJson, SynthesisResult::class.java)?.allCitedCommentIds()
                } catch (_: Exception) {
                    null
                }
            } ?: emptySet()
        }
    }

    suspend fun getCommentById(commentId: String): CommentEntity? =
        commentDao.getCommentById(commentId)

    fun pagingComments(
        importId: String,
        query: String,
        hasRepliesOnly: Boolean = false,
        questionsOnly: Boolean = false,
        citedIds: List<String> = emptyList(),
        hasCitedFilter: Boolean = false,
        viewMode: Int = 1,
        sort: Int = 0,
        focusedThreadSourceId: String? = null
    ): Flow<PagingData<CommentEntity>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = {
            commentDao.pagingComments(
                importId = importId,
                query = query,
                hasRepliesOnly = if (hasRepliesOnly) 1 else 0,
                questionsOnly = if (questionsOnly) 1 else 0,
                citedIds = if (hasCitedFilter && citedIds.isNotEmpty()) citedIds else listOf(""),
                hasCitedFilter = if (hasCitedFilter) 1 else 0,
                viewMode = viewMode,
                sort = sort,
                focusedThreadSourceId = focusedThreadSourceId
            )
        }
    ).flow

    private companion object {
        const val PAGE_SIZE = 30
    }
}
