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
        relationship: Int
    ): Flow<PagingData<CommentEntity>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { commentDao.pagingComments(importId, query, relationship) }
    ).flow

    private companion object {
        const val PAGE_SIZE = 30
    }
}