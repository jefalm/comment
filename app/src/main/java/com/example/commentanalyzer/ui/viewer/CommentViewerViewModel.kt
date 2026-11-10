package com.example.commentanalyzer.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.commentanalyzer.domain.model.VideoCommentExportData
import com.example.commentanalyzer.ingestion.file.TxtExportParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOption {
    LIKES_DESC,
    DATE_NEWEST,
    DATE_OLDEST
}

data class CommentViewerUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val exportData: VideoCommentExportData? = null,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.LIKES_DESC,
    val pageSize: Int = 25,
    val currentPage: Int = 0,
    val expandedReplies: Set<String> = emptySet()
)

class CommentViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CommentViewerUiState())
    val uiState: StateFlow<CommentViewerUiState> = _uiState.asStateFlow()

    init {
        loadExportFile()
    }

    fun loadExportFile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val context = getApplication<Application>()
                val inputStream = context.assets.open("gemini_export.txt")
                val data = TxtExportParser.parse(inputStream)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        exportData = data,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                // Fallback to default sample parser or error state
                try {
                    val fallbackData = TxtExportParser.parseLines(fallbackRawLines)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            exportData = fallbackData,
                            errorMessage = null
                        )
                    }
                } catch (fallbackEx: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load comment export: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, currentPage = 0) }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option, currentPage = 0) }
    }

    fun setPageSize(size: Int) {
        _uiState.update { it.copy(pageSize = size, currentPage = 0) }
    }

    fun setPage(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    fun toggleReplies(commentId: String) {
        _uiState.update { state ->
            val current = state.expandedReplies
            val updated = if (current.contains(commentId)) {
                current - commentId
            } else {
                current + commentId
            }
            state.copy(expandedReplies = updated)
        }
    }

    private val fallbackRawLines = listOf(
        "YouTube Comment Viewer",
        "VIDEO",
        "ENEM 2026: REDAÇÃO SOBRE O SUS E QUESTÃO DO MST GERAM POLÊMICA",
        "O Antagonista",
        "6,750 views",
        "250",
        "Published: 11/9/2026, 12:59:35 PM",
        "41 comments",
        "▲ STATISTICS",
        "Top-level comments\t24",
        "Total likes\t58",
        "Average comments per day\t24.00",
        "Average likes per comment\t2.42",
        "@adilsonalves8028 | 11/9/2026, 1:57:32 PM",
        "#1",
        "Uma bancada de \"direita\" no congresso e no senado e não tem uma alma viva capaz de fazer um projeto de lei que exija apenas conteúdo científico do MEC... Eita Brasil...",
        "11",
        "▼ Load 5 replies"
    )
}
