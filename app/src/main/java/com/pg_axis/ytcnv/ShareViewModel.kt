package com.pg_axis.ytcnv

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.ytcnv.settings.SettingsSave
import com.pg_axis.ytcnv.utils.DownloadNotificationService
import com.pg_axis.ytcnv.utils.DownloadUtils
import com.pg_axis.ytcnv.utils.DownloadUtils.downloadStream
import com.pg_axis.ytcnv.utils.FileSaver
import com.pg_axis.ytcnv.utils.StringUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.net.URL
import kotlin.math.abs

enum class SheetState { LOADING_METADATA, PICKING }

class ShareViewModel(application: Application, rawUrl: String) : AndroidViewModel(application) {
    private val context = getApplication<Application>()

    val settings = SettingsSave.getInstance(context)

    var sheetState by mutableStateOf(
        if (settings.quickDwnld) SheetState.PICKING else SheetState.LOADING_METADATA
    )
    var formatIndex by mutableIntStateOf(0) // 0 = mp3, 1 = mp4
    var qualityOptions by mutableStateOf<Map<Int, String>>(emptyMap())
    var qualityIndex by mutableIntStateOf(0)

    private var streamInfo: StreamInfo? = null
    private var muxedFallbackStream: VideoStream? = null
    private val cleanedUrl = StringUtils.cleanUrl(rawUrl)

    init {
        if (!settings.quickDwnld) fetchMetadata()
    }

