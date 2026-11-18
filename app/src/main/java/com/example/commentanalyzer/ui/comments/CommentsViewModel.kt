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

enum class CommentViewMode(val queryValue: Int) {
    THREADS(1),
    FLAT(0)
}

enum class CommentSortMode(val queryValue: Int) {
    MOST_RELEVANT(0),
    MOST_ENGAGED(1),
    MOST_DEBATED(2),
    NEWEST(3)
}

data class CommentScopeOption(
    val importId: String,
    val title: String,
    val sourceLabelRes: Int,
    val videoId: String?
)

data class CommentRowUiModel(
    val stableId: String,
    val importId: String,
    val sourceLabelRes: Int,
    val videoId: String?,
    val parentCommentId: String?,
    val author: String?,
    val text: String,
    val likes: Int?,
    val publishedAtEpochMillis: Long?,
    val isCitedInAnalysis: Boolean = false
)

data class CommentsUiState(
    val scopes: List<CommentScopeOption> = emptyList(),
    val selectedImportId: String? = null,
    val searchQuery: String = "",
    val viewMode: CommentViewMode = CommentViewMode.THREADS,
    val sortMode: CommentSortMode = CommentSortMode.MOST_RELEVANT,
    val referencedInSynthesisOnly: Boolean = false,
    val hasRepliesOnly: Boolean = false,
    val questionsOnly: Boolean = false,
    val activeSynthesisCitedIds: Set<String> = emptySet()
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            searchQuery.takeIf { it.isNotBlank() },
            referencedInSynthesisOnly.takeIf { it },
            hasRepliesOnly.takeIf { it },
            questionsOnly.takeIf { it }
        ).size
}

class CommentsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CommentAnalyzerApplication
    private val repository = CommentsRepository(
        app.database.commentDao(),
        app.database.importDao(),
        app.database.commentSynthesisDao()
    )

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    val comments = uiState
        .map { state ->
            PagingRequest(
                importId = state.selectedImportId,
                query = state.searchQuery.trim(),
                hasRepliesOnly = state.hasRepliesOnly,
                questionsOnly = state.questionsOnly,
                citedIds = if (state.referencedInSynthesisOnly) state.activeSynthesisCitedIds.toList() else emptyList(),
                hasCitedFilter = state.referencedInSynthesisOnly,
                viewMode = state.viewMode.queryValue,
                sort = state.sortMode.queryValue,
                activeCitedIds = state.activeSynthesisCitedIds
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
                    hasRepliesOnly = request.hasRepliesOnly,
                    questionsOnly = request.questionsOnly,
                    citedIds = request.citedIds,
                    hasCitedFilter = request.hasCitedFilter,
                    viewMode = request.viewMode,
                    sort = request.sort
                ).map { page ->
                    page.map { comment -> comment.toUiModel(request.activeCitedIds) }
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

        viewModelScope.launch {
            uiState
                .map { it.selectedImportId }
                .distinctUntilChanged()
                .flatMapLatest { importId ->
                    repository.observeSynthesisCitedCommentIds(importId)
                }
                .collect { citedIds ->
                    _uiState.update { it.copy(activeSynthesisCitedIds = citedIds) }
                }
        }
    }

    fun selectImport(importId: String) {
        if (_uiState.value.scopes.any { it.importId == importId }) {
            _uiState.update { it.copy(selectedImportId = importId) }
        }
    }

    fun focusComment(importId: String, commentId: String?) {
        _uiState.update { state ->
            state.copy(
                selectedImportId = importId,
                searchQuery = commentId ?: "",
                referencedInSynthesisOnly = false,
                hasRepliesOnly = false,
                questionsOnly = false
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setViewMode(viewMode: CommentViewMode) {
        _uiState.update { it.copy(viewMode = viewMode) }
    }

    fun setSortMode(sortMode: CommentSortMode) {
        _uiState.update { it.copy(sortMode = sortMode) }
    }

    fun toggleReferencedInSynthesis() {
        _uiState.update { it.copy(referencedInSynthesisOnly = !it.referencedInSynthesisOnly) }
    }

    fun toggleHasRepliesOnly() {
        _uiState.update { it.copy(hasRepliesOnly = !it.hasRepliesOnly) }
    }

    fun toggleQuestionsOnly() {
        _uiState.update { it.copy(questionsOnly = !it.questionsOnly) }
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                referencedInSynthesisOnly = false,
                hasRepliesOnly = false,
                questionsOnly = false
            )
        }
    }

    private data class PagingRequest(
        val importId: String?,
        val query: String,
        val hasRepliesOnly: Boolean,
        val questionsOnly: Boolean,
        val citedIds: List<String>,
        val hasCitedFilter: Boolean,
        val viewMode: Int,
        val sort: Int,
        val activeCitedIds: Set<String>
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

    private fun CommentEntity.toUiModel(activeCitedIds: Set<String>) = CommentRowUiModel(
        stableId = localId,
        importId = importId,
        sourceLabelRes = sourceType.toDisplayNameRes(),
        videoId = videoId,
        parentCommentId = parentSourceCommentId,
        author = authorDisplayName?.takeIf(String::isNotBlank),
        text = originalText,
        likes = likeCount,
        publishedAtEpochMillis = publishedAtEpochMillis,
        isCitedInAnalysis = localId in activeCitedIds
    )
}
