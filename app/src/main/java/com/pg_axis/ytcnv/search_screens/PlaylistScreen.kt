package com.pg_axis.ytcnv.search_screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.SearchResultRow
import com.pg_axis.ytcnv.models.Video

@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onEnqueue: (Video) -> Unit,
    onPreviewVideo: (String) -> Unit,
    onChannel: (String) -> Unit,
    viewModel: PlaylistViewModel
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
            IconButton(onClick = onBack, shape = CutCornerShape(0.dp), modifier = Modifier.size(45.dp).padding(horizontal = 5.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Header: Name - Uploader (+ Download all)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        viewModel.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val channelId = viewModel.uploaderChannelId
                    if (viewModel.uploader.isNotEmpty()) {
                        Text(
                            viewModel.uploader,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (channelId != null) Modifier.clickable { onChannel(channelId) } else Modifier
                        )
                    }
                }
                if (!viewModel.headerLoading && viewModel.items.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.onDownloadClicked(viewModel.asResultItem) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(stringResource(R.string.download_all), fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 4.dp, modifier = Modifier.padding(horizontal = 16.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                items(viewModel.items) { item ->
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
                        onPlaylist = { }
                    )
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        when {
                            viewModel.isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            viewModel.error != null -> Text(viewModel.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            !viewModel.endReached -> OutlinedButton(onClick = { viewModel.loadMore() }) {
                                Text(stringResource(R.string.load_more), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        QueueDialogs(viewModel)
    }
}