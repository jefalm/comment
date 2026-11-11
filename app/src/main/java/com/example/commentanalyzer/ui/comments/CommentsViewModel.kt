package com.example.commentanalyzer.ui.comments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.commentanalyzer.CommentAnalyzerApplication
import com.example.commentanalyzer.R
import com.example.commentanalyzer.data.local.CommentsRepository
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.domain.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommentRelationshipFilter(val queryValue: Int) {
    ALL(-1),
    TOP_LEVEL(0),
    REPLIES(1)
}

data class CommentScopeOption(
    val importId: String,
    val title: String,
    val sourceLabelRes: Int,
    val videoId: String?
)

data class CommentRowUiModel(
    val stableId: String,
    val displayId: String,
    val importId: String,
    val sourceLabelRes: Int,
    val videoId: String?,
    val parentCommentId: String?,
    val author: String?,
    val text: String,
    val likes: Int?,
    val publishedAtEpochMillis: Long?
)

data class CommentsUiState(
    val scopes: List<CommentScopeOption> = emptyList(),
    val selectedImportId: String? = null,
    val searchQuery: String = "",
    val relationship: CommentRelationshipFilter = CommentRelationshipFilter.ALL
)

class CommentsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CommentAnalyzerApplication
    private val repository = CommentsRepository(
        app.database.commentDao(),
        app.database.importDao()
    )

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    val comments = uiState
        .map { state ->
            PagingRequest(
                importId = state.selectedImportId,
                query = state.searchQuery.trim(),
                relationship = state.relationship.queryValue
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            val importId = request.importId
            if (importId == null) {
                flowOf(PagingData.empty())
            } else {
                repository.pagingComments(importId, request.query, request.relationship)
                    .map { page ->
                        page.map { comment -> comment.toUiModel() }
                    }
            }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            repository.observeRecentImportsWithComments().collect { imports ->
                val options = imports.map { it.toScopeOption() }
                _uiState.update { state ->
                    val retainedSelection = state.selectedImportId
                        ?.takeIf { id -> options.any { it.importId == id } }
                    state.copy(
                        scopes = options,
                        selectedImportId = retainedSelection ?: options.firstOrNull()?.importId
                    )
                }
            }
        }
    }

    fun selectImport(importId: String) {
        if (_uiState.value.scopes.any { it.importId == importId }) {
            _uiState.update { it.copy(selectedImportId = importId) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setRelationship(relationship: CommentRelationshipFilter) {
        _uiState.update { it.copy(relationship = relationship) }
    }

    private data class PagingRequest(
        val importId: String?,
        val query: String,
        val relationship: Int
    )

    private fun ImportEntity.toScopeOption() = CommentScopeOption(
        importId = importId,
        title = videoTitle?.takeIf(String::isNotBlank)
            ?: videoId?.takeIf(String::isNotBlank)
            ?: "Imported comments",
        sourceLabelRes = sourceType.toDisplayNameRes(),
        videoId = videoId
    )

    private fun SourceType.toDisplayNameRes(): Int = when (this) {
        SourceType.YOUTUBE_API -> R.string.comments_source_youtube
        SourceType.TXT_EXPORT -> R.string.comments_source_txt
        else -> R.string.comments_source_other
    }

    private fun CommentEntity.toUiModel() = CommentRowUiModel(
        stableId = localId,
        displayId = sourceCommentId ?: localId,
        importId = importId,
        sourceLabelRes = sourceType.toDisplayNameRes(),
        videoId = videoId,
        parentCommentId = parentSourceCommentId,
        author = authorDisplayName?.takeIf(String::isNotBlank),
        text = originalText,
        likes = likeCount,
        publishedAtEpochMillis = publishedAtEpochMillis
    )
}