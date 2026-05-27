package com.pg_axis.ytcnv.dialogs

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pg_axis.ytcnv.ui.theme.*
import androidx.core.net.toUri
import com.pg_axis.ytcnv.R

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    val context = LocalContext.current
    var dontShowAgain by remember { mutableStateOf(false) }

    val changelogText = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)) {
            append("Change log\n\n")
        }
        val cleaned = updateInfo.changelog
            .replace("### Change log\r\n\r\n", "")
            .replace("- ", "• ")
        append(cleaned)
    }

    Dialog(onDismissRequest = { onDismiss(dontShowAgain) }) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.ud_update_available),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${stringResource(R.string.ud_new_v_1)} (${updateInfo.version}) ${stringResource(R.string.ud_new_v_2)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Changelog
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = changelogText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Don't show again checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CyanPrimary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = stringResource(R.string.ud_no_remind),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onDismiss(dontShowAgain) }) {
                        Text(stringResource(R.string.ud_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val tagName = updateInfo.version
                            val apkUrl = "https://github.com/PGAxis/YTCnv/releases/download/v${tagName}/youtubeConverter-${tagName}.apk"
                            val fileName = "youtubeConverter-$tagName.apk"

                            val request = DownloadManager.Request(apkUrl.toUri())
                                .setTitle("YTCnv $tagName")
                                .setDescription("Downloading update...")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                .setMimeType("application/vnd.android.package-archive")

                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            dm.enqueue(request)

                            onDismiss(dontShowAgain)
                        }
                    ) {
                        Text(stringResource(R.string.download))
                    }
                }
            }
        }
    }
}