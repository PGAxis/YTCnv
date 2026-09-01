package com.pg_axis.ytcnv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.models.QualityOption
import com.pg_axis.ytcnv.services.Theme
import com.pg_axis.ytcnv.settings.SettingsSave
import com.pg_axis.ytcnv.ui.theme.*
import com.pg_axis.ytcnv.utils.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: run { finish(); return }

        val viewModel = ShareViewModel(application, url)

        NewPipe.init(
            NewPipeDownloader(),
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )

        setContent {
            val settings = remember { SettingsSave.getInstance(this) }
            val colorScheme = when (settings.theme) {
                Theme.CYAN -> YTCnvCyanScheme
                Theme.GRAYSCALE -> YTCnvGrayscaleScheme
                Theme.EMBER -> YTCnvEmberScheme
                Theme.AETHER -> YTCnvAetherScheme
                Theme.PHOSPHOR -> YTCnvPhosphorScheme
                Theme.CHALK -> YTCnvChalkScheme
                Theme.SUNSHINE -> YTCnvSoleilScheme
                Theme.BORDO ->YTCnvBordoScheme
                Theme.VOID -> YTCnvVoidScheme
            }
            YTCnvTheme(colorScheme = colorScheme) {
                ShareBottomSheet(
                    viewModel = viewModel,
                    rawUrl = url,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    viewModel: ShareViewModel,
    rawUrl: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)


    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Title ---
            Text(
                text = stringResource(R.string.share_quick_download),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // --- URL preview ---
            Text(
                text = rawUrl,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            when (viewModel.sheetState) {
                SheetState.LOADING_METADATA -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        Text(stringResource(R.string.share_loading_quality), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }

                SheetState.PICKING -> {
                    if (viewModel.settings.quickDwnld) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("MP3", "MP4").forEachIndexed { index, label ->
                                val selected = viewModel.formatIndex == index
                                OutlinedButton(
                                    onClick = { viewModel.onFormatChanged(index) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 2.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            var formatExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = formatExpanded,
                                onExpandedChange = { formatExpanded = !formatExpanded }
                            ) {
                                OutlinedTextField(
                                    value = viewModel.selectedFormatOption?.displayName ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.format), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(formatExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
                                        focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = formatExpanded,
                                    onDismissRequest = { formatExpanded = false },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    viewModel.formatOptions.forEachIndexed { index, option ->
                                        DropdownMenuItem(
                                            text = { Text(option.displayName, color = MaterialTheme.colorScheme.onPrimary) },
                                            onClick = {
                                                viewModel.onDetailedFormatChanged(index)
                                                formatExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (viewModel.qualityOptions.isNotEmpty()) {
                                var qualityExpanded by remember { mutableStateOf(false) }
                                val selectedQuality = viewModel.qualityOptions.getOrNull(viewModel.qualityIndex)

                                ExposedDropdownMenuBox(
                                    expanded = qualityExpanded,
                                    onExpandedChange = { qualityExpanded = !qualityExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedQuality?.let { qualityLabel(it) } ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.quality), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(qualityExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
                                            focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                    ExposedDropdownMenu(
                                        expanded = qualityExpanded,
                                        onDismissRequest = { qualityExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        viewModel.qualityOptions.forEachIndexed { index, option ->
                                            DropdownMenuItem(
                                                text = { Text(qualityLabel(option), color = MaterialTheme.colorScheme.onPrimary) },
                                                onClick = {
                                                    viewModel.onQualityChanged(index)
                                                    qualityExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                if (viewModel.isFetchingQualitySizes) {
                                    Text(
                                        text = stringResource(R.string.fetching_sizes),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // --- Download button ---
                    Button(
                        onClick = { viewModel.startDownload(onDone = onDismiss) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.download), color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun qualityLabel(option: QualityOption): String {
    val nativePart = if (!option.isNative) " · converted" else ""
    val sizePart = option.sizeBytes?.let { " (${formatBytes(it)})" } ?: ""
    return "${option.displayName}$nativePart$sizePart"
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