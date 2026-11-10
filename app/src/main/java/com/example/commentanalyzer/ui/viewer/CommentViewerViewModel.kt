package com.example.commentanalyzer.ui.viewer

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.commentanalyzer.CommentAnalyzerApplication
import com.example.commentanalyzer.data.local.entity.ImportEntity
import com.example.commentanalyzer.data.local.entity.ImportStatus
import com.example.commentanalyzer.domain.model.ImportProgress
import com.example.commentanalyzer.domain.model.ImportRequest
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.domain.model.VideoCommentExportData
import com.example.commentanalyzer.ingestion.file.TxtExportParser
import com.example.commentanalyzer.ingestion.file.TxtParseException
import com.example.commentanalyzer.ingestion.youtube.YouTubeImportException
import com.example.commentanalyzer.ingestion.youtube.YouTubeVideoPreview
import com.example.commentanalyzer.worker.CommentAnalyzerWorker
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption {
    LIKES_DESC,
    DATE_NEWEST,
    DATE_OLDEST
}

data class CommentViewerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val exportData: VideoCommentExportData? = null,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.LIKES_DESC,
    val pageSize: Int = 25,
    val currentPage: Int = 0,
    val expandedReplies: Set<String> = emptySet(),
    val youtubeInput: String = "",
    val youtubePreview: YouTubeVideoPreview? = null,
    val isLoadingPreview: Boolean = false,
    val includeReplies: Boolean = true,
    val isImportRunning: Boolean = false,
    val importProgress: ImportProgress? = null,
    val importErrorCode: String? = null,
    val activeImportId: String? = null,
    val resumableImportId: String? = null,
    val hasYouTubeApiKey: Boolean = false,
    val isReadingTxt: Boolean = false,
    val txtFileName: String? = null,
    val txtFilePath: String? = null,
    val txtPreview: VideoCommentExportData? = null,
    val txtErrorCode: String? = null
)

class CommentViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val app: CommentAnalyzerApplication
        get() = getApplication()

    private val _uiState = MutableStateFlow(CommentViewerUiState())
    val uiState: StateFlow<CommentViewerUiState> = _uiState.asStateFlow()
    private var importJob: Job? = null

    init {
        refreshApiKeyStatus()
        refreshResumableImport()
    }

    fun setYouTubeInput(value: String) {
        _uiState.update { it.copy(youtubeInput = value, youtubePreview = null, importErrorCode = null) }
    }

    fun setIncludeReplies(include: Boolean) {
        _uiState.update { it.copy(includeReplies = include) }
    }

    fun refreshApiKeyStatus() {
        val hasKey = runCatching {
            !app.secureCredentialStore.getApiKey(CommentAnalyzerApplication.YOUTUBE_API_KEY_NAME).isNullOrBlank()
        }.getOrDefault(false)
        _uiState.update { it.copy(hasYouTubeApiKey = hasKey) }
    }

    fun previewYouTube() {
        val locator = _uiState.value.youtubeInput.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPreview = true, youtubePreview = null, importErrorCode = null) }
            try {
                val preview = app.youtubeCommentImporter.preview(locator)
                _uiState.update { it.copy(isLoadingPreview = false, youtubePreview = preview) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: YouTubeImportException) {
                _uiState.update { it.copy(isLoadingPreview = false, importErrorCode = failure.code) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingPreview = false, importErrorCode = "youtube_request_failed") }
            }
        }
    }

    fun importYouTube() {
        val video = _uiState.value.youtubePreview ?: return
        val importId = UUID.randomUUID().toString()
        val request = ImportRequest(
            importId = importId,
            sourceType = SourceType.YOUTUBE_API,
            sourceLocator = video.videoId,
            sourceScopeKey = "${video.videoId}:$importId",
            includeReplies = _uiState.value.includeReplies
        )
        runImport(request, video)
    }

    fun resumeYouTube(importId: String) {
        viewModelScope.launch {
            val saved = app.database.importDao().getImportById(importId) ?: return@launch
            val request = ImportRequest(
                importId = saved.importId,
                sourceType = SourceType.YOUTUBE_API,
                sourceLocator = saved.videoId ?: saved.sourceLocator,
                sourceScopeKey = saved.sourceScopeKey,
                includeReplies = saved.includeReplies,
                nextThreadPageToken = saved.nextThreadPageToken,
                activeParentCommentId = saved.activeParentCommentId,
                nextReplyPageToken = saved.nextReplyPageToken
            )
            _uiState.update { it.copy(youtubeInput = request.sourceLocator, youtubePreview = null) }
            runImport(request)
        }
    }

    fun cancelImport() {
        val activeImportId = _uiState.value.activeImportId
        if (activeImportId != null) CommentAnalyzerWorker.cancel(app, activeImportId)
        importJob?.cancel()
        _uiState.update {
            it.copy(
                isImportRunning = false,
                activeImportId = null,
                resumableImportId = activeImportId ?: it.resumableImportId
            )
        }
    }

    private fun runImport(request: ImportRequest, preview: YouTubeVideoPreview? = null) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImportRunning = true,
                    activeImportId = request.importId,
                    importProgress = null,
                    importErrorCode = null
                )
            }
            try {
                if (preview != null) {
                    withContext(Dispatchers.IO) { app.youtubeImportPageSink.prepareImport(request, preview) }
                }
                CommentAnalyzerWorker.enqueue(app, request)
                val saved = app.database.importDao().observeImportById(request.importId).first { current ->
                    current != null && (
                        current.status == ImportStatus.COMPLETED ||
                            current.status == ImportStatus.CANCELLED ||
                            (current.status == ImportStatus.FAILED && current.retryable != true)
                        )
                } ?: return@launch
                _uiState.update {
                    it.copy(
                        isImportRunning = false,
                        activeImportId = null,
                        importProgress = saved.toImportProgress(),
                        importErrorCode = saved.lastErrorCode,
                        resumableImportId = saved.importId.takeIf {
                            saved.status == ImportStatus.CANCELLED || saved.retryable == true
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isImportRunning = false, activeImportId = null, importErrorCode = "import_interrupted")
                }
            }
        }
    }

    fun prepareTxtImport(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReadingTxt = true, txtPreview = null, txtErrorCode = null) }
            try {
                val staged = withContext(Dispatchers.IO) {
                    val resolver = app.contentResolver
                    val displayName = resolver.displayName(uri) ?: "import.txt"
                    val mime = resolver.getType(uri)
                    val hasTxtExtension = displayName.endsWith(".txt", ignoreCase = true)
                    if (mime != null && mime != "text/plain" && !(mime == "application/octet-stream" && hasTxtExtension)) {
                        throw TxtParseException("unsupported_file_type", null, null)
                    }
                    if (mime == null && !hasTxtExtension) throw TxtParseException("unsupported_file_type", null, null)

                    val directory = File(app.filesDir, "imports").apply { mkdirs() }
                    val file = File(directory, "${UUID.randomUUID()}.txt")
                    try {
                        val input = resolver.openInputStream(uri) ?: throw TxtParseException("unreadable_file", null, null)
                        input.use { stream ->
                            FileOutputStream(file).use { output ->
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > TxtExportParser.MAX_FILE_SIZE_BYTES) {
                                        throw TxtParseException("file_too_large", null, null)
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        val data = file.inputStream().use(TxtExportParser::parse)
                        Triple(file, displayName, data)
                    } catch (failure: Exception) {
                        file.delete()
                        throw failure
                    }
                }
                _uiState.update {
                    it.copy(
                        isReadingTxt = false,
                        txtFileName = staged.second,
                        txtFilePath = staged.first.absolutePath,
                        txtPreview = staged.third
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: TxtParseException) {
                val detail = listOfNotNull(
                    failure.reasonCode,
                    failure.section?.let { "section=$it" },
                    failure.lineNumber?.let { "line=$it" }
                ).joinToString(" · ")
                _uiState.update { it.copy(isReadingTxt = false, txtErrorCode = detail) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isReadingTxt = false, txtErrorCode = "txt_read_failed") }
            }
        }
    }

    fun confirmTxtImport() {
        val state = _uiState.value
        val filePath = state.txtFilePath ?: return
        val preview = state.txtPreview ?: return
        val importId = UUID.randomUUID().toString()
        val request = ImportRequest(
            importId = importId,
            sourceType = SourceType.TXT_EXPORT,
            sourceLocator = filePath,
            sourceScopeKey = "txt:$importId",
            includeReplies = false
        )
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isImportRunning = true, activeImportId = null, importProgress = null, importErrorCode = null)
            }
            try {
                val progress = withContext(Dispatchers.IO) { app.txtExportImporter.import(request) }
                _uiState.update {
                    it.copy(
                        isImportRunning = false,
                        importProgress = progress,
                        importErrorCode = progress.lastErrorCode,
                        exportData = preview.takeIf { progress.completed }
                    )
                }
                if (progress.completed) clearTxtSelection()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isImportRunning = false, importErrorCode = "txt_import_failed") }
            }
        }
    }

    fun clearTxtSelection() {
        _uiState.value.txtFilePath?.let { File(it).delete() }
        _uiState.update { it.copy(txtFileName = null, txtFilePath = null, txtPreview = null, txtErrorCode = null) }
    }

    private fun refreshResumableImport() {
        viewModelScope.launch {
            val saved = runCatching { app.database.importDao().getLatestResumableYouTubeImport() }.getOrNull()
            _uiState.update { it.copy(resumableImportId = saved?.importId) }
        }
    }

    private fun ContentResolver.displayName(uri: Uri): String? = query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

    private fun ImportEntity.toImportProgress() = ImportProgress(
        importId = importId,
        itemsSeen = itemsSeen,
        itemsInserted = itemsInserted,
        itemsDeduplicated = itemsDeduplicated,
        itemsFailed = itemsFailed,
        completed = status == ImportStatus.COMPLETED,
        lastErrorCode = lastErrorCode,
        retryable = retryable
    )

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
            val updated = if (commentId in state.expandedReplies) {
                state.expandedReplies - commentId
            } else {
                state.expandedReplies + commentId
            }
            state.copy(expandedReplies = updated)
        }
    }
}
