package com.example.commentanalyzer.ui.comments

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelationshipFilterChip(state, viewModel, CommentRelationshipFilter.ALL, R.string.comments_filter_all)
                RelationshipFilterChip(state, viewModel, CommentRelationshipFilter.TOP_LEVEL, R.string.comments_filter_top_level)
                RelationshipFilterChip(state, viewModel, CommentRelationshipFilter.REPLIES, R.string.comments_filter_replies)
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
                    comments[index]?.let { comment -> CommentRow(comment) }
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
private fun RelationshipFilterChip(
    state: CommentsUiState,
    viewModel: CommentsViewModel,
    filter: CommentRelationshipFilter,
    labelRes: Int
) {
    FilterChip(
        selected = state.relationship == filter,
        onClick = { viewModel.setRelationship(filter) },
        label = { Text(stringResource(labelRes)) }
    )
}

@Composable
private fun CommentRow(comment: CommentRowUiModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(comment.sourceLabelRes), style = MaterialTheme.typography.labelMedium)
                comment.likes?.let { Text(stringResource(R.string.comments_likes, it), style = MaterialTheme.typography.labelMedium) }
            }
            comment.author?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            comment.videoId?.let {
                Text(stringResource(R.string.comments_video_id, it), style = MaterialTheme.typography.bodySmall)
            }
            Text(comment.text, style = MaterialTheme.typography.bodyLarge)
            comment.parentCommentId?.let {
                Text(stringResource(R.string.comments_reply_to, it), style = MaterialTheme.typography.bodySmall)
            }
            comment.publishedAtEpochMillis?.let { timestamp ->
                val formatted = remember(timestamp) {
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(timestamp))
                }
                Text(formatted, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.comments_id, comment.displayId), style = MaterialTheme.typography.labelSmall)
        }
    }
}