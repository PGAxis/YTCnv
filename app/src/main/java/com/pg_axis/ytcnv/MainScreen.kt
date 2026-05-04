package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.dialogs.TermsDialog
import com.pg_axis.ytcnv.dialogs.TitleAuthorDialog
import com.pg_axis.ytcnv.dialogs.UpdateDialog
import com.pg_axis.ytcnv.settings.*
import com.pg_axis.ytcnv.side_pages.PlaylistPickerSheet
import com.pg_axis.ytcnv.ui.theme.BackgroundDark
import com.pg_axis.ytcnv.ui.theme.BorderColor
import com.pg_axis.ytcnv.ui.theme.CardDark
import com.pg_axis.ytcnv.ui.theme.CyanLight
import com.pg_axis.ytcnv.ui.theme.DividerColor
import com.pg_axis.ytcnv.ui.theme.SurfaceVariantDark
import com.pg_axis.ytcnv.ui.theme.TextPrimary
import com.pg_axis.ytcnv.ui.theme.TextSecondary
import com.pg_axis.ytcnv.ui.theme.YTCnvTheme

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    YTCnvTheme {
        MainScreen(viewModel = MainViewModel(Application()), {}, {}, {})
    }
}

@SuppressLint("SourceLockedOrientationActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onOpenSearch: () -> Unit, onOpenSettings: () -> Unit, onTermsDeclined: () -> Unit) {
    val settings = viewModel.settings
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(BackgroundDark)
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

        if (viewModel.showTermsDialog) {
            TermsDialog(
                onAccept = { viewModel.onTermsAccepted() },
                onDecline = {
                    viewModel.onTermsDeclined()
                    onTermsDeclined()
                }
            )
        } else {
            viewModel.updateInfo?.let { info ->
                UpdateDialog(
                    updateInfo = info,
                    onDismiss = { dontShowAgain -> viewModel.onUpdateDialogDismissed(dontShowAgain) }
                )
            }
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.dp)
        ) {
            // ─── Header ───
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
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSearch, shape = CutCornerShape(0.dp)) {
                    Icon(painter = painterResource(id = R.drawable.magglass), contentDescription = "Search", tint = CyanLight)
                }
                IconButton(onClick = onOpenSettings, shape = CutCornerShape(0.dp)) {
                    Icon(painter = painterResource(id = R.drawable.settings), contentDescription = "Settings", tint = CyanLight)
                }
            }

            // ─── URL Input ───
            Column {
                Text(text = stringResource(R.string.URLIDPrompt), fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = viewModel.urlEntryText,
                    onValueChange = { viewModel.onUrlChanged(it) },
                    placeholder = { Text(text = "https://www.youtube.com/watch?v=...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(25.dp))

            // ─── Format/Quality pickers ───
            if (viewModel.downloadOptionsIsVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Format picker
                    var formatExpanded by remember { mutableStateOf(false) }
                    val formats = listOf("MP3", "MP4")
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = formats.getOrElse(viewModel.formatPickerSelectedIndex) { "Choose format" },
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

                    if (viewModel.qualityPickerIsVisible) {
                        Spacer(modifier = Modifier.width(8.dp))
                        var qualityExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = qualityExpanded,
                            onExpandedChange = { qualityExpanded = it },
                            modifier = Modifier.weight(2f)
                        ) {
                            OutlinedTextField(
                                value = viewModel.qualityPickerItemsSource.getOrNull(viewModel.qualityPickerSelectedIndex)?.displayName ?: stringResource(R.string.choose_quality),
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
                                        text = { Text(option.displayName) },
                                        onClick = {
                                            viewModel.onQualityChanged(index)
                                            qualityExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ─── Action button ───
            Row(modifier = Modifier.fillMaxWidth()) {
                if (viewModel.loadButtonIsVisible) {
                    Button(
                        onClick = { viewModel.onLoadClicked() },
                        enabled = viewModel.loadButtonIsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceVariantDark,
                            contentColor = CyanLight
                        )
                    ) { Text(text = stringResource(R.string.load)) }
                }
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
                            containerColor = SurfaceVariantDark,
                            contentColor = CyanLight
                        )
                    ) { Text(text = stringResource(R.string.cancel)) }
                }
            }

            Spacer(modifier = Modifier.height(25.dp))

            // ─── Progress / Status ───
            Box(modifier = Modifier.height(80.dp)) {
                Column {
                    if (viewModel.dwnldProgressIsVisible) {
                        LinearProgressIndicator(
                            progress = { viewModel.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (viewModel.downloadIndicatorIsVisible) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    if (viewModel.statusLabelIsVisible) {
                        Text(
                            text = viewModel.statusLabelText,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = TextPrimary
                        )
                    }
                }
            }

            // ─── Divider ───
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 10.dp),
                color = DividerColor
            )

            // ─── Download History ───
            Card(
                modifier = Modifier.fillMaxSize().padding(vertical = 25.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.fillMaxHeight().padding(15.dp)) {
                    Text(
                        text = stringResource(R.string.download_history),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        color = CyanLight
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (settings.downloadHistory.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.empty_history),
                                color = TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn {
                            items(settings.downloadHistory) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        color = TextSecondary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.onHistoryItemTapped(item.urlOrId) },
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = {
                                        val updated = viewModel.settings.downloadHistory.toMutableList()
                                        updated.removeAll { it.urlOrId == item.urlOrId }
                                        viewModel.settings.downloadHistory = updated
                                        (viewModel.settings as? SettingsSave)?.saveExtraData()
                                    }) {
                                        Icon(painter = painterResource(id = R.drawable.cross), contentDescription = "Remove", tint = TextSecondary)
                                    }
                                }
                                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }
        }

        // ─── Popup overlay ───
        if (viewModel.popupIsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80212121))
                    .clickable(enabled = false) {}, // blocks touches
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
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = viewModel.popupMessage,
                            color = TextPrimary,
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