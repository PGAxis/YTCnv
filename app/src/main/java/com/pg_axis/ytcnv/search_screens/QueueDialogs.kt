package com.pg_axis.ytcnv.search_screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.dialogs.AddToQueueDialog
import com.pg_axis.ytcnv.dialogs.TitleAuthorDialog

@Composable
fun QueueDialogs(viewModel: QueueViewModel) {
    if (viewModel.showTitleAuthorDialog) {
        TitleAuthorDialog(
            title = viewModel.dialogTitle,
            author = viewModel.dialogAuthor,
            secondsRemaining = viewModel.autoDownloadSecondsRemaining,
            onTitleChange = { viewModel.onDialogTitleChanged(it) },
            onAuthorChange = { viewModel.onDialogAuthorChanged(it) },
            onInteraction = { viewModel.onTitleAuthorDialogInteraction() },
            onConfirm = { viewModel.onTitleAuthorConfirmed() },
            onDismiss = { viewModel.closeQueueDialog() }
        )
    } else if (viewModel.queueItem != null) {
        AddToQueueDialog(viewModel)
    }

    viewModel.playlistPrompt?.let { playlist ->
        AlertDialog(
            onDismissRequest = { viewModel.closePlaylistDialog() },
            title = { Text(stringResource(R.string.download_all)) },
            text = {
                when {
                    viewModel.playlistError != null -> Text(viewModel.playlistError!!)
                    viewModel.playlistEnqueued != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("${viewModel.playlistEnqueued}")
                    }
                    else -> Text(playlist.name)
                }
            },
            confirmButton = {
                if (viewModel.playlistEnqueued == null) {
                    Row {
                        TextButton(onClick = { viewModel.onPlaylistFormatChosen(audio = true) }) { Text(stringResource(R.string.audio)) }
                        TextButton(onClick = { viewModel.onPlaylistFormatChosen(audio = false) }) { Text(stringResource(R.string.video)) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closePlaylistDialog() }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}