package com.pg_axis.ytcnv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.ui.theme.*

@Composable
@Preview(showBackground = true, showSystemUi = true)
fun SettingsPreview() {
    val mainModel = remember { MainViewModel(Application()) }
    val viewModel = remember { SettingsViewModel(mainModel, Application()) }
    YTCnvTheme {
        SettingsScreen({}, viewModel)
    }
}

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = packageInfo.longVersionCode

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
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
            IconButton(onClick = onBack, shape = CutCornerShape(0.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = "Back",
                    tint = CyanLight
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Folder picker ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            folderPickerLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = BackgroundDark
                        )
                    ) {
                        Text(stringResource(R.string.download_dest))
                    }
                    Text(
                        text = viewModel.mainFolder + viewModel.finalFolder,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
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

            // ─── Notifications Group ───
            SettingsGroup(title = stringResource(R.string.l_settings), initiallyExpanded = false) {
                // ─── Dropdown: Change language ───
                SettingsDropdownRow(
                    label = stringResource(R.string.language),
                    options = viewModel.langOptions,
                    selected = viewModel.selectedLang,
                    onSelectChange = { viewModel.onLanguageChange(it) }
                )
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

        // ─── Version label ───
        Text(
            text = "$versionName ($versionCode)",
            color = Gray500,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(8.dp)
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = CyanLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(
                        id = if (isExpanded) R.drawable.expand_less
                        else R.drawable.expand_more
                    ),
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = CyanLight
                )
            }

            // Content
            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(bottom = 8.dp),
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
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BackgroundDark,
                checkedTrackColor = CyanPrimary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceVariantDark
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
    label: String,
    options: Map<String, String>,
    selected: String,
    onSelectChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
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
                        color = if (expanded) CyanPrimary else SurfaceVariantDark,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = options.entries.find { it.key == selected }?.value ?: selected,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                    contentDescription = null,
                    tint = if (expanded) CyanPrimary else TextSecondary,
                    modifier = Modifier.height(15.dp)
                )
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (backendValue, displayLabel) ->
                    DropdownMenuItem(
                        text = { Text(displayLabel, color = TextPrimary) },
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