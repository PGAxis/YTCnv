package com.pg_axis.ytcnv.side_pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.models.TrackType

private data class FormatEntry(
    val extension: String,
    val codec: String,
    val badge: String,
    val description: String,
)

private data class FormatGroup(
    val kind: TrackType,
    val entries: List<FormatEntry>,
)

@Composable
fun FormatInfoScreen(
    onBack: () -> Unit
) {
    val formatGroups = listOf(
        FormatGroup(
            kind = TrackType.AUDIO,
            entries = listOf(
                FormatEntry(
                    extension = ".m4a",
                    codec = "AAC",
                    badge = stringResource(R.string.format_badge_licensed),
                    description = stringResource(R.string.format_desc_m4a),
                ),
                FormatEntry(
                    extension = ".opus",
                    codec = "Opus",
                    badge = stringResource(R.string.format_badge_open_royalty_free),
                    description = stringResource(R.string.format_desc_opus),
                ),
                FormatEntry(
                    extension = ".mp3",
                    codec = "MP3",
                    badge = stringResource(R.string.format_badge_open_royalty_free),
                    description = stringResource(R.string.format_desc_mp3),
                ),
            ),
        ),
        FormatGroup(
            kind = TrackType.VIDEO,
            entries = listOf(
                FormatEntry(
                    extension = ".mp4",
                    codec = "H.264",
                    badge = stringResource(R.string.format_badge_licensed),
                    description = stringResource(R.string.format_desc_mp4),
                ),
                FormatEntry(
                    extension = ".webm",
                    codec = "VP9",
                    badge = stringResource(R.string.format_badge_open_royalty_free),
                    description = stringResource(R.string.format_desc_webm),
                ),
            ),
        ),
    )
    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            SideScreenHeader(
                onBack = onBack
            ) {
                Spacer(Modifier.width(4.dp))

                Text(
                    text = stringResource(R.string.format_info_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp)
            ) {
                formatGroups.forEach { group ->
                    item(key = "header_${group.kind}") {
                        Text(
                            text = if (group.kind == TrackType.AUDIO) stringResource(R.string.audio) else stringResource(
                                R.string.video
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(group.entries, key = { it.extension }) { entry ->
                        FormatCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatCard(entry: FormatEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append(entry.extension)
                        append(" - ")
                        append(entry.codec)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = entry.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}