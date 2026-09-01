package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.models.TargetFormat
import com.pg_axis.ytcnv.models.TrackType
import com.pg_axis.ytcnv.services.MusicAxsClient
import com.pg_axis.ytcnv.services.Theme
import com.pg_axis.ytcnv.settings.SettingsSave
import com.pg_axis.ytcnv.side_pages.SideScreenHeader
import java.io.File
import java.time.LocalDateTime

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }

    var logsOpened by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initPaths()
    }

    // SAF folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.onFolderPicked(uri.toString())
            }
        }
    }

    val folderVidPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.onVidFolderPicked(uri.toString())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        SideScreenHeader(
            onBack = onBack
        ) {
            Spacer(Modifier.width(4.dp))

            Text(
                text = stringResource(R.string.settings_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // -- Folder picker --
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.download_dest))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.audio),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )

                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    folderPickerLauncher.launch(intent)
                                },
                                shape = CutCornerShape(5.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.audio),
                                    contentDescription = "Audio file picker",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 15.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Text(
                                text = viewModel.mainFolder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                text = viewModel.finalFolder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.video),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )

                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    folderVidPickerLauncher.launch(intent)
                                },
                                shape = CutCornerShape(5.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.video),
                                    contentDescription = "Video file picker",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 15.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            Text(
                                text = viewModel.mainVidFolder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                text = viewModel.finalVidFolder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // -- Download Settings Group --
            SettingsGroup(title = stringResource(R.string.d_settings), initiallyExpanded = false) {
                // -- Toggle: 4K --
                SettingsToggleRow(
                    title = stringResource(R.string.up_to_4k),
                    description = stringResource(R.string.up_to_4k_desc),
                    checked = viewModel.settings.use4K
                ) { viewModel.onUse4KChanged(it) }

                // -- Toggle: Quick download --
                SettingsToggleRow(
                    title = stringResource(R.string.quick_download),
                    description = stringResource(R.string.quick_download_desc),
                    checked = viewModel.settings.quickDwnld
                ) { viewModel.onQuickDwnldChanged(it) }

                SettingsToggleRow(
                    title = stringResource(R.string.muxed_fallback),
                    description = stringResource(R.string.muxed_fallback_desc),
                    checked = viewModel.settings.muxedFallback
                ) { viewModel.onMuxedChanged(it) }

                SettingsNumberInputRow(
                    title = stringResource(R.string.storage_margin),
                    description = stringResource(R.string.storage_margin_desc),
                    warning = if (viewModel.settings.storageMarginMb < SettingsSave.RECOMMENDED_MIN_STORAGE_MARGIN_MB)
                        stringResource(R.string.storage_margin_too_low_warning) else null,
                    value = viewModel.settings.storageMarginMb,
                    suffix = "MB"
                ) { viewModel.onMarginChanged(it) }

                SettingsDropdownRow(
                    title = stringResource(R.string.default_format),
                    options = viewModel.formatOptions,
                    selected = viewModel.selectedFormat
                ) { viewModel.onFormatChanged(it as TrackType) }

                SettingsDropdownRow(
                    title = stringResource(R.string.default_audio_codec),
                    options = viewModel.targetAC,
                    selected = viewModel.selectedAC
                ) { viewModel.onACChanged(it as TargetFormat) }

                SettingsDropdownRow(
                    title = stringResource(R.string.default_video_codec),
                    options = viewModel.targetVC,
                    selected = viewModel.selectedVC
                ) { viewModel.onVCChanged(it as TargetFormat) }
            }

            // -- Notifications Group --
            SettingsGroup(title = stringResource(R.string.n_settings), initiallyExpanded = false) {
                // -- Toggle: Notify on finish --
                SettingsToggleRow(
                    title = stringResource(R.string.n_download_finished),
                    checked = viewModel.settings.notifyOnFinish,
                    onCheckedChange = { viewModel.onNotifyOnFinishChanged(it) }
                )

                // -- Toggle: Notify on fail --
                SettingsToggleRow(
                    title = stringResource(R.string.n_download_failed),
                    checked = viewModel.settings.notifyOnFail,
                    onCheckedChange = { viewModel.onNotifyOnFailChanged(it) }
                )
            }

            SettingsGroup(title = stringResource(R.string.set_scr_customization), initiallyExpanded = false) {
                SettingsDropdownRow(
                    title = stringResource(R.string.set_scr_theme),
                    options = viewModel.themeOptions,
                    selected = viewModel.selectedTheme,
                    onSelectChange = { viewModel.onThemeChanged(it as Theme) }
                )

                SettingsDropdownRow(
                    title = stringResource(R.string.set_preview),
                    description = stringResource(R.string.set_preview_desc),
                    options = viewModel.resolutionOptions,
                    selected = viewModel.selectedRes,
                    onSelectChange = { viewModel.onResolutionChange(it as Int) }
                )
            }

            // -- Notifications Group --
            SettingsGroup(title = stringResource(R.string.set_app_settings), initiallyExpanded = false) {
                // -- Dropdown: Change language --
                SettingsDropdownRow(
                    title = stringResource(R.string.language),
                    options = viewModel.langOptions,
                    selected = viewModel.selectedLang,
                    onSelectChange = { viewModel.onLanguageChange(it as String) }
                )

                if (MusicAxsClient.isMusicAxsInstalled(context)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.set_add_songs),
                        description = stringResource(R.string.set_add_songs_desc),
                        checked = viewModel.settings.addToMusicAxs,
                        onCheckedChange = { viewModel.onMusicAxsChanged(it) }
                    )
                }
            }

            // -- Updates Group --
            if (!BuildConfig.IS_FDROID) {
                SettingsGroup(title = stringResource(R.string.u_settings), initiallyExpanded = false) {
                    SettingsToggleRow(
                        title = stringResource(R.string.d_reminder),
                        checked = viewModel.settings.dontShowUpdate,
                        onCheckedChange = { viewModel.onDontShowUpdateChanged(it) }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { logsOpened = true },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Open crash logs", fontSize = 12.sp)
        }

        // -- Version label --
        Text(
            text = "$versionName ($versionCode)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(8.dp)
        )
    }

    if (logsOpened) {
        CrashLogsDialog(
            context = context,
            onDismiss = { logsOpened = false }
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        if (description != null) {
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
    title: String,
    description: String? = null,
    options: Map<out Any, String>,
    selected: Any,
    onSelectChange: (Any) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val minDropdownWidth = remember(options) {
        val maxPx = options.values.maxOfOrNull { text ->
            textMeasurer.measure(text, TextStyle(fontSize = 16.sp)).size.width
        } ?: 0
        with(density) { maxPx.toDp() + 32.dp }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                Row(
                    modifier = Modifier
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .wrapContentWidth()
                        .border(
                            width = 2.dp,
                            color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = options.entries.find { it.key == selected }?.value ?: selected.toString(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                        contentDescription = null,
                        tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(15.dp)
                    )
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .widthIn(min = minDropdownWidth)
                        .background(MaterialTheme.colorScheme.secondary)
                ) {
                    options.forEach { (backendValue, displayLabel) ->
                        DropdownMenuItem(
                            text = { Text(displayLabel, color = MaterialTheme.colorScheme.onSecondary) },
                            onClick = {
                                onSelectChange(backendValue)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (description != null) {
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CrashLogsDialog(context: Context, onDismiss: () -> Unit) {
    val logs = remember {
        File(context.filesDir, "crash_logs")
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var copiedFile by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        title = { Text("Crash Logs", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (logs.isEmpty()) {
                Text("No crash logs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    logs.forEach { file ->
                        val label = file.nameWithoutExtension.toLongOrNull()
                            ?.let { millis ->
                                LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(millis),
                                    java.time.ZoneId.systemDefault()
                                ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            }
                            ?: file.name

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Crash Log", file.readText())
                                )
                                copiedFile = file.name
                            }) {
                                Icon(
                                    painter = painterResource(
                                        if (copiedFile == file.name) R.drawable.check
                                        else R.drawable.copy
                                    ),
                                    contentDescription = "Copy",
                                    tint = if (copiedFile == file.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun SettingsNumberInputRow(
    title: String,
    description: String? = null,
    warning: String? = null,
    value: Int,
    suffix: String = "",
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText.filter { it.isDigit() }
                    text.toIntOrNull()?.let { onValueChange(it) }
                },
                modifier = Modifier.width(110.dp),
                singleLine = true,
                suffix = { if (suffix.isNotEmpty()) Text(suffix, fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        if (description != null) {
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        }
        if (warning != null) {
            Text(warning, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
        }
    }
}