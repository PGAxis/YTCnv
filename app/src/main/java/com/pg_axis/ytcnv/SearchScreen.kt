package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults.FocusedBorderThickness
import androidx.compose.material3.OutlinedTextFieldDefaults.UnfocusedBorderThickness
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pg_axis.ytcnv.models.Video
import com.pg_axis.ytcnv.search_screens.QueueDialogs
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onEnqueue: (Video) -> Unit,
    onPreviewVideo: (String) -> Unit,
    onChannel: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var isSearchFocused by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    SideEffect {
        viewModel.onVideoReady = { video ->
            onEnqueue(video)
            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
        }
    }

    val entries = viewModel.panelEntries

    // Animate history panel height
    val historyHeightFraction by animateFloatAsState(
        targetValue = if (isSearchFocused && entries.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "historyHeight"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars)
                .clickable(indication = null, interactionSource = remember {
                    MutableInteractionSource()
                }) { focusManager.clearFocus() }
        ) {
            // -- Header --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
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
                    text = stringResource(R.string.search_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // -- Search bar --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    BasicTextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.onQueryChanged(it) },
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            viewModel.onSearch()
                        }),
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                isSearchFocused = it.isFocused
                                if (it.isFocused) filterMenuExpanded = false
                            },
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = viewModel.searchQuery.text,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = interactionSource,
                                placeholder = {
                                    Text(stringResource(R.string.search_prompt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                trailingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable {
                                                focusManager.clearFocus()
                                                viewModel.onSearch()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.magglass),
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.background
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                container = {
                                    OutlinedTextFieldDefaults.Container(
                                        enabled = true,
                                        isError = false,
                                        interactionSource = interactionSource,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(50.dp),
                                        focusedBorderThickness = FocusedBorderThickness,
                                        unfocusedBorderThickness = UnfocusedBorderThickness,
                                    )
                                }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    var filterMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                filterMenuExpanded = true
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.filter),
                                contentDescription = "Filters",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            @Composable
                            fun FilterGroup(titleRes: Int, filters: List<SearchFilter>) {
                                Text(
                                    text = stringResource(titleRes),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )

                                filters.forEach { filter ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(filter.labelRes),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = filter == viewModel.selectedFilter,
                                                onClick = {
                                                    viewModel.onFilterToggled(filter)
                                                    filterMenuExpanded = false
                                                }
                                            )
                                        },
                                        onClick = {
                                            viewModel.onFilterToggled(filter)
                                            filterMenuExpanded = false
                                        }
                                    )
                                }
                            }

                            FilterGroup(
                                R.string.filter_youtube,
                                SearchFilter.entries.filter { !it.isMusic }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline
                            )

                            FilterGroup(
                                R.string.filter_youtube_music,
                                SearchFilter.entries.filter { it.isMusic }
                            )
                        }
                    }
                }

                // -- Search history panel --
                if (historyHeightFraction > 0f) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 52.dp)
                            .heightIn(max = (300 * historyHeightFraction).dp)
                            .zIndex(5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        LazyColumn(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                            items(entries, key = { it.text }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (entry.isHistory) viewModel.onHistoryItemTapped(entry.text)
                                            else {
                                                focusManager.clearFocus()
                                                viewModel.onSuggestionTapped(entry.text)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.text,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (entry.isHistory) {
                                        IconButton(
                                            onClick = { viewModel.onRemoveHistoryItem(entry.text) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.cross),
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // -- Results / states --
                Box(modifier = Modifier.fillMaxSize().padding(top = 50.dp)) {
                    when {
                        viewModel.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        viewModel.errorMessage != null -> {
                            Text(
                                text = viewModel.errorMessage!!,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        viewModel.results.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.search_big_prompt),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn {
                                    items(viewModel.results) { item ->
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
                                            onChannel = { (item.uploaderId ?: item.itemId).let(onChannel) },
                                            onPlaylist = { onPlaylist(item.itemId) }
                                        )
                                    }

                                    item {
                                        if (!viewModel.endReached) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                if (viewModel.isLoadingMore) {
                                                    CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                else {
                                                    OutlinedButton(
                                                        onClick = { viewModel.onLoadMore() },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.load_more),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontSize = 12.sp
                                                        )
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
        }

        QueueDialogs(viewModel)
    }
}

@Composable
fun SearchResultRow(
    item: SearchResultItem,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onCopyUrl: () -> Unit,
    onPreview: () -> Unit,
    onChannel: () -> Unit,
    onPlaylist: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = if (item is ChannelResultItem) Alignment.CenterHorizontally else Alignment.Start
    ) {
        // Thumbnail with duration overlay and buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (item.squareArt) Modifier.height(150.dp)
                        else Modifier.aspectRatio(16f / 9f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val shape = if (item is ChannelResultItem) CircleShape else RoundedCornerShape(8.dp)

                Box(
                    modifier = Modifier
                        .then(
                            if (item.squareArt) Modifier.fillMaxHeight().aspectRatio(1f)
                            else Modifier.fillMaxSize()
                        )
                        .clickable {
                            when(item) {
                                is VideoResultItem, is MusicResultItem -> onPreview()
                                is ChannelResultItem -> onChannel()
                                is PlaylistResultItem -> onPlaylist()
                            }
                        }
                ) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                    )
                    if (item.duration.isNotEmpty()) {
                        Text(
                            text = item.duration,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (item is PlaylistResultItem) {
                        Icon(
                            painter = painterResource(R.drawable.queue),
                            contentDescription = "Playlist",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(40.dp)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (item !is ChannelResultItem) {
                Spacer(modifier = Modifier.width(8.dp))
                // Buttons
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isDownloaded) {
                        OutlinedButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(stringResource(if (item !is PlaylistResultItem) R.string.download else R.string.download_all), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(stringResource(if (item !is PlaylistResultItem) R.string.download else R.string.download_all), fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onCopyUrl,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(stringResource(R.string.copy_url), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Title
        Text(
            text = item.name,
            color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        // Uploader
        if (!item.uploader.isNullOrEmpty()) {
            Text(
                text = item.uploader.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (item.uploaderId != null) Modifier.clickable { onChannel() } else Modifier
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 4.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

enum class SearchFilter(
    val key: String,
    val labelRes: Int,
    val isMusic: Boolean = false
) {
    VIDEOS(
        YoutubeSearchQueryHandlerFactory.VIDEOS,
        R.string.filter_videos
    ),
    CHANNELS(
        YoutubeSearchQueryHandlerFactory.CHANNELS,
        R.string.filter_channels
    ),
    PLAYLISTS(
        YoutubeSearchQueryHandlerFactory.PLAYLISTS,
        R.string.filter_playlists
    ),

    MUSIC_SONGS(
        YoutubeSearchQueryHandlerFactory.MUSIC_SONGS,
        R.string.filter_songs,
        true
    )
}

sealed interface SearchResultItem {
    val name: String
    val itemId: String
    val uploader: String?
    val duration: String
    val imageUrl: String
    val url: String
    val squareArt: Boolean
    val uploaderId: String?
}

data class VideoResultItem(
    override val name: String,
    override val itemId: String,
    override val uploader: String,
    override val duration: String,
    override val imageUrl: String,
    override val url: String,
    override val uploaderId: String?,
) : SearchResultItem {
    override val squareArt: Boolean = false
}

data class ChannelResultItem(
    override val name: String,
    override val itemId: String,
    override val imageUrl: String,
    override val url: String,
) : SearchResultItem {
    override val squareArt: Boolean = true
    override val uploader: String? = null
    override val duration: String = ""
    override val uploaderId: String? = null
}

data class PlaylistResultItem(
    override val name: String,
    override val itemId: String,
    override val uploader: String,
    override val imageUrl: String,
    override val url: String,
    override val uploaderId: String?,
) : SearchResultItem {
    override val squareArt: Boolean = false
    override val duration: String = ""
}

data class MusicResultItem(
    override val name: String,
    override val itemId: String,
    override val uploader: String,
    override val duration: String,
    override val imageUrl: String,
    override val url: String,
    override val uploaderId: String?,
) : SearchResultItem {
    override val squareArt: Boolean = true
}