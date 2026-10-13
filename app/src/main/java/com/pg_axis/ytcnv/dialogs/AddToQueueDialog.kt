package com.pg_axis.ytcnv.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.SearchViewModel
import com.pg_axis.ytcnv.side_pages.DownloadOptionsPicker

@Composable
fun AddToQueueDialog(viewModel: SearchViewModel) {
    val item = viewModel.queueItem ?: return
    val video = viewModel.queueVideo
    val settings = viewModel.settings

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80212121))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center
                )

                when {
                    viewModel.queueError != null -> {
                        Text(
                            text = viewModel.queueError ?: "",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    viewModel.queueIsLoading || video == null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = video?.statusLabelText?.text ?: "",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    else -> {
                        DownloadOptionsPicker(
                            quickMode = settings.quickDwnld,
                            video = video,
                            selectedQuickFormatIndex = viewModel.queueQuickFormatIndex,
                            selectedDetailedFormatIndex = viewModel.queueDetailedFormatIndex,
                            selectedQualityIndex = viewModel.queueQualityIndex,
                            selectedAudioTrackIndex = viewModel.queueAudioTrackIndex,
                            qualityPickerIsVisible = !settings.quickDwnld,
                            onQuickFormatChanged = { viewModel.onQueueQuickFormatChanged(it) },
                            onDetailedFormatChanged = { viewModel.onQueueDetailedFormatChanged(it) },
                            onQualityChanged = { viewModel.onQueueQualityChanged(it) },
                            onAudioTrackChanged = { viewModel.onQueueAudioTrackChanged(it) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.closeQueueDialog() }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Button(
                        onClick = { viewModel.onQueueOptionsConfirmed() },
                        enabled = video != null &&
                                !viewModel.queueIsLoading &&
                                !viewModel.queueIsPreparing &&
                                viewModel.queueError == null
                    ) {
                        Text(stringResource(R.string.download))
                    }
                }
            }
        }
    }
}