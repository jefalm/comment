package com.example.commentanalyzer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.commentanalyzer.domain.model.CommentStatistics
import com.example.commentanalyzer.domain.model.UiComment
import com.example.commentanalyzer.domain.model.VideoDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentViewerScreen(
    viewModel: CommentViewerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube Comment Analyzer") },
                actions = {
                    IconButton(onClick = { viewModel.loadExportFile() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading YouTube comment export...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                state.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.errorMessage ?: "Unknown error",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { viewModel.loadExportFile() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                state.exportData != null -> {
                    val export = state.exportData!!

                    // Filter and Sort comments
                    val filteredComments = remember(export.comments, state.searchQuery, state.sortOption) {
                        var list = export.comments
                        if (state.searchQuery.isNotBlank()) {
                            val q = state.searchQuery.trim().lowercase()
                            list = list.filter {
                                it.text.lowercase().contains(q) ||
                                it.author.lowercase().contains(q)
                            }
                        }
                        when (state.sortOption) {
                            SortOption.LIKES_DESC -> list.sortedByDescending { it.likes }
                            SortOption.DATE_NEWEST -> list.sortedByDescending { it.publishedEpochMillis }
                            SortOption.DATE_OLDEST -> list.sortedBy { it.publishedEpochMillis }
                        }
                    }

                    // Paging
                    val totalResults = filteredComments.size
                    val pageSize = state.pageSize
                    val totalPages = if (totalResults == 0) 1 else (totalResults + pageSize - 1) / pageSize
                    val currentPage = state.currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                    val pagedComments = remember(filteredComments, currentPage, pageSize) {
                        val start = currentPage * pageSize
                        val end = (start + pageSize).coerceAtMost(totalResults)
                        if (start < totalResults) filteredComments.subList(start, end) else emptyList()
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Video Details Section
                        item {
                            VideoDetailsCard(video = export.videoDetails)
                        }

                        // Statistics Section
                        item {
                            StatisticsCard(stats = export.statistics)
                        }

                        // Controls Section (Search, Sort, Page Size)
                        item {
                            ControlsSection(
                                searchQuery = state.searchQuery,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                sortOption = state.sortOption,
                                onSortOptionChange = { viewModel.setSortOption(it) },
                                pageSize = state.pageSize,
                                onPageSizeChange = { viewModel.setPageSize(it) },
                                showingCount = pagedComments.size,
                                totalCount = totalResults
                            )
                        }

                        // Comments List or Empty state
                        if (pagedComments.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No comments found matching your query.", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        } else {
                            items(pagedComments, key = { it.id + it.author }) { comment ->
                                CommentItemCard(
                                    comment = comment,
                                    isExpanded = state.expandedReplies.contains(comment.id),
                                    onToggleReplies = { viewModel.toggleReplies(comment.id) }
                                )
                            }

                            // Pagination footer
                            if (totalPages > 1) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { viewModel.setPage(currentPage - 1) },
                                            enabled = currentPage > 0
                                        ) {
                                            Text("Previous")
                                        }
                                        Text("Page ${currentPage + 1} of $totalPages", style = MaterialTheme.typography.bodyMedium)
                                        Button(
                                            onClick = { viewModel.setPage(currentPage + 1) },
                                            enabled = currentPage < totalPages - 1
                                        ) {
                                            Text("Next")
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
}

@Composable
fun VideoDetailsCard(video: VideoDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = video.channel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "👀 ${video.views} views", style = MaterialTheme.typography.bodySmall)
                Text(text = "👍 ${video.likes} likes", style = MaterialTheme.typography.bodySmall)
                Text(text = "💬 ${video.totalCommentsCount} comments", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Published: ${video.publishedAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun StatisticsCard(stats: CommentStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "▲ Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "Top-level", value = stats.topLevelComments.toString())
                StatItem(label = "Total Likes", value = stats.totalLikes.toString())
                StatItem(label = "Avg/Day", value = "%.1f".format(stats.avgCommentsPerDay))
                StatItem(label = "Likes/Com", value = "%.2f".format(stats.avgLikesPerComment))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    showingCount: Int,
    totalCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Filter comments by keyword or author...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort dropdown / buttons
            var sortExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { sortExpanded = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    val sortText = when (sortOption) {
                        SortOption.LIKES_DESC -> "Likes"
                        SortOption.DATE_NEWEST -> "Newest"
                        SortOption.DATE_OLDEST -> "Oldest"
                    }
                    Text("Sort: $sortText", style = MaterialTheme.typography.bodySmall)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Likes (Highest first)") },
                        onClick = { onSortOptionChange(SortOption.LIKES_DESC); sortExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Newest first)") },
                        onClick = { onSortOptionChange(SortOption.DATE_NEWEST); sortExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Oldest first)") },
                        onClick = { onSortOptionChange(SortOption.DATE_OLDEST); sortExpanded = false }
                    )
                }
            }

            // Page Size Selector
            var sizeExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { sizeExpanded = true }) {
                    Text("Page size: $pageSize", style = MaterialTheme.typography.bodySmall)
                }
                DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                    listOf(25, 50, 100, 500).forEach { size ->
                        DropdownMenuItem(
                            text = { Text("$size") },
                            onClick = { onPageSizeChange(size); sizeExpanded = false }
                        )
                    }
                }
            }
        }

        Text(
            text = "Showing $showingCount / $totalCount results",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun CommentItemCard(
    comment: UiComment,
    isExpanded: Boolean,
    onToggleReplies: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "#${comment.id}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = "Likes",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${comment.likes}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                val hint = comment.repliesHint
                if (!hint.isNullOrEmpty()) {
                    TextButton(onClick = onToggleReplies) {
                        Text(
                            text = if (isExpanded) "▲ Hide replies" else hint,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val hint = comment.repliesHint
            if (isExpanded && !hint.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Thread Replies ($hint)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Sample reply 1: Concordo plenamente com essa análise!",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Sample reply 2: Verdade, situação muito complicada no Brasil.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
