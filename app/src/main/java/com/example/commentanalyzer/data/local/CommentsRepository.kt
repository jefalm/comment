package com.example.commentanalyzer.data.local

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.commentanalyzer.data.local.dao.CommentDao
import com.example.commentanalyzer.data.local.dao.ImportDao
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import kotlinx.coroutines.flow.Flow

class CommentsRepository(
    private val commentDao: CommentDao,
    private val importDao: ImportDao
) {
    fun observeRecentImportsWithComments(): Flow<List<ImportEntity>> =
        importDao.observeRecentImportsWithComments()

    fun pagingComments(
        importId: String,
        query: String,
        relationship: Int,
        categories: List<String>,
        sentiments: List<String>,
        stateFilter: Int,
        hasRepliesOnly: Boolean,
        viewMode: Int,
        sort: Int
    ): Flow<PagingData<CommentEntity>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = {
            commentDao.pagingComments(
                importId = importId,
                query = query,
                relationship = relationship,
                categories = categories,
                hasCategories = if (categories.isEmpty()) 0 else 1,
                sentiments = sentiments,
                hasSentiments = if (sentiments.isEmpty()) 0 else 1,
                stateFilter = stateFilter,
                hasRepliesOnly = if (hasRepliesOnly) 1 else 0,
                viewMode = viewMode,
                sort = sort
            )
        }
    ).flow

    private companion object {
        const val PAGE_SIZE = 30
    }
}
