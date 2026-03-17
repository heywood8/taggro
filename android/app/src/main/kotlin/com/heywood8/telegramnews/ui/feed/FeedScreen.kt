package com.heywood8.telegramnews.ui.feed

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.ui.common.ChannelIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: FeedViewModel = hiltViewModel()) {
    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val showChannelIcons by viewModel.showChannelIcons.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val unreadCounts by viewModel.unreadCounts.collectAsStateWithLifecycle()
    val totalUnreadCount by viewModel.totalUnreadCount.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val lazyListState = rememberLazyListState()

    // Dwell-time read detection: poll every 200ms, mark as read after 500ms continuous visibility
    val dwellStart = remember { HashMap<Long, Long>() }
    val markedRead = remember { HashSet<Long>() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            val now = System.currentTimeMillis()
            val visibleIds = lazyListState.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? Long }
                .toSet()

            // Track newly visible items
            visibleIds.forEach { id ->
                if (id !in dwellStart && id !in markedRead) {
                    dwellStart[id] = now
                }
            }
            // Remove items that scrolled away
            val gone = dwellStart.keys.filter { it !in visibleIds }
            gone.forEach { dwellStart.remove(it) }

            // Mark items that have been visible for >= 500ms
            val ready = dwellStart.entries.filter { (_, start) -> now - start >= 500L }
            ready.forEach { (id, _) ->
                viewModel.markRead(id)
                markedRead.add(id)
                dwellStart.remove(id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News Feed") },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Channel filter chips with unread count badges and long-press to mark read
            if (subscriptions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val allLabel = if (totalUnreadCount > 0) "All ($totalUnreadCount)" else "All"
                    FilterChip(
                        selected = selectedChannel == null,
                        onClick = { viewModel.selectChannel(null) },
                        label = { Text(allLabel) },
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { viewModel.markAllRead() })
                        },
                    )
                    subscriptions.filter { it.active }.forEach { sub ->
                        val count = unreadCounts[sub.channel] ?: 0
                        val chipLabel = if (count > 0) "${sub.channel} ($count)" else sub.channel
                        FilterChip(
                            selected = selectedChannel == sub.channel,
                            onClick = { viewModel.selectChannel(sub.channel) },
                            label = { Text(chipLabel) },
                            modifier = Modifier.pointerInput(sub.channel) {
                                detectTapGestures(onLongPress = { viewModel.markChannelRead(sub.channel) })
                            },
                        )
                    }
                }
            }

            var selectedMessage by remember { mutableStateOf<Message?>(null) }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No messages yet.\nAdd channels to start receiving news.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            FeedItem(
                                message = message,
                                showChannelIcons = showChannelIcons,
                                onClick = { selectedMessage = message },
                            )
                        }
                    }
                }
            }

            if (selectedMessage != null) {
                ArticleSheet(
                    message = selectedMessage!!,
                    onDismiss = { selectedMessage = null },
                )
            }
        }
    }
}

@Composable
private fun FeedItem(message: Message, showChannelIcons: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (message.isRead) 0.6f else 1f),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    if (showChannelIcons) {
                        ChannelIcon(name = message.channelTitle.ifBlank { message.channel })
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = message.channelTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleSheet(message: Message, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = message.channelTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

private fun formatTimestamp(epochSeconds: Long): String {
    val date = Date(epochSeconds * 1000)
    val now = System.currentTimeMillis()
    return if (now - date.time < 86_400_000L) timeFormat.format(date) else dateFormat.format(date)
}
