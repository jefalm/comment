package com.example.commentanalyzer.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.commentanalyzer.data.local.entity.CommentEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceBottomSheet(
    sheetState: EvidenceSheetState,
    onDismissRequest: () -> Unit,
    onJumpToThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val modalState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = modalState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = sheetState.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Showing ${sheetState.comments.size} of ${sheetState.totalCitedCount} cited comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = sheetState.comments,
                    key = { it.localId }
                ) { comment ->
                    EvidenceCommentCard(
                        comment = comment,
                        allComments = sheetState.comments,
                        onJumpToThread = {
                            onDismissRequest()
                            onJumpToThread(comment.localId)
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun EvidenceCommentCard(
    comment: CommentEntity,
    allComments: List<CommentEntity>,
    onJumpToThread: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.authorDisplayName?.takeIf(String::isNotBlank) ?: "Anonymous",
                    style = MaterialTheme.typography.titleSmall
                )
                comment.publishedAtEpochMillis?.let { timestamp ->
                    val formatted = remember(timestamp) {
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(timestamp))
                    }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Likes: ${comment.likeCount ?: 0}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Replies: ${comment.declaredReplyCount ?: 0}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (comment.parentSourceCommentId != null) {
                val parent = allComments.firstOrNull {
                    it.sourceCommentId == comment.parentSourceCommentId || it.localId == comment.parentSourceCommentId
                }
                val parentSnippet = if (parent != null) {
                    val preview = if (parent.originalText.length > 100) {
                        parent.originalText.take(100) + "..."
                    } else {
                        parent.originalText
                    }
                    "Replying to ${parent.authorDisplayName ?: "user"}: \"$preview\""
                } else {
                    "Reply to ${comment.parentSourceCommentId}"
                }
                Text(
                    text = parentSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Text(
                text = comment.originalText,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onJumpToThread) {
                    Text("Jump to Thread")
                }
            }
        }
    }
}
