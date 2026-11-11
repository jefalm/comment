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

enum class CommentViewMode {
    THREADS, CATEGORIES, ALL
}

enum class CommentSortMode(val queryValue: Int) {
    NEWEST(0),
    OLDEST(1),
    MOST_ENGAGED(2),
    NEEDS_ATTENTION(3),
    GROUP_SIZE(4)
}

enum class CommentStateFilter(val queryValue: Int) {
    ALL(0),
    UNCLASSIFIED(1),
    REVIEW_REJECTED(2),
    RETRYING(3),
    FAILED(4)
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
    val publishedAtEpochMillis: Long?,
    val category: String?,
    val sentiment: String?
)

data class CommentsUiState(
    val scopes: List<CommentScopeOption> = emptyList(),
    val selectedImportId: String? = null,
    val searchQuery: String = "",
    val relationship: CommentRelationshipFilter = CommentRelationshipFilter.ALL,
    val viewMode: CommentViewMode = CommentViewMode.ALL,
    val sortMode: CommentSortMode = CommentSortMode.NEWEST,
    val selectedCategories: Set<String> = emptySet(),
    val selectedSentiments: Set<String> = emptySet(),
    val stateFilter: CommentStateFilter = CommentStateFilter.ALL,
    val hasRepliesOnly: Boolean = false
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            searchQuery.takeIf { it.isNotBlank() },
            relationship.takeIf { it != CommentRelationshipFilter.ALL },
            selectedCategories.takeIf { it.isNotEmpty() },
            selectedSentiments.takeIf { it.isNotEmpty() },
            stateFilter.takeIf { it != CommentStateFilter.ALL },
            hasRepliesOnly.takeIf { it }
        ).size
}

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
                relationship = state.relationship.queryValue,
                categories = state.selectedCategories.toList(),
                sentiments = state.selectedSentiments.toList(),
                stateFilter = state.stateFilter.queryValue,
                hasRepliesOnly = state.hasRepliesOnly,
                viewMode = when (state.viewMode) {
                    CommentViewMode.THREADS -> 1
                    CommentViewMode.CATEGORIES -> 2
                    CommentViewMode.ALL -> 0
                },
                sort = state.sortMode.queryValue
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            val importId = request.importId
            if (importId == null) {
                flowOf(PagingData.empty())
            } else {
                repository.pagingComments(
                    importId = importId,
                    query = request.query,
                    relationship = request.relationship,
                    categories = request.categories,
                    sentiments = request.sentiments,
                    stateFilter = request.stateFilter,
                    hasRepliesOnly = request.hasRepliesOnly,
                    viewMode = request.viewMode,
                    sort = request.sort
                ).map { page ->
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

    fun setViewMode(viewMode: CommentViewMode) {
        _uiState.update { it.copy(viewMode = viewMode) }
    }

    fun setSortMode(sortMode: CommentSortMode) {
        _uiState.update { it.copy(sortMode = sortMode) }
    }

    fun toggleCategory(category: String) {
        _uiState.update { state ->
            val updated = if (state.selectedCategories.contains(category)) {
                state.selectedCategories - category
            } else {
                state.selectedCategories + category
            }
            state.copy(selectedCategories = updated)
        }
    }

    fun toggleSentiment(sentiment: String) {
        _uiState.update { state ->
            val updated = if (state.selectedSentiments.contains(sentiment)) {
                state.selectedSentiments - sentiment
            } else {
                state.selectedSentiments + sentiment
            }
            state.copy(selectedSentiments = updated)
        }
    }

    fun setStateFilter(stateFilter: CommentStateFilter) {
        _uiState.update { it.copy(stateFilter = stateFilter) }
    }

    fun setHasRepliesOnly(hasRepliesOnly: Boolean) {
        _uiState.update { it.copy(hasRepliesOnly = hasRepliesOnly) }
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                relationship = CommentRelationshipFilter.ALL,
                selectedCategories = emptySet(),
                selectedSentiments = emptySet(),
                stateFilter = CommentStateFilter.ALL,
                hasRepliesOnly = false
            )
        }
    }

    private data class PagingRequest(
        val importId: String?,
        val query: String,
        val relationship: Int,
        val categories: List<String>,
        val sentiments: List<String>,
        val stateFilter: Int,
        val hasRepliesOnly: Boolean,
        val viewMode: Int,
        val sort: Int
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
        publishedAtEpochMillis = publishedAtEpochMillis,
        category = category,
        sentiment = sentiment
    )
}
