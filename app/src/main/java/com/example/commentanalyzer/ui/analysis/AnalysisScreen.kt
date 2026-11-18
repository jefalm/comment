package com.example.commentanalyzer.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.commentanalyzer.R
import com.example.commentanalyzer.data.local.AnalysisSummary
import com.example.commentanalyzer.domain.analysis.formatAnalysisForClipboard
import java.util.Locale

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onNavigateToComments: (importId: String, commentId: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val persistedWorkInProgress = (state.summary?.processing?.inProgress ?: 0L) > 0
    val selectedImport = state.imports.firstOrNull { it.importId == state.selectedImportId }
    val clipboardManager = LocalClipboardManager.current
    var showCopiedConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.analysis_title), style = MaterialTheme.typography.headlineSmall)
        if (state.imports.isEmpty() && !state.isLoading) {
            Text(stringResource(R.string.analysis_no_imports), style = MaterialTheme.typography.bodyLarge)
        } else if (state.imports.isNotEmpty()) {
            ImportSelector(state, viewModel)
            if (state.configVersions.size > 1) {
                ConfigSelector(state, viewModel)
            }
            if (state.selectedImportId != null && state.selectedConfigVersion != null) {
                if (state.isRemoteProcessingReady) {
                    Button(
                        onClick = viewModel::analyzeSelectedImport,
                        enabled = !state.isAnalysisRunning && !persistedWorkInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.analysis_start))
                    }
                } else {
                    Text(
                        stringResource(R.string.analysis_readiness_required),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (state.isAnalysisRunning || persistedWorkInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.analysis_running))
            }
            state.analysisActionMessage?.let { message ->
                val text = when (message) {
                    AnalysisActionMessage.NOT_READY,
                    AnalysisActionMessage.REMOTE_BLOCKED ->
                        stringResource(R.string.analysis_readiness_required)
                    AnalysisActionMessage.NO_ELIGIBLE_COMMENTS ->
                        stringResource(R.string.analysis_no_eligible_comments)
                    AnalysisActionMessage.SUCCEEDED ->
                        stringResource(R.string.analysis_completed_message)
                    AnalysisActionMessage.PARTIAL ->
                        stringResource(R.string.analysis_partial_message)
                    AnalysisActionMessage.ANALYSIS_FAILED ->
                        stringResource(R.string.analysis_persisted_failure_message)
                    AnalysisActionMessage.FAILED -> stringResource(R.string.analysis_action_failed)
                    AnalysisActionMessage.CANCELLED -> stringResource(R.string.analysis_cancelled)
                }
                Text(
                    text,
                    color = if (message == AnalysisActionMessage.FAILED ||
                        message == AnalysisActionMessage.ANALYSIS_FAILED ||
                        message == AnalysisActionMessage.NOT_READY ||
                        message == AnalysisActionMessage.REMOTE_BLOCKED
                    ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            val synthesis = state.summary?.synthesis
            if (synthesis != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = viewModel::openAskComments,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ask Comments")
                    }
                    Button(
                        onClick = viewModel::openPinnedReply,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Draft Pinned Reply")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val brief = viewModel.getFollowUpBriefMarkdown()
                            if (brief != null) {
                                clipboardManager.setText(AnnotatedString(brief))
                                showCopiedConfirmation = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Follow-Up Brief")
                    }
                    OutlinedButton(
                        onClick = {
                            val report = formatAnalysisForClipboard(
                                videoTitle = selectedImport?.videoTitle,
                                publisher = selectedImport?.channelTitle,
                                videoId = selectedImport?.videoId,
                                synthesis = synthesis
                            )
                            clipboardManager.setText(AnnotatedString(report))
                            showCopiedConfirmation = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.analysis_copy_action))
                    }
                }
            }
            if (showCopiedConfirmation) {
                Text(
                    stringResource(R.string.analysis_copied_confirmation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            when {
                state.hasError -> Text(
                    stringResource(R.string.analysis_load_error),
                    color = MaterialTheme.colorScheme.error
                )
                state.configVersions.isEmpty() && !state.isLoading ->
                    Text(stringResource(R.string.analysis_no_config))
                state.isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.summary != null -> AnalysisSummaryContent(
                    summary = requireNotNull(state.summary),
                    onShowEvidence = { title, ids -> viewModel.showEvidence(title, ids) }
                )
            }
        } else if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    state.evidenceSheetState?.let { sheetState ->
        EvidenceBottomSheet(
            sheetState = sheetState,
            onDismissRequest = viewModel::dismissEvidence,
            onJumpToThread = { commentId ->
                viewModel.dismissEvidence()
                state.selectedImportId?.let { importId ->
                    onNavigateToComments(importId, commentId)
                }
            }
        )
    }

    if (state.askCommentsOpen) {
        AskCommentsBottomSheet(
            question = state.question,
            onQuestionChange = viewModel::updateQuestion,
            suggestedQuestions = state.suggestedQuestions,
            onAsk = { q -> viewModel.askQuestion(q) },
            isLoading = state.qaLoading,
            answer = state.qaAnswer,
            citedCommentIds = state.qaCitedCommentIds,
            insufficientEvidence = state.qaInsufficientEvidence,
            onShowEvidence = viewModel::showQaEvidence,
            onDismissRequest = viewModel::dismissAskComments
        )
    }

    if (state.pinnedReplyOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissPinnedReply,
            title = { Text("Draft Pinned Reply") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Editable creator draft. Grounded in audience feedback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = state.pinnedReplyDraft ?: "",
                        onValueChange = viewModel::updatePinnedReplyDraft,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 14
                    )
                    if (state.pinnedReplySupportingEvidenceIds.isNotEmpty()) {
                        OutlinedButton(
                            onClick = viewModel::showPinnedReplyEvidence,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View evidence (${state.pinnedReplySupportingEvidenceIds.size})")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val draft = state.pinnedReplyDraft ?: ""
                        clipboardManager.setText(AnnotatedString(draft))
                        showCopiedConfirmation = true
                        viewModel.dismissPinnedReply()
                    }
                ) {
                    Text("Copy Draft")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissPinnedReply) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ImportSelector(state: AnalysisUiState, viewModel: AnalysisViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.imports.firstOrNull { it.importId == state.selectedImportId }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.title ?: stringResource(R.string.analysis_select_import))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.imports.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = {
                        viewModel.selectImport(option.importId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfigSelector(state: AnalysisUiState, viewModel: AnalysisViewModel) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text(
            state.selectedConfigVersion?.let { stringResource(R.string.analysis_config_version, it) }
                ?: stringResource(R.string.analysis_select_config)
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        state.configVersions.forEach { version ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.analysis_config_version, version)) },
                onClick = {
                    viewModel.selectConfigVersion(version)
                    expanded = false
                }
            )
        }
    }
}

@Composable
internal fun AnalysisSummaryContent(
    summary: AnalysisSummary,
    onShowEvidence: (title: String, citedCommentIds: List<String>) -> Unit = { _, _ -> }
) {
    val processing = summary.processing
    val synthesis = summary.synthesis
    val isV3 = synthesis != null && synthesis.schemaVersion >= 3

    // 1. AUDIENCE PULSE (App-computed persisted metrics)
    val totalImported = processing.totalComments
    val filteredNoise = processing.locallyRejected + processing.jevRejected
    val analyzedEvidence = processing.completed

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.analysis_audience_pulse),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(R.string.analysis_coverage_summary, totalImported, filteredNoise, analyzedEvidence),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isV3 && synthesis != null) {
                    Text(
                        "Active Debates: ${synthesis.debates.size} · Questions & Requests: ${synthesis.questionsAndRequests.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 2. EXECUTIVE SUMMARY
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Executive Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (synthesis != null) {
                    Text(
                        synthesis.executiveSummary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        "Analysis has not generated synthesis yet. Run analysis to generate GLM synthesis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (synthesis != null && isV3) {
        // V3 SECTIONS:
        // 3. THEMES
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Themes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.themesV3.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No major themes recorded.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            } else {
                synthesis.themesV3.forEach { theme ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(theme.title, style = MaterialTheme.typography.titleSmall)
                            Text(theme.takeaway, style = MaterialTheme.typography.bodyMedium)
                            if (!theme.representativeQuote.isNullOrBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "“${theme.representativeQuote}”",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontStyle = FontStyle.Italic
                                        )
                                        Text(
                                            "— ${theme.representativeAuthor?.takeIf(String::isNotBlank) ?: "Audience voice"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "Supported by ${theme.evidenceCount} evidence comments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (theme.citedCommentIds.isNotEmpty()) {
                                    OutlinedButton(onClick = { onShowEvidence(theme.title, theme.citedCommentIds) }) {
                                        Text("View evidence (${theme.evidenceCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. DEBATES
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Debates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.debates.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No active debates recorded.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            } else {
                synthesis.debates.forEach { debate ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(debate.topic, style = MaterialTheme.typography.titleSmall)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Side A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        Text(debate.sideASummary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Side B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                        Text(debate.sideBSummary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "Supported by ${debate.evidenceCount} evidence comments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (debate.citedCommentIds.isNotEmpty()) {
                                    OutlinedButton(onClick = { onShowEvidence(debate.topic, debate.citedCommentIds) }) {
                                        Text("View evidence (${debate.evidenceCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. QUESTIONS & REQUESTS
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Questions & Requests", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.questionsAndRequests.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No questions or requests recorded.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            } else {
                synthesis.questionsAndRequests.forEach { q ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(q.questionOrRequest, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "Supported by ${q.evidenceCount} evidence comments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (q.citedCommentIds.isNotEmpty()) {
                                    OutlinedButton(onClick = { onShowEvidence(q.questionOrRequest, q.citedCommentIds) }) {
                                        Text("View evidence (${q.evidenceCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. BLIND SPOTS & CORRECTIONS
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Blind Spots & Corrections", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.blindSpotsAndCorrections.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No blind spots recorded.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            } else {
                synthesis.blindSpotsAndCorrections.forEach { b ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(b.claimOrCorrection, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "Supported by ${b.evidenceCount} evidence comments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (b.citedCommentIds.isNotEmpty()) {
                                    OutlinedButton(onClick = { onShowEvidence(b.claimOrCorrection, b.citedCommentIds) }) {
                                        Text("View evidence (${b.evidenceCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. INSIDER PERSPECTIVES
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Insider Perspectives", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.insiderPerspectives.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No insider perspectives recorded.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            } else {
                synthesis.insiderPerspectives.forEach { insider ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(insider.perspective, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "Supported by ${insider.evidenceCount} evidence comments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (insider.citedCommentIds.isNotEmpty()) {
                                    OutlinedButton(onClick = { onShowEvidence(insider.perspective, insider.citedCommentIds) }) {
                                        Text("View evidence (${insider.evidenceCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. RECOMMENDED FOLLOW-UPS
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recommended Follow-Ups", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.recommendedFollowUps.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No follow-up recommendations recorded.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            } else {
                synthesis.recommendedFollowUps.forEach { followUp ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(followUp.action, style = MaterialTheme.typography.titleSmall)
                            Text(followUp.rationale, style = MaterialTheme.typography.bodyMedium)
                            if (followUp.citedCommentIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Supported by ${followUp.evidenceCount} evidence comments",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(onClick = { onShowEvidence(followUp.action, followUp.citedCommentIds) }) {
                                        Text("View evidence (${followUp.evidenceCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (synthesis != null && !isV3) {
        // MINIMAL LEGACY V2 PRESENTATION:
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Key Themes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            synthesis.themes.forEach { theme ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(theme.title, style = MaterialTheme.typography.titleSmall)
                        Text(theme.description, style = MaterialTheme.typography.bodyMedium)
                        if (theme.citedCommentIds.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(onClick = { onShowEvidence(theme.title, theme.citedCommentIds) }) {
                                    Text("View evidence (${theme.citedCommentIds.size})")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (synthesis.agreements.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agreements", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        synthesis.agreements.forEachIndexed { index, agreement ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(agreement.topic, style = MaterialTheme.typography.titleSmall)
                                Text(agreement.description, style = MaterialTheme.typography.bodyMedium)
                                if (agreement.citedCommentIds.isNotEmpty()) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        OutlinedButton(onClick = { onShowEvidence(agreement.topic, agreement.citedCommentIds) }) {
                                            Text("View evidence (${agreement.citedCommentIds.size})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (synthesis.contrastingPerspectives.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Contrasting Perspectives", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                synthesis.contrastingPerspectives.forEach { p ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${p.perspectiveA} vs ${p.perspectiveB}", style = MaterialTheme.typography.titleSmall)
                            Text(p.description, style = MaterialTheme.typography.bodyMedium)
                            if (p.citedCommentIds.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    OutlinedButton(onClick = { onShowEvidence("${p.perspectiveA} vs ${p.perspectiveB}", p.citedCommentIds) }) {
                                        Text("View evidence (${p.citedCommentIds.size})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (synthesis.notableInsights.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notable Insights", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                synthesis.notableInsights.forEach { insight ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(insight.insight, style = MaterialTheme.typography.bodyMedium)
                            if (insight.citedCommentIds.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    OutlinedButton(onClick = { onShowEvidence(insight.insight, insight.citedCommentIds) }) {
                                        Text("View evidence (${insight.citedCommentIds.size})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // TECHNICAL / PROCESSING DETAIL (Collapsed / Secondary section)
    var showProcessingDetails by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { showProcessingDetails = !showProcessingDetails },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showProcessingDetails) "Hide Processing Details" else "Processing Details")
        }

        if (showProcessingDetails) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.analysis_processing), style = MaterialTheme.typography.titleSmall)
                    MetricRow(stringResource(R.string.analysis_total), processing.totalComments.toString())
                    MetricRow(stringResource(R.string.analysis_completed), processing.completed.toString())
                    MetricRow(stringResource(R.string.analysis_locally_rejected), processing.locallyRejected.toString())
                    MetricRow(stringResource(R.string.analysis_jev_rejected), processing.jevRejected.toString())
                    MetricRow(stringResource(R.string.analysis_retrying), processing.retrying.toString())
                    MetricRow(stringResource(R.string.analysis_failed), processing.permanentlyFailed.toString())

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(stringResource(R.string.analysis_usage), style = MaterialTheme.typography.titleSmall)
                    MetricRow(
                        stringResource(R.string.analysis_input_tokens),
                        summary.usage.inputTokens?.toString() ?: stringResource(R.string.analysis_unknown)
                    )
                    MetricRow(
                        stringResource(R.string.analysis_output_tokens),
                        summary.usage.outputTokens?.toString() ?: stringResource(R.string.analysis_unknown)
                    )
                    MetricRow(
                        stringResource(R.string.analysis_known_cost),
                        summary.usage.costUsd?.let { String.format(Locale.US, "$%.6f", it) }
                            ?: stringResource(R.string.analysis_unknown)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
