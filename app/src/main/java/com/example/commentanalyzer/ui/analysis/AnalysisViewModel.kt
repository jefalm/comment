package com.example.commentanalyzer.ui.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.commentanalyzer.AnalysisAppContainer
import com.example.commentanalyzer.CommentAnalyzerApplication
import com.example.commentanalyzer.data.local.AnalysisImportOption
import com.example.commentanalyzer.data.local.AnalysisSummary
import com.example.commentanalyzer.data.local.dao.AnalysisProcessingCounts
import com.example.commentanalyzer.data.local.entity.CommentEntity
import com.example.commentanalyzer.worker.AnalysisWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AnalysisActionMessage {
    NOT_READY,
    NO_ELIGIBLE_COMMENTS,
    SUCCEEDED,
    PARTIAL,
    ANALYSIS_FAILED,
    REMOTE_BLOCKED,
    FAILED,
    CANCELLED
}

data class EvidenceSheetState(
    val title: String,
    val totalCitedCount: Int,
    val comments: List<CommentEntity>
)

data class AnalysisUiState(
    val imports: List<AnalysisImportOption> = emptyList(),
    val selectedImportId: String? = null,
    val configVersions: List<Long> = emptyList(),
    val selectedConfigVersion: Long? = null,
    val summary: AnalysisSummary? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val isRemoteProcessingReady: Boolean = false,
    val isAnalysisRunning: Boolean = false,
    val analysisActionMessage: AnalysisActionMessage? = null,
    val evidenceSheetState: EvidenceSheetState? = null,
    val askCommentsOpen: Boolean = false,
    val question: String = "",
    val qaLoading: Boolean = false,
    val qaAnswer: String? = null,
    val qaCitedCommentIds: List<String> = emptyList(),
    val qaInsufficientEvidence: Boolean = false,
    val qaError: String? = null,
    val pinnedReplyDraft: String? = null,
    val pinnedReplyOpen: Boolean = false,
    val pinnedReplySupportingEvidenceIds: List<String> = emptyList()
) {
    val suggestedQuestions: List<String>
        get() {
            val synth = summary?.synthesis ?: return emptyList()
            val list = mutableListOf<String>()
            synth.debates.firstOrNull()?.let {
                list.add("What are viewers debating about ${it.topic}?")
            }
            synth.questionsAndRequests.take(2).forEach {
                list.add(it.questionOrRequest)
            }
            if (list.size < 3) {
                synth.blindSpotsAndCorrections.firstOrNull()?.let {
                    list.add("What corrections or misconceptions were raised?")
                }
            }
            return list.take(3)
        }
}

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CommentAnalyzerApplication
    private val analysisContainer = app.container as AnalysisAppContainer
    private val repository = analysisContainer.analysisReadRepository

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState = _uiState.asStateFlow()
    private val workManager = WorkManager.getInstance(app)
    private val analysisAction = AnalysisActionController(
        isReady = { configVersion -> isRemoteProcessingReady(configVersion) },
        enqueue = { importId, configVersion ->
            AnalysisWorker.enqueue(app, importId, configVersion)
        },
        onStateChanged = { importId, configVersion, running, message ->
            _uiState.update {
                if (it.selectedImportId == importId && it.selectedConfigVersion == configVersion) {
                    it.copy(isAnalysisRunning = running, analysisActionMessage = message)
                } else {
                    it
                }
            }
        }
    )

    init {
        refreshRemoteProcessingReadiness()
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
                            hasError = false,
                            isRemoteProcessingReady = isRemoteProcessingReady(selectedVersion)
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

        viewModelScope.launch {
            uiState
                .map { state ->
                    WorkSelection(state.selectedImportId, state.selectedConfigVersion)
                }
                .distinctUntilChanged()
                .flatMapLatest { selection ->
                    val importId = selection.importId
                    val configVersion = selection.configVersion
                    if (importId == null || configVersion == null) {
                        flowOf(WorkObservation(emptyList(), null))
                    } else {
                        combine(
                            workManager.getWorkInfosForUniqueWorkFlow(
                                AnalysisWorker.uniqueWorkName(importId, configVersion)
                            ),
                            repository.observeSummary(importId, configVersion)
                        ) { infos, summary ->
                            WorkObservation(infos, summary.processing)
                        }
                    }
                }
                .collect { observation ->
                    val infos = observation.infos
                    val selection = uiState.value.let {
                        WorkSelection(it.selectedImportId, it.selectedConfigVersion)
                    }
                    if (selection.importId != null && selection.configVersion != null && infos.isNotEmpty()) {
                        val active = infos.any {
                            it.state == WorkInfo.State.ENQUEUED ||
                                it.state == WorkInfo.State.RUNNING ||
                                it.state == WorkInfo.State.BLOCKED
                        }
                        val latest = infos.last()
                        val message = if (active) null else when (latest.state) {
                            WorkInfo.State.SUCCEEDED -> if (
                                observation.processing == null
                            ) null else classifyAnalysisCompletion(
                                requireNotNull(observation.processing)
                            )
                            WorkInfo.State.FAILED -> if (
                                latest.outputData.getString(AnalysisWorker.KEY_ERROR) ==
                                AnalysisWorker.ERROR_NOT_READY
                            ) AnalysisActionMessage.NOT_READY else AnalysisActionMessage.FAILED
                            WorkInfo.State.CANCELLED -> AnalysisActionMessage.CANCELLED
                            else -> null
                        }
                        analysisAction.onWorkUpdate(
                            selection.importId,
                            selection.configVersion,
                            active,
                            message
                        )
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
                    hasError = false,
                    isAnalysisRunning = false,
                    analysisActionMessage = null
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
                    hasError = false,
                    isAnalysisRunning = false,
                    analysisActionMessage = null,
                    isRemoteProcessingReady = isRemoteProcessingReady(configVersion)
                )
            }
        }
    }

    fun refreshRemoteProcessingReadiness() {
        _uiState.update { it.copy(isRemoteProcessingReady = isRemoteProcessingReady(it.selectedConfigVersion)) }
    }

    fun analyzeSelectedImport() {
        val state = _uiState.value
        val importId = state.selectedImportId ?: return
        val configVersion = state.selectedConfigVersion ?: return
        if (state.isAnalysisRunning) return
        analysisAction.start(importId, configVersion)
    }

    fun showEvidence(title: String, citedCommentIds: List<String>) {
        val importId = _uiState.value.selectedImportId ?: return
        if (citedCommentIds.isEmpty()) return
        viewModelScope.launch {
            val rawComments = repository.getCommentsByIds(citedCommentIds)
            val ordered = filterAndOrderEvidenceComments(rawComments, importId, citedCommentIds)
            _uiState.update {
                it.copy(
                    evidenceSheetState = EvidenceSheetState(
                        title = title,
                        totalCitedCount = citedCommentIds.size,
                        comments = ordered
                    )
                )
            }
        }
    }

    fun dismissEvidence() {
        _uiState.update { it.copy(evidenceSheetState = null) }
    }

    fun openAskComments() {
        _uiState.update { it.copy(askCommentsOpen = true) }
    }

    fun dismissAskComments() {
        _uiState.update { it.copy(askCommentsOpen = false) }
    }

    fun updateQuestion(question: String) {
        _uiState.update { it.copy(question = question) }
    }

    fun askQuestion(query: String = _uiState.value.question) {
        val importId = _uiState.value.selectedImportId ?: return
        val configVersion = _uiState.value.selectedConfigVersion ?: return
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || _uiState.value.qaLoading) return

        _uiState.update {
            it.copy(
                question = cleanQuery,
                qaLoading = true,
                qaAnswer = null,
                qaCitedCommentIds = emptyList(),
                qaInsufficientEvidence = false,
                qaError = null
            )
        }

        viewModelScope.launch {
            val candidateComments = app.database.commentDao().getCommentsForSynthesis(importId, configVersion)
            val activeSynthesisCitedIds = _uiState.value.summary?.synthesis?.allCitedCommentIds() ?: emptySet()
            val retrievedComments = com.example.commentanalyzer.domain.synthesis.CommentQueryRetriever.retrieveRelevantComments(
                importId = importId,
                configVersion = configVersion,
                question = cleanQuery,
                candidateComments = candidateComments,
                activeSynthesisCitedIds = activeSynthesisCitedIds
            )

            val result = analysisContainer.commentQaAdapter.answerQuestion(
                question = cleanQuery,
                evidenceComments = retrievedComments
            )

            _uiState.update {
                it.copy(
                    qaLoading = false,
                    qaAnswer = result.answer,
                    qaCitedCommentIds = result.citedCommentIds,
                    qaInsufficientEvidence = result.insufficientEvidence,
                    qaError = result.error
                )
            }
        }
    }

    fun showQaEvidence() {
        val ids = _uiState.value.qaCitedCommentIds
        if (ids.isNotEmpty()) {
            showEvidence("Q&A Evidence", ids)
        }
    }

    fun openPinnedReply() {
        val synthesis = _uiState.value.summary?.synthesis ?: return
        val selectedImport = _uiState.value.imports.firstOrNull { it.importId == _uiState.value.selectedImportId }
        val draft = com.example.commentanalyzer.domain.synthesis.PinnedReplyFormatter.format(
            synthesis = synthesis,
            videoTitle = selectedImport?.videoTitle
        )
        _uiState.update {
            it.copy(
                pinnedReplyDraft = draft.text,
                pinnedReplySupportingEvidenceIds = draft.supportingEvidenceCommentIds,
                pinnedReplyOpen = true
            )
        }
    }

    fun updatePinnedReplyDraft(text: String) {
        _uiState.update { it.copy(pinnedReplyDraft = text) }
    }

    fun dismissPinnedReply() {
        _uiState.update { it.copy(pinnedReplyOpen = false) }
    }

    fun showPinnedReplyEvidence() {
        val ids = _uiState.value.pinnedReplySupportingEvidenceIds
        if (ids.isNotEmpty()) {
            showEvidence("Pinned Reply Evidence", ids)
        }
    }

    fun getFollowUpBriefMarkdown(): String? {
        val synthesis = _uiState.value.summary?.synthesis ?: return null
        val selectedImport = _uiState.value.imports.firstOrNull { it.importId == _uiState.value.selectedImportId }
        return com.example.commentanalyzer.domain.synthesis.FollowUpBriefFormatter.format(
            videoTitle = selectedImport?.videoTitle,
            channelTitle = selectedImport?.channelTitle,
            videoId = selectedImport?.videoId,
            synthesis = synthesis
        )
    }

    fun isRemoteProcessingReady(configVersion: Long? = _uiState.value.selectedConfigVersion): Boolean {
        if (configVersion == null) return false
        return runCatching {
            val hasOpenRouter = !app.secureCredentialStore.getApiKey(CommentAnalyzerApplication.OPENROUTER_API_KEY_NAME).isNullOrBlank()
            val hasConsent = app.secureCredentialStore.getBoolean(
                CommentAnalyzerApplication.OPENROUTER_CONSENT_KEY,
                false
            )
            val hasTypeSafe = !app.secureCredentialStore.getApiKey(CommentAnalyzerApplication.TYPESAFE_API_KEY_NAME).isNullOrBlank()
            computeConfigReadiness(configVersion, hasOpenRouter, hasTypeSafe, hasConsent)
        }.getOrDefault(false)
    }

    private data class SummaryRequest(val importId: String?, val configVersion: Long?)

    private data class WorkSelection(val importId: String?, val configVersion: Long?)

    private data class WorkObservation(
        val infos: List<WorkInfo>,
        val processing: AnalysisProcessingCounts?
    )
}

