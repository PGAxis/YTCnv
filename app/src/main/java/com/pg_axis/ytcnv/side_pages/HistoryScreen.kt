package com.pg_axis.ytcnv.side_pages

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.models.Video
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onResultSelected: (url: String) -> Unit,
    vm: HistoryViewModel = viewModel()
) {
    var pickerUri: String? by remember { mutableStateOf(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // -- Header --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, shape = CutCornerShape(0.dp), modifier = Modifier.size(45.dp).padding(horizontal = 5.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.download_history),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val lazyListState = rememberLazyListState()
            // Keys of queue rows are Video.uid (Long); everything else in the list uses String keys,
            // so only queue rows can ever be reordered. The downloading row is disabled below.
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val fromUid = from.key as? Long
                val toUid = to.key as? Long
                if (fromUid != null && toUid != null) vm.onMoveQueueItem(fromUid, toUid)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                state = lazyListState
            ) {
                val queue = vm.queue
                if (queue.isNotEmpty()) {
                    item(key = "queue_header") {
                        SectionHeader(stringResource(R.string.queue_section))
                    }
                    items(queue, key = { it.uid }) { video ->
                        val isActive = video === vm.activeVideo
                        ReorderableItem(reorderableState, key = video.uid, enabled = !isActive) { isDragging ->
                            QueueItemRow(
                                video = video,
                                isActive = isActive,
                                isDragging = isDragging,
                                onRemove = { vm.onRemoveQueueItem(video) }
                            )
                        }
                    }
                    item(key = "history_header") {
                        SectionHeader(stringResource(R.string.history_section))
                    }
                }

                itemsIndexed(
                    items = vm.settings.downloadHistory,
                    key = { i, item -> "${item.urlOrId}_${i}" }
                ) { _, historyItem ->
                    LaunchedEffect(historyItem.uri, historyItem.downloaded) {
                        if (historyItem.downloaded && historyItem.uri.isNotEmpty()) {
                            val isValid = withContext(Dispatchers.IO) {
                                try {
                                    context.contentResolver
                                        .openFileDescriptor(historyItem.uri.toUri(), "r")
                                        ?.use { true } ?: false
                                } catch (_: Exception) {
                                    false
                                }
                            }
                            if (!isValid) {
                                vm.markUndownloaded(historyItem.urlOrId)
                            }
                        }
                    }

                    HistoryItemRow(
                        titleOrg = historyItem.title,
                        title = historyItem.metadataTitle,
                        author = historyItem.metadataAuthor,
                        isMp3 = historyItem.isMp3,
                        downloaded = historyItem.downloaded,
                        onRemove = { vm.onRemove(historyItem.urlOrId) },
                        onRedownload = { onResultSelected(historyItem.urlOrId) },
                        onShowPicker = {
                            val uriString = historyItem.uri
                            if (uriString == "") {
                                vm.markUndownloaded(historyItem.urlOrId)
                                return@HistoryItemRow
                            }
                            scope.launch {
                                val isValid = withContext(Dispatchers.IO) {
                                    try {
                                        context.contentResolver
                                            .openFileDescriptor(uriString.toUri(), "r")
                                            ?.use { true } ?: false
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                                if (isValid) {
                                    pickerUri = uriString
                                } else {
                                    vm.markUndownloaded(historyItem.urlOrId)
                                }
                            }
                        }
                    )
                }
            }
        }

        pickerUri?.let { uri ->
            PlaylistPickerSheet(
                songUri = uri.toUri(),
                onDismiss = { pickerUri = null }
            )
        }
    }
}

@Composable
fun HistoryItemRow(
    titleOrg: String,
    title: String,
    author: String,
    isMp3: Boolean?,
    downloaded: Boolean,
    onRemove: () -> Unit,
    onRedownload: () -> Unit,
    onShowPicker: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleOrg,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Left,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, shape = RoundedCornerShape(0.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.settings),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rm_from_history), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hist_scr_redownload), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                            onClick = {
                                menuExpanded = false
                                onRedownload()
                            }
                        )
                        if (downloaded) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pp_add_to), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                                onClick = {
                                    menuExpanded = false
                                    onShowPicker()
                                }
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.metadata),
                textAlign = TextAlign.Left,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = title,
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = author,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = when(isMp3) {
                            true -> "MP3"
                            false -> "MP4"
                            else -> "N/A"
                        },
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun ReorderableCollectionItemScope.QueueItemRow(
    video: Video,
    isActive: Boolean,
    isDragging: Boolean,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "queueRowElevation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = video.displayTitle,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (isActive) 12.dp else 0.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                if (!isActive) {
                    Icon(
                        painter = painterResource(R.drawable.drag_handle),
                        contentDescription = "Reorder",
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp)
                            .draggableHandle(),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }, shape = RoundedCornerShape(0.dp), modifier = Modifier.size(24.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.settings),
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.queue_remove), color = MaterialTheme.colorScheme.onSecondaryContainer) },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isActive) stringResource(R.string.queue_downloading) else video.displayAuthor,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = when (video.isAudioTarget) {
                        true -> "MP3"
                        false -> "MP4"
                        null -> "N/A"
                    },
                    textAlign = TextAlign.End,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
        }
    }
}