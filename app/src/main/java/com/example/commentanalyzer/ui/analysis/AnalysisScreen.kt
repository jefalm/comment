package com.example.commentanalyzer.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.commentanalyzer.R
import com.example.commentanalyzer.data.local.AnalysisSummary
import com.example.commentanalyzer.domain.model.SynthesisResult
import java.util.Locale

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onNavigateToComments: (importId: String, commentId: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val persistedWorkInProgress = (state.summary?.processing?.inProgress ?: 0L) > 0

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
            if (state.configVersions.isNotEmpty()) {
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
                    importId = state.selectedImportId,
                    onNavigateToComments = onNavigateToComments
                )
            }
        } else if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
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
    importId: String?,
    onNavigateToComments: (String, String?) -> Unit
) {
    val processing = summary.processing
    val synthesis = summary.synthesis

    // 2. EXECUTIVE SUMMARY (Hero insight section, visually prominent)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Executive Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
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

    if (synthesis != null) {
        // 3. KEY THEMES
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Key Themes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.themes.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No major themes recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                synthesis.themes.forEach { theme ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(theme.title, style = MaterialTheme.typography.titleSmall)
                            Text(theme.description, style = MaterialTheme.typography.bodyMedium)
                            if (theme.citedCommentIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { importId?.let { onNavigateToComments(it, theme.citedCommentIds.first()) } }
                                    ) {
                                        Text("View evidence (${theme.citedCommentIds.size})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. AGREEMENTS
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Agreements", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.agreements.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No major agreements recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        synthesis.agreements.forEachIndexed { index, agreement ->
                            if (index > 0) {
                                androidx.compose.material3.HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(agreement.topic, style = MaterialTheme.typography.titleSmall)
                                Text(agreement.description, style = MaterialTheme.typography.bodyMedium)
                                if (agreement.citedCommentIds.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = { importId?.let { onNavigateToComments(it, agreement.citedCommentIds.first()) } }
                                        ) {
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

        // 5. CONTRASTING PERSPECTIVES
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Contrasting Perspectives", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.contrastingPerspectives.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No contrasting perspectives recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                synthesis.contrastingPerspectives.forEach { perspective ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    perspective.perspectiveA,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    " vs ",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    perspective.perspectiveB,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                            Text(perspective.description, style = MaterialTheme.typography.bodyMedium)
                            if (perspective.citedCommentIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { importId?.let { onNavigateToComments(it, perspective.citedCommentIds.first()) } }
                                    ) {
                                        Text("View evidence (${perspective.citedCommentIds.size})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. NOTABLE INSIGHTS
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Notable Insights", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.notableInsights.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No notable insights recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                synthesis.notableInsights.forEach { insight ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(insight.insight, style = MaterialTheme.typography.bodyMedium)
                            if (insight.citedCommentIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { importId?.let { onNavigateToComments(it, insight.citedCommentIds.first()) } }
                                    ) {
                                        Text("View evidence (${insight.citedCommentIds.size})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. ACTIONABLE FOLLOW-UPS
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Actionable Follow-Ups", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (synthesis.actionableFollowUps.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No actionable follow-ups recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        synthesis.actionableFollowUps.forEachIndexed { index, followUp ->
                            if (index > 0) {
                                androidx.compose.material3.HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${index + 1}. ${followUp.action}", style = MaterialTheme.typography.titleSmall)
                                Text(followUp.rationale, style = MaterialTheme.typography.bodyMedium)
                                if (followUp.citedCommentIds.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = { importId?.let { onNavigateToComments(it, followUp.citedCommentIds.first()) } }
                                        ) {
                                            Text("View evidence (${followUp.citedCommentIds.size})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 8. SUPPORTING DATA
    var showProcessingDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Supporting Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DistributionRows(
                    title = stringResource(R.string.analysis_categories),
                    emptyLabel = stringResource(R.string.analysis_no_results),
                    rows = summary.categories.map { it.value to it.count }
                )
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DistributionRows(
                    title = stringResource(R.string.analysis_sentiment),
                    emptyLabel = stringResource(R.string.analysis_no_results),
                    rows = summary.sentiments.map { it.value to it.count }
                )
            }
        }
    }

    // 9. TECHNICAL / PROCESSING DETAIL (Collapsed / Secondary section)
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

                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

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
private fun DistributionRows(
    title: String,
    emptyLabel: String,
    rows: List<Pair<String, Long>>
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (rows.isEmpty()) {
        Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
    } else {
        rows.forEach { (value, count) -> MetricRow(value, count.toString()) }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
