package com.pg_axis.ytcnv.search_screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.SearchResultRow
import com.pg_axis.ytcnv.models.Video

@Composable
fun ChannelScreen(
    onBack: () -> Unit,
    onEnqueue: (Video) -> Unit,
    onPreviewVideo: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    viewModel: ChannelViewModel = viewModel()
) {
    val context = LocalContext.current

    val text = stringResource(R.string.added_to_queue)
    SideEffect {
        viewModel.onVideoReady = { video ->
            onEnqueue(video)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Top bar
            IconButton(onClick = onBack, shape = CutCornerShape(0.dp), modifier = Modifier.size(45.dp).padding(horizontal = 5.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            val tab = viewModel.tabs[viewModel.selectedTab]

            LazyColumn(Modifier.fillMaxSize()) {
                item { ChannelHeader(viewModel) }

                stickyHeader {
                    PrimaryTabRow(
                        selectedTabIndex = viewModel.selectedTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        ChannelTab.entries.forEach { t ->
                            Tab(
                                selected = viewModel.selectedTab == t,
                                onClick = { viewModel.onTabSelected(t) },
                                text = {
                                    Text(stringResource(when (t) {
                                        ChannelTab.VIDEOS -> R.string.tab_videos
                                        ChannelTab.PLAYLISTS -> R.string.tab_playlists
                                        ChannelTab.SEARCH -> R.string.tab_search
                                    }))
                                }
                            )
                        }
                    }
                }

                if (tab != null) {
                    items(tab.items) { item ->
                        SearchResultRow(
                            item = item,
                            isDownloaded = viewModel.isDownloaded(item.itemId),
                            onDownload = { viewModel.onDownloadClicked(item) },
                            onCopyUrl = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(
                                    "YouTube URL",
                                    item.url
                                )
                                clipboard.setPrimaryClip(clip)
                            },
                            onPreview = { onPreviewVideo(item.itemId) },
                            onChannel = { },
                            onPlaylist = { onPlaylist(item.itemId) }
                        )
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            when {
                                tab.isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                tab.error != null -> Text(tab.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                !tab.endReached && tab.loaded -> OutlinedButton(onClick = { viewModel.loadMore(viewModel.selectedTab) }) {
                                    Text(stringResource(R.string.load_more), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    item { ChannelSearchBar(viewModel) }

                    items(viewModel.searchResults) { item ->
                        SearchResultRow(
                            item = item,
                            isDownloaded = viewModel.isDownloaded(item.itemId),
                            onDownload = { viewModel.onDownloadClicked(item) },
                            onCopyUrl = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(
                                    "YouTube URL",
                                    item.url
                                )
                                clipboard.setPrimaryClip(clip)
                            },
                            onPreview = { onPreviewVideo(item.itemId) },
                            onChannel = { },
                            onPlaylist = { }
                        )
                    }

                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when {
                                viewModel.searchScanning -> {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.channel_search_scanned, viewModel.searchScanned),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                viewModel.searchError != null -> Text(
                                    viewModel.searchError!!,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                viewModel.searchStarted && !viewModel.searchDone -> OutlinedButton(
                                    onClick = { viewModel.scanMore() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.channel_search_more),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp
                                    )
                                }
                                viewModel.searchStarted && viewModel.searchResults.isEmpty() -> Text(
                                    stringResource(R.string.channel_search_none),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        QueueDialogs(viewModel)
    }
}

@Composable
private fun ChannelHeader(vm: ChannelViewModel) {
    Column(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = vm.bannerUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(110.dp).background(MaterialTheme.colorScheme.secondaryContainer)
        )
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = vm.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(y = (-36).dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                Text(vm.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                vm.subscribers?.let {
                    Text(stringResource(R.string.subscribers, formatCount(it)), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChannelSearchBar(vm: ChannelViewModel) {
    val focusManager = LocalFocusManager.current
    val submit = { focusManager.clearFocus(); vm.onChannelSearch() }

    OutlinedTextField(
        value = vm.searchQuery,
        onValueChange = vm::onSearchQueryChanged,
        enabled = !vm.headerLoading,
        singleLine = true,
        placeholder = {
            Text(stringResource(R.string.channel_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }) {
                Icon(
                    painterResource(R.drawable.magglass),
                    "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        shape = RoundedCornerShape(50.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}