package com.example.commentanalyzer.ui.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.commentanalyzer.CommentAnalyzerApplication
import com.example.commentanalyzer.data.local.AnalysisImportOption
import com.example.commentanalyzer.data.local.AnalysisReadRepository
import com.example.commentanalyzer.data.local.AnalysisSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalysisUiState(
    val imports: List<AnalysisImportOption> = emptyList(),
    val selectedImportId: String? = null,
    val configVersions: List<Long> = emptyList(),
    val selectedConfigVersion: Long? = null,
    val summary: AnalysisSummary? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CommentAnalyzerApplication
    private val repository = AnalysisReadRepository(
        commentDao = app.database.commentDao(),
        importDao = app.database.importDao(),
        processingBatchDao = app.database.processingBatchDao()
    )

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeImports()
                .catch {
                    _uiState.update { state -> state.copy(isLoading = false, hasError = true) }
                }
                .collect { imports ->
                    _uiState.update { state ->
                        val selectedImportId = state.selectedImportId
                            ?.takeIf { id -> imports.any { it.importId == id } }
                            ?: imports.firstOrNull()?.importId
                        state.copy(
                            imports = imports,
                            selectedImportId = selectedImportId,
                            hasError = false,
                            isLoading = imports.isNotEmpty() &&
                                (state.selectedConfigVersion == null || state.summary == null)
                        )
                    }
                }
        }

        viewModelScope.launch {
            uiState
                .map { it.selectedImportId }
                .distinctUntilChanged()
                .flatMapLatest { importId ->
                    importId?.let(repository::observeConfigVersions) ?: flowOf(emptyList())
                }
                .catch {
                    _uiState.update { state -> state.copy(isLoading = false, hasError = true) }
                }
                .collect { versions ->
                    _uiState.update { state ->
                        val selectedVersion = state.selectedConfigVersion
                            ?.takeIf(versions::contains)
                            ?: versions.firstOrNull()
                        state.copy(
                            configVersions = versions,
                            selectedConfigVersion = selectedVersion,
                            summary = if (selectedVersion == state.selectedConfigVersion) state.summary else null,
                            isLoading = selectedVersion != null && state.summary == null,
                            hasError = false
                        )
                    }
                }
        }

        viewModelScope.launch {
            uiState
                .map { state -> SummaryRequest(state.selectedImportId, state.selectedConfigVersion) }
                .distinctUntilChanged()
                .flatMapLatest { request ->
                    if (request.importId == null || request.configVersion == null) {
                        flowOf<AnalysisSummary?>(null)
                    } else {
                        repository.observeSummary(request.importId, request.configVersion)
                            .map<AnalysisSummary, AnalysisSummary?> { it }
                    }
                }
                .catch {
                    _uiState.update { state ->
                        state.copy(summary = null, isLoading = false, hasError = true)
                    }
                }
                .collect { summary ->
                    _uiState.update { state ->
                        state.copy(summary = summary, isLoading = false, hasError = false)
                    }
                }
        }
    }

    fun selectImport(importId: String) {
        if (_uiState.value.imports.any { it.importId == importId }) {
            _uiState.update { state ->
                state.copy(
                    selectedImportId = importId,
                    summary = null,
                    isLoading = true,
                    hasError = false
                )
            }
        }
    }

    fun selectConfigVersion(configVersion: Long) {
        if (configVersion in _uiState.value.configVersions) {
            _uiState.update { state ->
                state.copy(
                    selectedConfigVersion = configVersion,
                    summary = null,
                    isLoading = true,
                    hasError = false
                )
            }
        }
    }

    private data class SummaryRequest(val importId: String?, val configVersion: Long?)
}