    private fun fetchMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=$cleanedUrl"
                )
                streamInfo = info
                buildQualityOptions(formatIndex, info)
                withContext(Dispatchers.Main) { sheetState = SheetState.PICKING }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { sheetState = SheetState.PICKING }
            }
        }
    }

    fun onFormatChanged(index: Int) {
        formatIndex = index
        qualityIndex = 0
        streamInfo?.let { buildQualityOptions(index, it) }
    }

    private fun buildQualityOptions(format: Int, info: StreamInfo) {
        if (format == 0) {
            val audioStreams = info.audioStreams
                .filter { it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null }
                .map { it.averageBitrate }
                .distinct()
                .sortedDescending()

            if (audioStreams.isEmpty()) {
                muxedFallbackStream = if (settings.muxedFallback) info.videoStreams.maxByOrNull { it.height } else null
                // TODO: disable download button / show error when qualityOptions is empty and muxedFallback is off
                qualityOptions = mapOf(-1 to "Default")
            } else {
                muxedFallbackStream = null
                qualityOptions = audioStreams.associateWith { "${it}kbps" }
            }
        } else {
            val videoOptions = info.videoOnlyStreams
                .map { it.height }
                .distinct()
                .sortedDescending()

            if (videoOptions.isEmpty()) {
                muxedFallbackStream = if (settings.muxedFallback) info.videoStreams.maxByOrNull { it.height } else null
                // TODO: disable download button / show error when qualityOptions is empty and muxedFallback is off
                qualityOptions = mapOf(-1 to "Default")
            } else {
                muxedFallbackStream = null
                qualityOptions = videoOptions.associateWith { "${it}p" }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startDownload(onDone: () -> Unit) {
        onDone()
        GlobalScope.launch(Dispatchers.IO) {
            val m4aPath = File(context.cacheDir, "share_audio.m4a").absolutePath
            val mp4Path = File(context.cacheDir, "share_video.mp4").absolutePath
            val semiOutput = File(context.filesDir, "share_semi_output.mp4").absolutePath
            val semiOutputAudio = File(context.filesDir, "share_semi_output.mp3").absolutePath
            val imagePath = File(context.cacheDir, "share_thumbnail.jpg").absolutePath

            fun deleteFiles() {
                listOf(m4aPath, mp4Path, semiOutput, semiOutputAudio, imagePath)
                    .forEach { if (File(it).exists()) File(it).delete() }
            }

            fun stopService() {
                context.stopService(Intent(context, DownloadNotificationService::class.java))
            }

            try {
                DownloadNotificationService.setProgressType(false)
                withContext(Dispatchers.Main) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, DownloadNotificationService::class.java)
                    )
                }

                val info = streamInfo ?: StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=$cleanedUrl"
                )

                if (info.duration <= 0) {
                    if (settings.notifyOnFail)
                        DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_live))
                    stopService()
                    withContext(Dispatchers.Main) { onDone() }
                    return@launch
                }

                var author = StringUtils.cleanAuthor(info.uploaderName ?: "")
                val (title, cleanedAuthor) = StringUtils.cleanTitle(info.name ?: "YouTube_Video", author)
                author = cleanedAuthor

                // Update download history
                withContext(Dispatchers.Main) {
                    val existing = settings.downloadHistory.find { it.urlOrId == cleanedUrl }
                    val newItem = SettingsSave.HistoryItem(info.name?.trim() ?: title, cleanedUrl)
                    val updated = settings.downloadHistory.toMutableList()
                    updated.remove(existing)
                    updated.add(0, newItem)
                    settings.downloadHistory = updated
                }

                // Download thumbnail
                var hasThumbnail = false
                val thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url
                if (thumbnailUrl != null) {
                    try {
                        val bytes = URL(thumbnailUrl).readBytes()
                        val ext = DownloadUtils.detectImageExtension(bytes)
                        val tempThumbnail = File(context.cacheDir, "share_thumb.$ext").absolutePath
                        File(tempThumbnail).writeBytes(bytes)
                        val result = DownloadUtils.runFFmpeg("-y -i \"$tempThumbnail\" -frames:v 1 \"$imagePath\"")
                        hasThumbnail = result && File(imagePath).exists()
                        if (File(tempThumbnail).exists()) File(tempThumbnail).delete()
                    } catch (_: Exception) { }
                }

                DownloadNotificationService.setProgressType(true)
                DownloadNotificationService.startTimer()
                var lastNotifiedPercent = -1
                var lastNotifiedTime = 0L

                if (formatIndex == 0) {
                    // ─── MP3 ───
                    fun getAudioStream(): AudioStream? {
                        val audioStream = if (settings.quickDwnld || streamInfo == null) {
                            info.audioStreams
                                .filter { it.format?.name?.contains("m4a", ignoreCase = true) == true && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
                                .maxByOrNull { it.averageBitrate }
                        } else {
                            val selectedBitrate = qualityOptions.keys.elementAtOrNull(qualityIndex)
                            info.audioStreams
                                .filter { it.format?.name?.contains("m4a", ignoreCase = true) == true && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
                                .minByOrNull { abs(it.averageBitrate - (selectedBitrate ?: 0)) }
                        } ?: info.audioStreams.maxByOrNull { it.averageBitrate }

                        return audioStream
                    }

                    val audioStream = getAudioStream()
                    val muxed = if (settings.muxedFallback) muxedFallbackStream ?: info.videoStreams.maxByOrNull { it.height } else null
                    val inputForFFmpeg: String

                    if (audioStream != null) {
                        inputForFFmpeg = m4aPath
                        Log.d("YTCnv", "audioStream.content = ${audioStream.content}")
                        downloadStream(audioStream.content, m4aPath,
                            onProgress = { progress ->
                                val percent = (progress * 100).toInt().coerceAtMost(100)
                                val now = System.currentTimeMillis()
                                if (percent != lastNotifiedPercent && now - lastNotifiedTime >= 500) {
                                    lastNotifiedPercent = percent
                                    lastNotifiedTime = now
                                    DownloadNotificationService.updateProgress(context, percent)
                                }
                            },
                            urlRefresher = { getAudioStream()?.content ?: audioStream.content }
                        )
                    } else if (muxed != null) {
                        inputForFFmpeg = mp4Path
                        Log.d("YTCnv", "Falling back to muxed stream: ${muxed.content}")
                        downloadStream(muxed.content, mp4Path,
                            onProgress = { progress ->
                                val percent = (progress * 100).toInt().coerceAtMost(100)
                                val now = System.currentTimeMillis()
                                if (percent != lastNotifiedPercent && now - lastNotifiedTime >= 500) {
                                    lastNotifiedPercent = percent
                                    lastNotifiedTime = now
                                    DownloadNotificationService.updateProgress(context, percent)
                                }
                            },
                            urlRefresher = {
                                StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$cleanedUrl")
                                    .videoStreams.maxByOrNull { it.height }?.content ?: muxed.content
                            }
                        )
                    } else {
                        throw Exception("No audio streams available for this video.")
                    }

                    DownloadNotificationService.setProgressType(false)
                    DownloadNotificationService.updateProgress(context, 0, finale = true)

                    val ffmpegCmd = buildString {
                        append("-y -i \"$inputForFFmpeg\" ")
                        if (hasThumbnail) append("-i \"$imagePath\" ")
                        append("-map 0:a ")
                        if (hasThumbnail) append("-map 1:v ")
                        append("-c:a libmp3lame -b:a 128k -ac 2 -af anull ")
                        if (hasThumbnail) {
                            append("-c:v mjpeg -disposition:v attached_pic ")
                            append("-metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover\" ")
                        }
                        append("-metadata title=\"$title\" -metadata artist=\"$author\" ")
                        append("-threads 1 \"$semiOutputAudio\"")
                    }

                    if (DownloadUtils.runFFmpeg(ffmpegCmd)) {
                        FileSaver.saveAudio(context, "$title.mp3", semiOutputAudio, settings.fileUri.ifBlank { null })
                        if (settings.notifyOnFinish)
                            DownloadNotificationService.showFinishNotification(context, "$title.mp3")
                    } else {
                        if (settings.notifyOnFail)
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_failed))
                    }

                } else {
                    // ─── MP4 ───
                    fun getAudioStream(): AudioStream? = info.audioStreams
                        .filter { it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null }
                        .maxByOrNull { it.averageBitrate }
                        ?: info.audioStreams.maxByOrNull { it.averageBitrate }

                    fun getVideoStream(): VideoStream? = if (settings.quickDwnld || streamInfo == null) {
                        if (settings.use4K)
                            info.videoOnlyStreams.maxByOrNull { it.height }
                        else
                            info.videoOnlyStreams
                                .filter { it.height <= 1080 && it.format?.name?.contains("mpeg-4", ignoreCase = true) == true }
                                .maxByOrNull { it.height }
                                ?: info.videoOnlyStreams.maxByOrNull { it.height }
                    } else {
                        val selectedHeight = qualityOptions.keys.elementAtOrNull(qualityIndex)
                        info.videoOnlyStreams
                            .filter { it.format?.name?.contains("mpeg-4", ignoreCase = true) == true || settings.use4K }
                            .firstOrNull { it.height == selectedHeight }
                            ?: info.videoOnlyStreams.maxByOrNull { it.height }
                    }

                    val audioStream = getAudioStream()
                    val videoStream = getVideoStream()
                    val muxed = if (settings.muxedFallback) muxedFallbackStream ?: info.videoStreams.maxByOrNull { it.height } else null

                    if (videoStream != null) {
                        val isMoreThan1080p = videoStream.height > 1080

                        withContext(Dispatchers.IO) {
                            val audioJob = launch {
                                if (audioStream != null) {
                                    downloadStream(audioStream.content, m4aPath, onProgress = {}, urlRefresher = { getAudioStream()?.content ?: audioStream.content })
                                } else if (muxed != null) {
                                    downloadStream(muxed.content, m4aPath, onProgress = {}, urlRefresher = {
                                        StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$cleanedUrl")
                                            .videoStreams.maxByOrNull { it.height }?.content ?: muxed.content
                                    })
                                } else {
                                    throw Exception("No audio streams available for this video.")
                                }
                            }
                            val videoJob = launch {
                                downloadStream(
                                    videoStream.content, mp4Path,
                                    onProgress = { progress ->
                                        val percent = (progress * 100).toInt()
                                        val now = System.currentTimeMillis()
                                        if (percent != lastNotifiedPercent && now - lastNotifiedTime >= 500) {
                                            lastNotifiedPercent = percent
                                            lastNotifiedTime = now
                                            DownloadNotificationService.updateProgress(context, percent)
                                        }
                                    },
                                    urlRefresher = { getVideoStream()?.content ?: videoStream.content }
                                )
                            }
                            audioJob.join()
                            videoJob.join()
                        }

                        DownloadNotificationService.setProgressType(false)
                        DownloadNotificationService.updateProgress(context, 0, finale = true)

                        val ffmpegArgs = if (settings.use4K && isMoreThan1080p) {
                            "-y -i \"$mp4Path\" -i \"$m4aPath\" -c:v libx264 -pix_fmt yuv420p -preset superfast -crf 23 " +
                                    "-c:a copy -map 0:v:0 -map 1:a:0 -shortest " +
                                    "-metadata title=\"$title\" -metadata artist=\"$author\" \"$semiOutput\""
                        } else {
                            "-y -i \"$mp4Path\" -i \"$m4aPath\" -c:v copy -c:a copy -map 0:v:0 -map 1:a:0 -shortest " +
                                    "-metadata title=\"$title\" -metadata artist=\"$author\" \"$semiOutput\""
                        }

                        if (DownloadUtils.runFFmpeg(ffmpegArgs)) {
                            FileSaver.saveVideo(context, "$title.mp4", semiOutput, settings.fileVidUri.ifBlank { null })
                            if (settings.notifyOnFinish)
                                DownloadNotificationService.showFinishNotification(context, "$title.mp4")
                        } else {
                            if (settings.notifyOnFail)
                                DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_failed))
                        }

                    } else if (muxed != null) {
                        // No separate streams available — download muxed and copy directly
                        downloadStream(
                            muxed.content, mp4Path,
                            onProgress = { progress ->
                                val percent = (progress * 100).toInt()
                                val now = System.currentTimeMillis()
                                if (percent != lastNotifiedPercent && now - lastNotifiedTime >= 500) {
                                    lastNotifiedPercent = percent
                                    lastNotifiedTime = now
                                    DownloadNotificationService.updateProgress(context, percent)
                                }
                            },
                            urlRefresher = {
                                StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$cleanedUrl")
                                    .videoStreams.maxByOrNull { it.height }?.content ?: muxed.content
                            }
                        )

                        DownloadNotificationService.setProgressType(false)
                        DownloadNotificationService.updateProgress(context, 0, finale = true)

                        val ffmpegArgs = "-y -i \"$mp4Path\" -c copy " +
                                "-metadata title=\"$title\" -metadata artist=\"$author\" \"$semiOutput\""

                        if (DownloadUtils.runFFmpeg(ffmpegArgs)) {
                            FileSaver.saveVideo(context, "$title.mp4", semiOutput, settings.fileVidUri.ifBlank { null })
                            if (settings.notifyOnFinish)
                                DownloadNotificationService.showFinishNotification(context, "$title.mp4")
                        } else {
                            if (settings.notifyOnFail)
                                DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_failed))
                        }

                    } else {
                        throw Exception("No video streams available for this video.")
                    }
                }
            } catch (e: Exception) {
                DownloadNotificationService.setProgressType(false)
                if (settings.notifyOnFail) {
                    val msg = when {
                        e.message?.contains("403") == true || e.message?.contains("404") == true ->
                            context.getString(R.string.pm_unavailable)
                        e.message?.contains("ID or URL") == true ->
                            context.getString(R.string.pm_invalid_url)
                        e.message?.contains("Software caused connection abort") == true ->
                            context.getString(R.string.pm_network_error)
                        e.message?.contains("streams available for this video") == true -> {
                            context.getString(R.string.pm_no_streams)
                        }
                        else -> e.message ?: context.getString(R.string.pm_error)
                    }
                    DownloadNotificationService.showFailedNotification(context, msg)
                }
            } finally {
                deleteFiles()
                stopService()
                settings.isDownloadRunning = false
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }
}