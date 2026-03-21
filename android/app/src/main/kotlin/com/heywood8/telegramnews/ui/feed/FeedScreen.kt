package com.heywood8.telegramnews.ui.feed

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.heywood8.telegramnews.domain.model.MediaType
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.PhotoLayout
import com.heywood8.telegramnews.ui.common.ChannelIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

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
    val photoLayout by viewModel.photoLayout.collectAsStateWithLifecycle()
    val getPhotoPath: suspend (Int) -> String? = remember { { viewModel.getPhotoPath(it) } }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val lazyListState = rememberLazyListState()

    // Hide separator on first scroll
    var showSeparator by remember { mutableStateOf(true) }
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.isScrollInProgress }
            .filter { it }
            .first()
        showSeparator = false
    }

    // Dwell-time read detection: poll every 200ms, mark as read after 500ms continuous visibility
    val dwellStart = remember { HashMap<Long, Long>() }
    val markedRead = remember { HashSet<Long>() }
    LaunchedEffect(lazyListState) {
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
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation()
                                } ?: viewModel.markAllRead()
                            }
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
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation()
                                    } ?: viewModel.markChannelRead(sub.channel)
                                }
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
                    val unread = messages.filter { !it.isRead }
                    val read = messages.filter { it.isRead }
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(read.reversed(), key = { it.id }) { message ->
                            FeedItem(
                                message = message,
                                showChannelIcons = showChannelIcons,
                                photoLayout = photoLayout,
                                showPhoto = subscriptions.find { it.channel == message.channel }?.includePhotos ?: false,
                                getPhotoPath = getPhotoPath,
                                onClick = { selectedMessage = message },
                            )
                        }
                        if (showSeparator && read.isNotEmpty() && unread.isNotEmpty()) {
                            item(key = "separator") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    HorizontalDivider()
                                    Text(
                                        text = "New messages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        items(unread.reversed(), key = { it.id }) { message ->
                            FeedItem(
                                message = message,
                                showChannelIcons = showChannelIcons,
                                photoLayout = photoLayout,
                                showPhoto = subscriptions.find { it.channel == message.channel }?.includePhotos ?: false,
                                getPhotoPath = getPhotoPath,
                                onClick = { selectedMessage = message },
                            )
                        }
                    }
                }
            }

            if (selectedMessage != null) {
                ArticleSheet(
                    message = selectedMessage!!,
                    showPhoto = subscriptions.find { it.channel == selectedMessage!!.channel }?.includePhotos ?: false,
                    getPhotoPath = getPhotoPath,
                    onDismiss = { selectedMessage = null },
                )
            }
        }
    }
}

@Composable
private fun FeedItem(
    message: Message,
    showChannelIcons: Boolean,
    photoLayout: PhotoLayout,
    showPhoto: Boolean,
    getPhotoPath: suspend (Int) -> String?,
    onClick: () -> Unit,
) {
    val photoPaths by produceState<List<String>>(emptyList(), message.photoFileIds) {
        value = message.photoFileIds.mapNotNull { getPhotoPath(it) }
    }
    val effectivePhotoPaths = if (showPhoto) photoPaths else emptyList()
    val firstPhotoPath = effectivePhotoPaths.firstOrNull()
    val isAlbum = effectivePhotoPaths.size > 1

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        when {
            firstPhotoPath != null && !isAlbum && photoLayout == PhotoLayout.LEFT -> {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(java.io.File(firstPhotoPath))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(80.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
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
                        val bodyText = buildAnnotatedString { append(message.text) }
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (effectivePhotoPaths.isNotEmpty() && photoLayout == PhotoLayout.ABOVE) {
                        if (isAlbum) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp),
                            ) {
                                items(effectivePhotoPaths) { path ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(java.io.File(path))
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(160.dp),
                                    )
                                }
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(java.io.File(firstPhotoPath!!))
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                            )
                        }
                    }
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
                    val bodyText = if (message.mediaType == MediaType.PHOTO && message.text.isBlank() && effectivePhotoPaths.isEmpty()) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("[Photo]") }
                        }
                    } else {
                        buildAnnotatedString { append(message.text) }
                    }
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (effectivePhotoPaths.isNotEmpty() && photoLayout == PhotoLayout.BELOW) {
                        if (isAlbum) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .padding(top = 8.dp),
                            ) {
                                items(effectivePhotoPaths) { path ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(java.io.File(path))
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(160.dp),
                                    )
                                }
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(java.io.File(firstPhotoPath!!))
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleSheet(
    message: Message,
    showPhoto: Boolean,
    getPhotoPath: suspend (Int) -> String?,
    onDismiss: () -> Unit,
) {
    val photoPaths by produceState<List<String>>(emptyList(), message.photoFileIds) {
        value = message.photoFileIds.mapNotNull { getPhotoPath(it) }
    }
    val effectivePhotoPaths = if (showPhoto) photoPaths else emptyList()
    val isAlbum = effectivePhotoPaths.size > 1

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
            if (isAlbum) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .padding(bottom = 12.dp),
                ) {
                    items(effectivePhotoPaths) { path ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(java.io.File(path))
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(240.dp),
                        )
                    }
                }
            } else if (effectivePhotoPaths.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(effectivePhotoPaths.first()))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(bottom = 12.dp),
                )
            }
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
            val articleBodyText = if (message.mediaType == MediaType.PHOTO && message.text.isBlank() && effectivePhotoPaths.isEmpty()) {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("[Photo]") }
                }
            } else {
                buildAnnotatedString { append(message.text) }
            }
            Text(
                text = articleBodyText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

private fun formatTimestamp(epochSeconds: Long): String {
    val instant = Instant.ofEpochSecond(epochSeconds)
    val now = System.currentTimeMillis()
    return if (now - epochSeconds * 1000 < 86_400_000L) {
        timeFormatter.format(instant)
    } else {
        dateFormatter.format(instant)
    }
}
