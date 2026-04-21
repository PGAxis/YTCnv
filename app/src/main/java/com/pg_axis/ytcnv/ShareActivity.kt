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
import com.pg_axis.ytcnv.ui.theme.*
import com.pg_axis.ytcnv.utils.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

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
            YTCnvTheme {
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
        containerColor = CardDark,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Title ───
            Text(
                text = stringResource(R.string.share_quick_download),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // ─── URL preview ───
            Text(
                text = rawUrl,
                color = TextSecondary,
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
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyanPrimary, strokeWidth = 2.dp)
                        Text(stringResource(R.string.share_loading_quality), color = TextSecondary, fontSize = 14.sp)
                    }
                }

                SheetState.PICKING -> {
                    // ─── Format picker ───
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
                                    containerColor = if (selected) CyanPrimary else CardDark
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    color = if (selected) CyanPrimary else AquaAccent
                                )
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) BackgroundDark else CyanLight,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // ─── Quality picker (only when not quick and metadata loaded) ───
                    if (!viewModel.settings.quickDwnld && viewModel.qualityOptions.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedLabel = viewModel.qualityOptions.values.elementAtOrNull(viewModel.qualityIndex) ?: ""

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.quality), color = TextSecondary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanPrimary,
                                    unfocusedBorderColor = AquaAccent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                containerColor = CardDark
                            ) {
                                viewModel.qualityOptions.values.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(label, color = TextPrimary) },
                                        onClick = {
                                            viewModel.qualityIndex = index
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ─── Download button ───
                    Button(
                        onClick = { viewModel.startDownload(onDone = onDismiss) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text(stringResource(R.string.download), color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}