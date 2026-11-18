package com.example.commentanalyzer.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.commentanalyzer.R
import com.example.commentanalyzer.ui.components.UiPresentationState
import com.example.commentanalyzer.ui.components.UiStatusContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CommentsScreen(viewModel: CommentsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comments = viewModel.comments.collectAsLazyPagingItems()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.comments_title), style = MaterialTheme.typography.headlineSmall)
        if (state.scopes.isEmpty()) {
            UiStatusContent(
                status = UiPresentationState.Empty(R.string.comments_no_imports),
                modifier = Modifier.weight(1f)
            )
        } else {
            ScopeSelector(state, viewModel)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.comments_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.comments_clear_search))
                        }
                    }
                }
            )

            // View modes and sorts
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewModeChip(state, viewModel, CommentViewMode.THREADS, stringResource(R.string.comments_view_threads))
                ViewModeChip(state, viewModel, CommentViewMode.FLAT, stringResource(R.string.comments_view_flat))
                Spacer(modifier = Modifier.weight(1f))
                SortModeDropdown(state, viewModel)
            }

            // Quick filters & clear filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.referencedInSynthesisOnly,
                    onClick = viewModel::toggleReferencedInSynthesis,
                    label = { Text(stringResource(R.string.comments_filter_referenced_in_synthesis)) }
                )
                FilterChip(
                    selected = state.hasRepliesOnly,
                    onClick = viewModel::toggleHasRepliesOnly,
                    label = { Text(stringResource(R.string.comments_filter_has_replies)) }
                )
                FilterChip(
                    selected = state.questionsOnly,
                    onClick = viewModel::toggleQuestionsOnly,
                    label = { Text(stringResource(R.string.comments_filter_questions)) }
                )
            }

            if (state.activeFilterCount > 0) {
                OutlinedButton(
                    onClick = viewModel::clearAllFilters,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Filters (${state.activeFilterCount})")
                }
            }

            if (comments.loadState.refresh is LoadState.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (comments.itemCount == 0 && comments.loadState.refresh is LoadState.NotLoading) {
                    item {
                        Text(
                            stringResource(R.string.comments_no_matching),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                items(
                    count = comments.itemCount,
                    key = comments.itemKey { it.stableId }
                ) { index ->
                    comments[index]?.let { comment ->
                        val isThreadReply = state.viewMode == CommentViewMode.THREADS &&
                            !comment.parentCommentId.isNullOrBlank()
                        CommentRow(
                            comment = comment,
                            isThreadReply = isThreadReply,
                            searchQuery = state.searchQuery
                        )
                    }
                }
                if (comments.loadState.append is LoadState.Loading) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
                if (comments.loadState.refresh is LoadState.Error || comments.loadState.append is LoadState.Error) {
                    item {
                        Text(
                            stringResource(R.string.comments_load_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeSelector(state: CommentsUiState, viewModel: CommentsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.scopes.firstOrNull { it.importId == state.selectedImportId }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected?.let { "${it.title} · ${stringResource(it.sourceLabelRes)}" }
                    ?: stringResource(R.string.comments_select_import)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.scopes.forEach { scope ->
                DropdownMenuItem(
                    text = { Text("${scope.title} · ${stringResource(scope.sourceLabelRes)}") },
                    onClick = {
                        viewModel.selectImport(scope.importId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ViewModeChip(
    state: CommentsUiState,
    viewModel: CommentsViewModel,
    mode: CommentViewMode,
    label: String
) {
    FilterChip(
        selected = state.viewMode == mode,
        onClick = { viewModel.setViewMode(mode) },
        label = { Text(label) }
    )
}

@Composable
private fun SortModeDropdown(state: CommentsUiState, viewModel: CommentsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (state.sortMode) {
        CommentSortMode.MOST_RELEVANT -> stringResource(R.string.comments_sort_most_relevant)
        CommentSortMode.MOST_ENGAGED -> stringResource(R.string.comments_sort_most_engaged)
        CommentSortMode.MOST_DEBATED -> stringResource(R.string.comments_sort_most_debated)
        CommentSortMode.NEWEST -> stringResource(R.string.comments_sort_newest)
    }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Sort: $label")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CommentSortMode.entries.forEach { mode ->
                val modeLabel = when (mode) {
                    CommentSortMode.MOST_RELEVANT -> stringResource(R.string.comments_sort_most_relevant)
                    CommentSortMode.MOST_ENGAGED -> stringResource(R.string.comments_sort_most_engaged)
                    CommentSortMode.MOST_DEBATED -> stringResource(R.string.comments_sort_most_debated)
                    CommentSortMode.NEWEST -> stringResource(R.string.comments_sort_newest)
                }
                DropdownMenuItem(
                    text = { Text(modeLabel) },
                    onClick = {
                        viewModel.setSortMode(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentRowUiModel,
    isThreadReply: Boolean,
    searchQuery: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = if (isThreadReply) 16.dp else 0.dp)
    ) {
        if (isThreadReply) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Card(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(comment.sourceLabelRes), style = MaterialTheme.typography.labelMedium)
                    comment.likes?.let {
                        Text(stringResource(R.string.comments_likes, it), style = MaterialTheme.typography.labelMedium)
                    }
                }
                comment.author?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                comment.videoId?.let {
                    Text(stringResource(R.string.comments_video_id, it), style = MaterialTheme.typography.bodySmall)
                }

                val highlightedText = remember(comment.text, searchQuery) {
                    highlightSearchQuery(comment.text, searchQuery)
                }
                Text(highlightedText, style = MaterialTheme.typography.bodyLarge)

                if (comment.isCitedInAnalysis) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.comments_filter_cited_pill),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                comment.publishedAtEpochMillis?.let { timestamp ->
                    val formatted = remember(timestamp) {
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(timestamp))
                    }
                    Text(formatted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun highlightSearchQuery(text: String, query: String): AnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty() || !text.contains(cleanQuery, ignoreCase = true)) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = cleanQuery.lowercase()
        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex == -1) {
                append(text.substring(currentIndex))
                break
            }
            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }
            val matchEnd = matchIndex + cleanQuery.length
            pushStyle(SpanStyle(background = androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.35f)))
            append(text.substring(matchIndex, matchEnd))
            pop()
            currentIndex = matchEnd
        }
    }
}
