package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.dialogs.TitleAuthorDialog
import com.pg_axis.ytcnv.dialogs.UpdateDialog
import com.pg_axis.ytcnv.models.AudioTrackOption
import com.pg_axis.ytcnv.models.TrackType
import com.pg_axis.ytcnv.side_pages.PlaylistPickerSheet
import com.pg_axis.ytcnv.ui.theme.PopupError
import java.util.Locale

@SuppressLint("SourceLockedOrientationActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val settings = viewModel.settings
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        if (viewModel.showTitleAuthorDialog) {
            TitleAuthorDialog(
                initialTitle = viewModel.dialogTitle,
                initialAuthor = viewModel.dialogAuthor,
                onConfirm = { title, author -> viewModel.onTitleAuthorConfirmed(title, author) },
                onDismiss = { viewModel.onTitleAuthorDismissed() }
            )
        }

        viewModel.updateInfo?.let { info ->
            UpdateDialog(
                updateInfo = info,
                onDismiss = { dontShowAgain -> viewModel.onUpdateDialogDismissed(dontShowAgain) }
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.dp)
        ) {
            // --- Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSearch, shape = CutCornerShape(0.dp)) {
                    Icon(painter = painterResource(id = R.drawable.magglass), contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onOpenSettings, shape = CutCornerShape(0.dp)) {
                    Icon(painter = painterResource(id = R.drawable.settings), contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // --- URL Input ---
            Column {
                Text(text = stringResource(R.string.URLIDPrompt), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = viewModel.urlEntryText,
                    onValueChange = { viewModel.onUrlChanged(it) },
                    placeholder = { Text(text = "youtube.com/watch?v=...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(25.dp))

            // --- Format/Quality pickers ---
            if (viewModel.downloadOptionsIsVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (settings.quickDwnld) {
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
                                    value = formats.getOrElse(viewModel.selectedFormatIndex) { stringResource(R.string.choose_format) },
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
                                                viewModel.onFormatChanged(index)
                                                formatExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onOpenInfo() }, shape = CutCornerShape(0.dp), modifier = Modifier.size(35.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        val formatOptions = viewModel.video?.formatOptions ?: emptyList()
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
                                    value = formatOptions.getOrNull(viewModel.selectedDetailedFormatIndex)?.displayName
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
                                                viewModel.onDetailedFormatChanged(index)
                                                formatExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onOpenInfo() }, shape = CutCornerShape(0.dp), modifier = Modifier.size(35.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (viewModel.qualityPickerIsVisible && viewModel.qualityPickerItemsSource.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var qualityExpanded by remember { mutableStateOf(false) }
                            val selectedQuality = viewModel.qualityPickerItemsSource.getOrNull(viewModel.selectedQualityIndex)
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
                                    viewModel.qualityPickerItemsSource.forEachIndexed { index, option ->
                                        DropdownMenuItem(
                                            text = { Text(qualityLabel(option)) },
                                            onClick = {
                                                viewModel.onQualityChanged(index)
                                                qualityExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            if (viewModel.video?.isFetchingQualitySizes == true) {
                                Text(
                                    text = stringResource(R.string.fetching_sizes),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // --- Audio quality toggle (video downloads only) ---
                            val selectedFormat = viewModel.selectedFormatOption?.format
                            if (selectedFormat?.trackType == TrackType.VIDEO) {

                                val audioOptions = viewModel.video?.audioTrackOptions ?: emptyList()
                                if (audioOptions.isNotEmpty()) {
                                    var audioExpanded by remember { mutableStateOf(false) }
                                    val videoSizeBytes = selectedQuality?.sizeBytes
                                    val selectedAudio = audioOptions.getOrNull(viewModel.selectedAudioTrackIndex)
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
                                                        viewModel.onAudioTrackChanged(index)
                                                        audioExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (viewModel.video?.isFetchingAudioTrackSizes == true) {
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

                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Action button ---
            Row(modifier = Modifier.fillMaxWidth()) {
                /*if (viewModel.loadButtonIsVisible) {
                    Button(
                        onClick = { viewModel.onLoadClicked() },
                        enabled = viewModel.loadButtonIsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text(text = stringResource(R.string.load)) }
                }*/
                if (viewModel.downloadButtonIsVisible) {
                    Button(
                        onClick = { viewModel.onDownloadClicked() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(text = stringResource(R.string.download)) }
                }
                if (viewModel.cancelButtonIsVisible) {
                    Button(
                        onClick = { viewModel.onCancelClicked() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text(text = stringResource(R.string.cancel)) }
                }
            }

            Spacer(modifier = Modifier.height(25.dp))

            // --- Progress / Status ---
            if (viewModel.dwnldProgressIsVisible || viewModel.downloadIndicatorIsVisible || viewModel.statusLabelIsVisible) {
                Column(modifier = Modifier.padding(bottom = 25.dp)) {
                    if (viewModel.dwnldProgressIsVisible) {
                        LinearProgressIndicator(
                            progress = { viewModel.video?.downloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )

                        val v = viewModel.video
                        if (v != null && v.isMuxing) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val sizeLabel = if (v.muxedBytesTotalEstimate > 0) {
                                    " · ~" + stringResource(
                                        R.string.muxing_progress,
                                        formatBytes(v.muxedBytesEstimate),
                                        formatBytes(v.muxedBytesTotalEstimate)
                                    )
                                } else ""
                                Text(
                                    text = "${(v.downloadProgress * 100).toInt()}%$sizeLabel",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDuration(v.elapsedSeconds),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            v.muxEtaSeconds?.let {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.eta_format, formatDuration(it)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        } else if (v != null && v.bytesTotal > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = "${(v.downloadProgress * 100).toInt()}% · ${formatBytes(v.bytesDownloaded)} / ${formatBytes(v.bytesTotal)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDuration(v.elapsedSeconds),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (v.downloadSpeedBytesPerSec > 0 || v.etaSeconds != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = if (v.downloadSpeedBytesPerSec > 0) "${formatBytes(v.downloadSpeedBytesPerSec)}/s" else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    v.etaSeconds?.let {
                                        Text(
                                            text = stringResource(R.string.eta_format, formatDuration(it)),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    if (viewModel.downloadIndicatorIsVisible) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    if (viewModel.statusLabelIsVisible) {
                        Text(
                            text = viewModel.video?.statusLabelText ?: AnnotatedString(""),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // --- Divider ---
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            // --- Download History ---
            Card(
                modifier = Modifier.padding(vertical = 25.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(15.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.download_history),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 30.dp)
                                .align(Alignment.Center),
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onOpenHistory, modifier = Modifier.size(30.dp).align(Alignment.CenterEnd), shape = RoundedCornerShape(0.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_right),
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (settings.downloadHistory.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.empty_history),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn {
                            items(settings.downloadHistory.take(3)) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.onHistoryItemTapped(item.urlOrId) },
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = {
                                        val updated = viewModel.settings.downloadHistory.toMutableList()
                                        updated.removeAll { it.urlOrId == item.urlOrId }
                                        viewModel.settings.downloadHistory = updated
                                    }) {
                                        Icon(painter = painterResource(id = R.drawable.cross), contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }
        }

        // --- Popup overlay ---
        if (viewModel.popupIsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80212121))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = viewModel.popupBackground),
                    modifier = Modifier.widthIn(min = 200.dp).padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = viewModel.popupTitle,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = viewModel.popupMessage,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                        TextButton(
                            onClick = { viewModel.onClosePopupClicked() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(viewModel.popupButtonText, color = Color(0xFFD0D0D0))
                        }
                    }
                }
            }
        }

        // --- Keep/discard partial download dialog ---
        if (viewModel.showKeepPartialDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80212121))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.widthIn(min = 200.dp).padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.keep_partial_title),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(
                                R.string.keep_partial_message,
                                viewModel.keepPartialStreamLabel,
                                formatBytes(viewModel.keepPartialSizeBytes)
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.onKeepPartialChosen(false) }) {
                                Text(stringResource(R.string.delete), color = Color(0xFFD0D0D0))
                            }
                            TextButton(onClick = { viewModel.onKeepPartialChosen(true) }) {
                                Text(stringResource(R.string.keep), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.showCancelConfirmDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80212121))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.widthIn(min = 200.dp).padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.cancel_confirm_title),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.cancel_confirm_message),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.onCancelDismissed() }) {
                                Text(stringResource(R.string.cancel_confirm_no), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            TextButton(onClick = { viewModel.onCancelConfirmed() }) {
                                Text(stringResource(R.string.cancel_confirm_yes), color = PopupError)
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.showMarginOverrideDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80212121))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.widthIn(min = 200.dp).padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.margin_override_title),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(
                                R.string.margin_override_message,
                                formatBytes(viewModel.marginOverrideAvailableBytes),
                                formatBytes(viewModel.marginOverrideRequiredBytes)
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.onMarginOverrideChosen(false) }) {
                                Text(stringResource(R.string.margin_override_cancel), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            TextButton(onClick = { viewModel.onMarginOverrideChosen(true) }) {
                                Text(stringResource(R.string.margin_override_proceed), color = PopupError)
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.showPlaylistPicker) {
            viewModel.lastDownloadedSongUri?.let { uri ->
                PlaylistPickerSheet(
                    songUri = uri,
                    onDismiss = { viewModel.showPlaylistPicker = false }
                )
            }
        }
    }
}

private fun qualityLabel(option: com.pg_axis.ytcnv.models.QualityOption): String {
    val nativePart = if (!option.isNative) " · converted" else ""
    val sizePart = option.sizeBytes?.let { " (${formatBytes(it)})" } ?: ""
    return "${option.displayName}$nativePart$sizePart"
}

private fun audioTrackLabel(option: AudioTrackOption, videoSizeBytes: Long?): String {
    val base = "${option.codecName}: ${option.bitrate} kbps"
    val audioSize = option.sizeBytes ?: return base
    return if (videoSizeBytes != null) {
        val total = formatBytes(videoSizeBytes + audioSize)
        "$base | $total (${formatBytes(videoSizeBytes)} + ${formatBytes(audioSize)})"
    } else {
        "$base | ${formatBytes(audioSize)}"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.getDefault(), "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(Locale.getDefault(), "%.0f KB", kb)
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 0) return "--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> String.format(Locale.getDefault(), "%dh %02dm", h, m)
        m > 0 -> String.format(Locale.getDefault(), "%dm %02ds", m, s)
        else -> "${s}s"
    }
}