internal fun filterAndOrderEvidenceComments(
    rawComments: List<CommentEntity>,
    importId: String,
    citedCommentIds: List<String>
): List<CommentEntity> {
    val matchingByLocalId = rawComments
        .filter { it.importId == importId }
        .associateBy { it.localId }
    return citedCommentIds.mapNotNull { matchingByLocalId[it] }
}

internal fun computeConfigReadiness(
    configVersion: Long?,
    hasOpenRouter: Boolean,
    hasTypeSafe: Boolean,
    hasConsent: Boolean
): Boolean {
    if (configVersion == null) return false
    return if (configVersion >= 3L) {
        hasOpenRouter && hasConsent
    } else {
        hasOpenRouter && hasTypeSafe && hasConsent
    }
}

internal fun classifyAnalysisCompletion(
    processing: AnalysisProcessingCounts
): AnalysisActionMessage? = when {
    processing.completed > 0 && processing.permanentlyFailed > 0 ->
        AnalysisActionMessage.PARTIAL
    processing.completed > 0 -> AnalysisActionMessage.SUCCEEDED
    processing.permanentlyFailed > 0 -> AnalysisActionMessage.ANALYSIS_FAILED
    processing.retrying > 0 || processing.inProgress > 0 -> null
    else -> AnalysisActionMessage.NO_ELIGIBLE_COMMENTS
}

internal class AnalysisActionController(
    private val isReady: (configVersion: Long) -> Boolean,
    private val enqueue: (String, Long) -> Unit,
    private val onStateChanged: (String, Long, Boolean, AnalysisActionMessage?) -> Unit
) {
    private val activeScopes = mutableSetOf<Pair<String, Long>>()

    fun start(importId: String, configVersion: Long): Boolean {
        val scope = importId to configVersion
        if (scope in activeScopes) return false
        if (!isReady(configVersion)) {
            onStateChanged(importId, configVersion, false, AnalysisActionMessage.NOT_READY)
            return false
        }
        activeScopes += scope
        onStateChanged(importId, configVersion, true, null)
        try {
            enqueue(importId, configVersion)
        } catch (_: Exception) {
            activeScopes -= scope
            onStateChanged(importId, configVersion, false, AnalysisActionMessage.FAILED)
            return false
        }
        return true
    }

    fun onWorkUpdate(
        importId: String,
        configVersion: Long,
        active: Boolean,
        message: AnalysisActionMessage?
    ) {
        val scope = importId to configVersion
        if (active) activeScopes += scope else activeScopes -= scope
        onStateChanged(importId, configVersion, active, message)
    }
}
