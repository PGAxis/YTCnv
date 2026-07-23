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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.services.MusicAxsClient
import com.pg_axis.ytcnv.services.Theme
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
        // ─── Header ───
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
                text = stringResource(R.string.settings_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Folder picker ───
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

            // ─── Download Settings Group ───
            SettingsGroup(title = stringResource(R.string.d_settings)) {
                // ─── Toggle: 4K ───
                SettingsToggleRow(
                    label = stringResource(R.string.up_to_4k),
                    checked = viewModel.settings.use4K,
                    onCheckedChange = { viewModel.onUse4KChanged(it) }
                )

                // ─── Toggle: Quick download ───
                SettingsToggleRow(
                    label = stringResource(R.string.quick_download),
                    checked = viewModel.settings.quickDwnld,
                    onCheckedChange = { viewModel.onQuickDwnldChanged(it) }
                )

                SettingsToggleRow(
                    label = stringResource(R.string.muxed_fallback),
                    checked = viewModel.settings.muxedFallback,
                    onCheckedChange = { viewModel.onMuxedChanged(it) }
                )
            }

            // ─── Notifications Group ───
            SettingsGroup(title = stringResource(R.string.n_settings), initiallyExpanded = false) {
                // ─── Toggle: Notify on finish ───
                SettingsToggleRow(
                    label = stringResource(R.string.n_download_finished),
                    checked = viewModel.settings.notifyOnFinish,
                    onCheckedChange = { viewModel.onNotifyOnFinishChanged(it) }
                )

                // ─── Toggle: Notify on fail ───
                SettingsToggleRow(
                    label = stringResource(R.string.n_download_failed),
                    checked = viewModel.settings.notifyOnFail,
                    onCheckedChange = { viewModel.onNotifyOnFailChanged(it) }
                )
            }

            SettingsGroup(title = stringResource(R.string.set_scr_customization), initiallyExpanded = false) {
                SettingsDropdownRow(
                    label = stringResource(R.string.set_scr_theme),
                    options = viewModel.themeOptions,
                    selected = viewModel.selectedTheme,
                    onSelectChange = { viewModel.onThemeChanged(it as Theme) }
                )

                SettingsDropdownRow(
                    label = stringResource(R.string.set_preview),
                    options = viewModel.resolutionOptions,
                    selected = viewModel.selectedRes,
                    onSelectChange = { viewModel.onResolutionChange(it as Int) }
                )
            }

            // ─── Notifications Group ───
            SettingsGroup(title = stringResource(R.string.set_app_settings), initiallyExpanded = false) {
                // ─── Dropdown: Change language ───
                SettingsDropdownRow(
                    label = stringResource(R.string.language),
                    options = viewModel.langOptions,
                    selected = viewModel.selectedLang,
                    onSelectChange = { viewModel.onLanguageChange(it as String) }
                )

                if (MusicAxsClient.isMusicAxsInstalled(context)) {
                    SettingsToggleRow(
                        label = stringResource(R.string.set_add_songs),
                        description = stringResource(R.string.set_add_songs_desc),
                        checked = viewModel.settings.addToMusicAxs,
                        onCheckedChange = { viewModel.onMusicAxsChanged(it) }
                    )
                }
            }

            // ─── Updates Group ───
            if (!BuildConfig.IS_FDROID) {
                SettingsGroup(title = stringResource(R.string.u_settings), initiallyExpanded = false) {
                    // ─── Toggle: Don't show updates ───
                    SettingsToggleRow(
                        label = stringResource(R.string.d_reminder),
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

        // ─── Version label ───
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
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (description != null) {
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
    label: String,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (description != null) {
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
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
                modifier = Modifier.widthIn(min = minDropdownWidth).background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                options.forEach { (backendValue, displayLabel) ->
                    DropdownMenuItem(
                        text = { Text(displayLabel, color = MaterialTheme.colorScheme.onSecondaryContainer) },
                        onClick = {
                            onSelectChange(backendValue)
                            expanded = false
                        }
                    )
                }
            }
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