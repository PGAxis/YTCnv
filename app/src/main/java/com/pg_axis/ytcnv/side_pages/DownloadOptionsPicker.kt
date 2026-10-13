package com.pg_axis.ytcnv.side_pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.formatBytes
import com.pg_axis.ytcnv.models.AudioTrackOption
import com.pg_axis.ytcnv.models.QualityOption
import com.pg_axis.ytcnv.models.TrackType
import com.pg_axis.ytcnv.models.Video
import com.pg_axis.ytcnv.qualityLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsPicker(
    quickMode: Boolean,
    video: Video?,
    selectedQuickFormatIndex: Int,
    selectedDetailedFormatIndex: Int,
    selectedQualityIndex: Int,
    selectedAudioTrackIndex: Int,
    qualityPickerIsVisible: Boolean,
    onQuickFormatChanged: (Int) -> Unit,
    onDetailedFormatChanged: (Int) -> Unit,
    onQualityChanged: (Int) -> Unit,
    onAudioTrackChanged: (Int) -> Unit,
    onOpenInfo: (() -> Unit)? = null
) {
    val qualityItems: List<QualityOption> = video?.qualityOptionsWithSizes ?: emptyList()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (quickMode) {
            var formatExpanded by remember { mutableStateOf(false) }
            val formats = listOf("MP3", "MP4")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                ExposedDropdownMenuBox(
                    expanded = formatExpanded,
                    onExpandedChange = { formatExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = formats.getOrElse(selectedQuickFormatIndex) { stringResource(R.string.choose_format) },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(R.string.format)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false }
                    ) {
                        formats.forEachIndexed { index, format ->
                            DropdownMenuItem(
                                text = { Text(format) },
                                onClick = {
                                    onQuickFormatChanged(index)
                                    formatExpanded = false
                                }
                            )
                        }
                    }
                }
                if (onOpenInfo != null) IconButton(onClick = onOpenInfo, shape = CutCornerShape(0.dp), modifier = Modifier.size(35.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            val formatOptions = video?.formatOptions ?: emptyList()
            val hasOptions = formatOptions.isNotEmpty()
            var formatExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                ExposedDropdownMenuBox(
                    expanded = formatExpanded,
                    onExpandedChange = { formatExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = formatOptions.getOrNull(selectedDetailedFormatIndex)?.displayName
                            ?: stringResource(R.string.choose_format),
                        onValueChange = {},
                        readOnly = true,
                        enabled = hasOptions,
                        label = { Text(text = stringResource(R.string.format)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = hasOptions)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false }
                    ) {
                        formatOptions.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    onDetailedFormatChanged(index)
                                    formatExpanded = false
                                }
                            )
                        }
                    }
                }
                if (onOpenInfo != null) IconButton(onClick = onOpenInfo, shape = CutCornerShape(0.dp), modifier = Modifier.size(35.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (qualityPickerIsVisible && qualityItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                var qualityExpanded by remember { mutableStateOf(false) }
                val selectedQuality = qualityItems.getOrNull(selectedQualityIndex)
                ExposedDropdownMenuBox(
                    expanded = qualityExpanded,
                    onExpandedChange = { qualityExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedQuality?.let { qualityLabel(it) } ?: stringResource(R.string.choose_quality),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(R.string.quality)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = qualityExpanded,
                        onDismissRequest = { qualityExpanded = false }
                    ) {
                        qualityItems.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(qualityLabel(option)) },
                                onClick = {
                                    onQualityChanged(index)
                                    qualityExpanded = false
                                }
                            )
                        }
                    }
                }
                if (video?.isFetchingQualitySizes == true) {
                    Text(
                        text = stringResource(R.string.fetching_sizes),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // --- Audio quality toggle (video downloads only) ---
                val selectedFormat = video?.formatOptions?.getOrNull(selectedDetailedFormatIndex)?.format
                if (selectedFormat?.trackType == TrackType.VIDEO) {

                    val audioOptions = video.audioTrackOptions
                    if (audioOptions.isNotEmpty()) {
                        var audioExpanded by remember { mutableStateOf(false) }
                        val videoSizeBytes = selectedQuality?.sizeBytes
                        val selectedAudio = audioOptions.getOrNull(selectedAudioTrackIndex)
                        ExposedDropdownMenuBox(
                            expanded = audioExpanded,
                            onExpandedChange = { audioExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedAudio?.let {
                                    audioTrackLabel(
                                        it,
                                        videoSizeBytes
                                    )
                                }
                                    ?: stringResource(R.string.choose_quality),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(text = stringResource(R.string.audio)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = audioExpanded
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor(
                                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = audioExpanded,
                                onDismissRequest = { audioExpanded = false }
                            ) {
                                audioOptions.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                audioTrackLabel(
                                                    option,
                                                    videoSizeBytes
                                                )
                                            )
                                        },
                                        onClick = {
                                            onAudioTrackChanged(index)
                                            audioExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        if (video.isFetchingAudioTrackSizes) {
                            Text(
                                text = stringResource(R.string.fetching_sizes),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun audioTrackLabel(option: AudioTrackOption, videoSizeBytes: Long?): String {
    val base = "${option.codecName}: ${option.bitrate} kbps"
    val audioSize = option.sizeBytes ?: return base
    return if (videoSizeBytes != null) {
        val total = formatBytes(videoSizeBytes + audioSize)
        "$base | $total (${formatBytes(videoSizeBytes)} + ${formatBytes(audioSize)})"
    } else {
        "$base | ${formatBytes(audioSize)}"
    }
}