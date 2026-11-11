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
import java.util.Locale

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            when {
                state.hasError -> Text(
                    stringResource(R.string.analysis_load_error),
                    color = MaterialTheme.colorScheme.error
                )
                state.configVersions.isEmpty() && !state.isLoading ->
                    Text(stringResource(R.string.analysis_no_config))
                state.isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.summary != null -> AnalysisSummaryContent(requireNotNull(state.summary))
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
private fun AnalysisSummaryContent(summary: AnalysisSummary) {
    val processing = summary.processing
    SummarySection(stringResource(R.string.analysis_processing)) {
        MetricRow(stringResource(R.string.analysis_total), processing.totalComments.toString())
        MetricRow(stringResource(R.string.analysis_completed), processing.completed.toString())
        MetricRow(stringResource(R.string.analysis_locally_rejected), processing.locallyRejected.toString())
        MetricRow(stringResource(R.string.analysis_jev_rejected), processing.jevRejected.toString())
        MetricRow(stringResource(R.string.analysis_retrying), processing.retrying.toString())
        MetricRow(stringResource(R.string.analysis_failed), processing.permanentlyFailed.toString())
    }

    SummarySection(stringResource(R.string.analysis_results)) {
        DistributionRows(
            title = stringResource(R.string.analysis_categories),
            emptyLabel = stringResource(R.string.analysis_no_results),
            rows = summary.categories.map { it.value to it.count }
        )
        DistributionRows(
            title = stringResource(R.string.analysis_sentiment),
            emptyLabel = stringResource(R.string.analysis_no_results),
            rows = summary.sentiments.map { it.value to it.count }
        )
    }

    SummarySection(stringResource(R.string.analysis_usage)) {
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
private fun SummarySection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
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
