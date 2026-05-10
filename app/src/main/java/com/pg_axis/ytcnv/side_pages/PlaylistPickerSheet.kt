package com.pg_axis.ytcnv.side_pages

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.services.MusicAxsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    songUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playlistList by remember { mutableStateOf<List<MusicAxsClient.PlaylistInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        playlistList = withContext(Dispatchers.IO) { MusicAxsClient.getPlaylists(context) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add to Music.axs playlist",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()

            if (playlistList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                playlistList.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.songCount} songs") },
                        modifier = Modifier.clickable {
                            CoroutineScope(Dispatchers.IO).launch {
                                val success = MusicAxsClient.addSongToPlaylist(context, songUri, playlist.id)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (success) "Added to ${playlist.name}" else "Failed to add song",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}