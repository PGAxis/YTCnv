package com.pg_axis.ytcnv

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

fun mapItems(
    items: List<InfoItem>?,
    isMusic: Boolean
): List<SearchResultItem> =
    items?.mapNotNull { item ->
        val name = item.name
        val videoId = extractId(item)
        val imageUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: ""
        val url = item.url

        when (item) {
            is StreamInfoItem -> {
                val uploader = item.uploaderName ?: ""
                val duration = formatDuration(item.duration)
                val uploaderId = channelIdFromUrl(item.uploaderUrl)

                if (isMusic) {
                    MusicResultItem(
                        name = name, itemId = videoId, uploader = uploader, duration = duration, imageUrl = imageUrl, url = url, uploaderId = uploaderId
                    )
                } else {
                    VideoResultItem(
                        name = name, itemId = videoId, uploader = uploader, duration = duration, imageUrl = imageUrl, url = url, uploaderId = uploaderId
                    )
                }
            }

            is ChannelInfoItem -> ChannelResultItem(
                name = name,
                itemId = videoId,
                imageUrl = imageUrl,
                url = url
            )

            is PlaylistInfoItem -> PlaylistResultItem(
                name = name,
                itemId = videoId,
                uploader = item.uploaderName ?: "",
                imageUrl = imageUrl,
                url = url,
                uploaderId = channelIdFromUrl(item.uploaderUrl)
            )

            else -> null
        }
    } ?: emptyList()

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "Live"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

fun extractId(item: InfoItem): String = when (item) {
    is StreamInfoItem -> item.url.substringAfter("v=").substringBefore("&")
    is PlaylistInfoItem -> item.url.substringAfter("list=").substringBefore("&")
    is ChannelInfoItem -> item.url.substringAfterLast("/").substringBefore("?")
    else -> ""
}

private fun channelIdFromUrl(url: String?): String? =
    url?.takeIf { it.isNotBlank() }
        ?.substringAfterLast("/")
        ?.substringBefore("?")
        ?.takeIf { it.isNotBlank() }