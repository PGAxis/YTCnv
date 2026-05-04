package com.pg_axis.ytcnv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.pg_axis.ytcnv.dialogs.*
import com.pg_axis.ytcnv.services.MusicAxsClient
import com.pg_axis.ytcnv.settings.*
import com.pg_axis.ytcnv.ui.theme.PopupDefault
import com.pg_axis.ytcnv.ui.theme.PopupError
import com.pg_axis.ytcnv.ui.theme.PopupSuccess
import com.pg_axis.ytcnv.ui.theme.TextSecondary
import com.pg_axis.ytcnv.utils.DownloadNotificationService
import com.pg_axis.ytcnv.utils.DownloadUtils
import com.pg_axis.ytcnv.utils.FileSaver
import com.pg_axis.ytcnv.utils.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

data class QualityOption(
    val key: Double,
    val displayName: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings: ISettings = try {
        SettingsSave.getInstance(application)
    } catch (_: Exception) {
        PreviewSettings()
    }

    private val isQuick get() = settings.quickDwnld
    private val context get() = getApplication<Application>()

    var showPlaylistPicker by mutableStateOf(false)
    var lastDownloadedSongUri by mutableStateOf<Uri?>(null)

    // ─── URL entry ───
    var urlEntryText by mutableStateOf("")

    // ─── Download options ───
    var downloadOptionsIsVisible by mutableStateOf(true)
    var formatPickerIsEnabled by mutableStateOf(true)
    var qualityPickerIsVisible by mutableStateOf(!isQuick)
    var qualityPickerIsEnabled by mutableStateOf(true)
    var qualityPickerSelectedIndex by mutableIntStateOf(0)
    var qualityPickerItemsSource by mutableStateOf<List<QualityOption>>(emptyList())
    var loadButtonIsVisible by mutableStateOf(!isQuick)
    var loadButtonIsEnabled by mutableStateOf(true)
    var downloadButtonIsVisible by mutableStateOf(isQuick)
    var cancelButtonIsVisible by mutableStateOf(false)

    // ─── Progress / status ───
    var dwnldProgressIsVisible by mutableStateOf(false)
    var downloadProgress by mutableFloatStateOf(0f)
    var downloadIndicatorIsVisible by mutableStateOf(false)
    var statusLabelIsVisible by mutableStateOf(false)
    var statusLabelText by mutableStateOf(AnnotatedString(""))

    // ─── Popup ───
    var popupIsVisible by mutableStateOf(false)
    var popupBackground by mutableStateOf(Color(0xFF2D2D2D))
    var popupTitle by mutableStateOf("")
    var popupMessage by mutableStateOf("")
    var popupButtonText by mutableStateOf("OK")

    // ─── Title/Author dialog ───
    var showTitleAuthorDialog by mutableStateOf(false)
    var dialogTitle by mutableStateOf("")
    var dialogAuthor by mutableStateOf("")

    // ─── Internal state ───
    private var cleanedUrl = ""
    private var audioOptions = mapOf<Double, AudioStream>()
    private var videoOptions = mapOf<Int, VideoStream>()
    private var downloadJob: Job? = null
    private var pendingOnConfirm: ((String, String) -> Unit)? = null
    var updateInfo by mutableStateOf<UpdateInfo?>(null)

    var showTermsDialog by mutableStateOf(!settings.termsAccepted)

    // ─── Actions ───
    suspend fun checkForUpdates(context: Context) {
        val info = UpdateChecker.checkForUpdates(context, settings)
        if (info != null) {
            withContext(Dispatchers.Main) {
                updateInfo = info
            }
        }
    }

    fun onUpdateDialogDismissed(dontShowAgain: Boolean) {
        updateInfo = null
        settings.dontShowUpdate = dontShowAgain
        (settings as? SettingsSave)?.saveSettings()
    }

    fun onTermsAccepted() {
        settings.termsAccepted = true
        (settings as? SettingsSave)?.saveSettings()
        showTermsDialog = false
    }

    fun onTermsDeclined() {
        showTermsDialog = false
    }

    fun onUrlChanged(value: String) { urlEntryText = value }
    fun onFormatChanged(index: Int) {
        println("Index: $index, audioOptions: ${audioOptions.size}, videoOptions: ${videoOptions.size}")
        formatPickerSelectedIndex = index
        if (audioOptions.isNotEmpty() && videoOptions.isNotEmpty()) {
            qualityPickerItemsSource = when (index) {
                0 -> audioOptions.entries.map { QualityOption(it.key, "${it.key.toInt()} kbps") }
                1 -> videoOptions.entries.map { QualityOption(it.key.toDouble(), "${it.key}p") }
                else -> emptyList()
            }
            qualityPickerSelectedIndex = 0
        }
    }
    fun onQualityChanged(index: Int) { qualityPickerSelectedIndex = index }
    fun onClosePopupClicked() { popupIsVisible = false }
    fun onHistoryItemTapped(urlOrId: String) { urlEntryText = urlOrId }

    var formatPickerSelectedIndex by mutableIntStateOf(0)

    // ─── Load metadata ───
    fun onLoadClicked() {
        viewModelScope.launch {
            loadVideoMetadata()
        }
    }

    private suspend fun loadVideoMetadata() = withContext(Dispatchers.IO) {
        settings.isDownloadRunning = true
        withContext(Dispatchers.Main) {
            loadButtonIsEnabled = false
            statusLabelIsVisible = false
            downloadIndicatorIsVisible = true
            statusLabelIsVisible = true
            statusLabelText = AnnotatedString(context.getString(R.string.sl_retrieving_metadata))
        }

        cleanedUrl = StringUtils.cleanUrl(urlEntryText)

        if (cleanedUrl.isBlank()) {
            withContext(Dispatchers.Main) {
                showPopup(context.getString(R.string.pt_no_url), context.getString(R.string.pm_no_url), 2)
                applyQuickDownloadState()
            }
            settings.isDownloadRunning = false
            return@withContext
        }

        try {
            val streamInfo = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$cleanedUrl"
            )

            streamInfo.audioStreams.forEach { stream ->
                println("=== AUDIO STREAM: format=${stream.format?.name}, averageBitrate=${stream.averageBitrate}")
            }

            streamInfo.videoStreams.forEach { stream ->
                println("=== MUXED STREAM: format=${stream.format?.name}, height=${stream.height}")
            }

            streamInfo.videoOnlyStreams.forEach { stream ->
                println("=== VIDEO STREAM: format=${stream.format?.name}, height=${stream.height}")
            }

            // Build audio options
            var audioStreams = streamInfo.audioStreams
                .filter { it.format?.name?.contains("m4a", ignoreCase = true) == true && it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
                .sortedByDescending { it.averageBitrate }
                .distinctBy { it.averageBitrate }

            if (audioStreams.isEmpty()) {
                audioStreams = streamInfo.audioStreams
                    .filter { it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
                    .sortedByDescending { it.averageBitrate }
                    .distinctBy { it.averageBitrate }
            }

            audioOptions = audioStreams.associateBy { it.averageBitrate.toDouble() }

            // Build video options
            val use4K = settings.use4K
            val videoStreams = streamInfo.videoOnlyStreams
                .filter { stream ->
                    if (use4K) true
                    else stream.height in 1..1080 && stream.format?.name?.contains("mpeg-4", ignoreCase = true) == true
                }
                .sortedByDescending { it.height }
                .distinctBy { it.height }

            videoOptions = videoStreams.associateBy { it.height }

            withContext(Dispatchers.Main) {
                loadButtonIsVisible = false
                loadButtonIsEnabled = true
                downloadIndicatorIsVisible = false
                statusLabelIsVisible = false
                downloadOptionsIsVisible = true
                qualityPickerIsVisible = true
                formatPickerSelectedIndex = 0
                qualityPickerItemsSource = audioOptions.entries.map { QualityOption(it.key, "${it.key.toInt()} kbps") }
                qualityPickerSelectedIndex = 0
                downloadButtonIsVisible = true
                cancelButtonIsVisible = false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                applyQuickDownloadState()
                when {
                    e.message?.contains("403") == true || e.message?.contains("404") == true -> {
                        showPopup(context.getString(R.string.pt_unavailable), context.getString(R.string.pm_unavailable), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_unavailable))
                        }
                    }
                    e.message?.contains("ID or URL") == true -> {
                        showPopup(context.getString(R.string.pt_invalid_url), context.getString(R.string.pm_invalid_url), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_invalid_url))
                        }
                    }
                    e.message?.contains("Software caused connection abort") == true -> {
                        showPopup(context.getString(R.string.pt_network_error), context.getString(R.string.pm_network_error), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_network_error))
                        }
                    }
                    else -> {
                        showPopup(context.getString(R.string.pt_error), e.message ?: context.getString(R.string.pm_error), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_error))
                        }
                    }
                }
            }
        } finally {
            settings.isDownloadRunning = false
        }
    }

    // ─── Download ───
    fun onDownloadClicked() {
        downloadJob = viewModelScope.launch {
            startDownload()
        }
    }

    private suspend fun startDownload() = withContext(Dispatchers.IO) {
        settings.isDownloadRunning = true

        withContext(Dispatchers.Main) {
            formatPickerIsEnabled = false
            qualityPickerIsEnabled = false
            downloadButtonIsVisible = false
            cancelButtonIsVisible = true
            downloadIndicatorIsVisible = true
            statusLabelIsVisible = true
            statusLabelText = AnnotatedString(context.getString(R.string.sl_retrieving_metadata))
        }

        if (isQuick) cleanedUrl = StringUtils.cleanUrl(urlEntryText)

        if (cleanedUrl.isBlank()) {
            withContext(Dispatchers.Main) {
                applyQuickDownloadState()
                showPopup(context.getString(R.string.pt_no_url), context.getString(R.string.pm_no_url), 2)
            }
            settings.isDownloadRunning = false
            return@withContext
        }

        val m4aPath = File(context.cacheDir, "audio.m4a").absolutePath
        val mp4Path = File(context.cacheDir, "video.mp4").absolutePath
        val semiOutput = File(context.filesDir, "semi-outputVideo.mp4").absolutePath
        val semiOutputAudio = File(context.filesDir, "semi-outputAudio.mp3").absolutePath
        val imagePath = File(context.cacheDir, "thumbnail.jpg").absolutePath

        fun deleteFiles() {
            listOf(m4aPath, mp4Path, semiOutput, semiOutputAudio, imagePath)
                .forEach { if (File(it).exists()) File(it).delete() }
        }

        try {
            // Start foreground service
            DownloadNotificationService.setProgressType(false)
            withContext(Dispatchers.Main) {
                context.startForegroundService(
                    Intent(context, DownloadNotificationService::class.java)
                )
            }

            val streamInfo = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$cleanedUrl"
            )

            if (streamInfo.duration <= 0) {
                withContext(Dispatchers.Main) {
                    applyQuickDownloadState()
                    showPopup(context.getString(R.string.pt_live), context.getString(R.string.pm_live))
                }
                stopService()
                settings.isDownloadRunning = false
                DownloadNotificationService.setProgressType(false)
                return@withContext
            }

            var author = StringUtils.cleanAuthor(streamInfo.uploaderName ?: "")
            val (cleanedTitle, cleanedAuthor) = StringUtils.cleanTitle(streamInfo.name ?: "YouTube_Video", author)
            author = cleanedAuthor
            var title = cleanedTitle
            val unalteredTitle = streamInfo.name?.trim() ?: title

            // Update history
            withContext(Dispatchers.Main) {
                val existing = settings.downloadHistory.find { it.urlOrId == cleanedUrl }
                val newItem = SettingsSave.HistoryItem(unalteredTitle, cleanedUrl)
                val updated = settings.downloadHistory.toMutableList()
                updated.remove(existing)
                updated.add(0, newItem)
                settings.downloadHistory = updated
                (settings as? SettingsSave)?.saveExtraData()
            }

            // Download thumbnail
            val thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url
            var hasThumbnail = false
            if (thumbnailUrl != null) {
                val bytes = URL(thumbnailUrl).readBytes()
                val ext = detectImageExtension(bytes)
                val tempThumbnail = File(context.cacheDir, "tempThumbnail.$ext").absolutePath
                File(tempThumbnail).writeBytes(bytes)

                val ffmpegComd = "-y -i \"$tempThumbnail\" -frames:v 1 \"$imagePath\""
                Log.d("FFmpegCommand", ffmpegComd)
                val result = runFFmpeg(ffmpegComd)

                hasThumbnail = result && File(imagePath).exists()

                if (File(tempThumbnail).exists()) File(tempThumbnail).delete()
            }

            // Show title/author dialog and wait for result
            val (confirmedTitle, confirmedAuthor) = withContext(Dispatchers.Main) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    dialogTitle = title
                    dialogAuthor = author
                    showTitleAuthorDialog = true
                    pendingOnConfirm = { t, a ->
                        showTitleAuthorDialog = false
                        @Suppress("DEPRECATION")
                        cont.resume(Pair(t, a)) { }
                    }
                    cont.invokeOnCancellation {
                        showTitleAuthorDialog = false
                    }
                }
            }
            title = StringUtils.cleanTitle(confirmedTitle, confirmedAuthor).first
            author = StringUtils.cleanAuthor(confirmedAuthor)

            withContext(Dispatchers.Main) {
                statusLabelText = buildAnnotatedString {
                    append(context.getString(R.string.sl_download))
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextSecondary)) {
                        append(title)
                    }
                }
                downloadIndicatorIsVisible = false
                dwnldProgressIsVisible = true
                DownloadNotificationService.setProgressType(true)
                DownloadNotificationService.startTimer()
            }

            val selectedFormat = formatPickerSelectedIndex
            var lastNotifiedPercent = -1
            var lastNotifiedTime = 0L

            if (selectedFormat == 0) {
                // ─── MP3 ───
                fun getAudioStream(): AudioStream {
                    val audioStream = if (isQuick) {
                        streamInfo.audioStreams
                            .filter { it.format?.name?.contains("m4a", ignoreCase = true) == true && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
                            .maxByOrNull { it.averageBitrate }
                            ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
                    } else {
                        val selectedBitrate = audioOptions.entries.elementAtOrNull(qualityPickerSelectedIndex)?.key
                        if (selectedBitrate != null) {
                            streamInfo.audioStreams
                                .filter { it.format?.name?.contains("m4a", ignoreCase = true) == true && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
                                .minByOrNull { abs(it.averageBitrate - selectedBitrate.toInt()) }
                        } else null
                    } ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }!!

                    return audioStream
                }

                val audioStream = getAudioStream()

                downloadStream(audioStream.content, m4aPath,
                    onProgress = { progress ->
                        downloadProgress = progress
                        val percent = (progress * 100).toInt()
                        val now = System.currentTimeMillis()
                        if (percent != lastNotifiedPercent && now - lastNotifiedTime >= 500) {
                            lastNotifiedPercent = percent
                            lastNotifiedTime = now
                            DownloadNotificationService.updateProgress(
                                context,
                                percent
                            )
                        }
                    },
                    urlRefresher = {
                        getAudioStream().content
                    }
                )

                withContext(Dispatchers.Main) {
                    dwnldProgressIsVisible = false
                    DownloadNotificationService.setProgressType(false)
                    DownloadNotificationService.updateProgress(context, 0, finale = true)
                    downloadIndicatorIsVisible = true
                    statusLabelText = AnnotatedString(context.getString(R.string.sl_add_metadata))
                }

                //FileSaver.saveM4a(context, "$title.m4a", m4aPath, settings.fileUri.ifBlank { null })  YTmI9rP5YUw

                val ffmpegCmd = buildString {
                    append("-y -i \"$m4aPath\" ")
                    if (hasThumbnail) {
                        append("-i \"$imagePath\" ")
                    }
                    append("-map 0:a ")
                    if (hasThumbnail) {
                        append("-map 1:v ")
                    }
                    append("-c:a libmp3lame -b:a 128k -ac 2 -af anull ")
                    if (hasThumbnail) {
                        append("-c:v mjpeg -disposition:v attached_pic ")
                        append("-metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover\" ")
                    }
                    append("-metadata title=\"$title\" -metadata artist=\"$author\" ")
                    append("-threads 1 \"$semiOutputAudio\"")
                }

                Log.d("FFmpegCommand", ffmpegCmd)
                val ffmpegResult = runFFmpeg(ffmpegCmd)

                if (ffmpegResult) {
                    val savedUri = FileSaver.saveAudio(context, "$title.mp3", semiOutputAudio, settings.fileUri.ifBlank { null })
                    if (settings.notifyOnFinish) {
                        DownloadNotificationService.showFinishNotification(context, "$title.mp3")
                    }
                    withContext(Dispatchers.Main) {
                        applyQuickDownloadState()
                        showPopup(context.getString(R.string.pt_finished), context.getString(R.string.pm_finished), 1)
                        urlEntryText = ""
                        if (savedUri != null && MusicAxsClient.isMusicAxsInstalled(context) && settings.addToMusicAxs) {
                            lastDownloadedSongUri = savedUri
                            showPlaylistPicker = true
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        applyQuickDownloadState()
                        showPopup(context.getString(R.string.pt_failed), context.getString(R.string.pm_failed), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_failed))
                        }
                    }
                }

            } else {
                // ─── MP4 ───
                fun getAudioStream(): AudioStream {
                    return streamInfo.audioStreams.filter{ it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null }.maxByOrNull{ it.averageBitrate } ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }!!
                }

                fun getVideoStream(): VideoStream {
                    val videoStream = if (isQuick) {
                        if (settings.use4K)
                            streamInfo.videoOnlyStreams.maxByOrNull { it.height }
                        else
                            streamInfo.videoOnlyStreams
                                .filter { it.height <= 1080 &&
                                        it.format?.name?.contains("mpeg-4", ignoreCase = true) == true }
                                .maxByOrNull { it.height }
                    } else {
                        val selectedHeight = videoOptions.entries.elementAtOrNull(qualityPickerSelectedIndex)?.key
                        if (selectedHeight != null) {
                            streamInfo.videoOnlyStreams
                                .filter { it.format?.name?.contains("mpeg-4", ignoreCase = true) == true || settings.use4K }
                                .firstOrNull { it.height == selectedHeight }
                        } else null
                    } ?: streamInfo.videoOnlyStreams.maxByOrNull { it.height }!!

                    return videoStream
                }

                val audioStream = getAudioStream()
                val videoStream = getVideoStream()

                val isMoreThan1080p = videoStream.height > 1080

                // Download audio and video in parallel
                withContext(Dispatchers.IO) {
                    val audioJob = launch { downloadStream(audioStream.content, m4aPath, onProgress = {}, urlRefresher = { getAudioStream().content }) }
                    val videoJob = launch {
                        downloadStream(videoStream.content, mp4Path,
                            onProgress = { progress ->
                                downloadProgress = progress
                                val percent = (progress * 100).toInt()
                                val now = System.currentTimeMillis()
                                if (percent != lastNotifiedPercent && now - lastNotifiedTime >= 500) {
                                    lastNotifiedPercent = percent
                                    lastNotifiedTime = now
                                    DownloadNotificationService.updateProgress(
                                        context,
                                        percent
                                    )
                                }
                            },
                            urlRefresher = {
                                getVideoStream().content
                            }
                        )
                    }
                    audioJob.join()
                    videoJob.join()
                }

                withContext(Dispatchers.Main) {
                    dwnldProgressIsVisible = false
                    DownloadNotificationService.setProgressType(false)
                    DownloadNotificationService.updateProgress(context, 0, finale = true)
                    downloadIndicatorIsVisible = true
                    statusLabelText = AnnotatedString(context.getString(R.string.sl_joining_a_and_v))
                }

                //FileSaver.saveM4a(context, "$title.m4a", m4aPath, settings.fileUri.ifBlank { null })

                val ffmpegArgs = if (settings.use4K && isMoreThan1080p) {
                    "-y -i \"$mp4Path\" -i \"$m4aPath\" -c:v libx264 -pix_fmt yuv420p -preset superfast -crf 23 " +
                            "-c:a copy -map 0:v:0 -map 1:a:0 -shortest " +
                            "-metadata title=\"$title\" -metadata artist=\"$author\" \"$semiOutput\""
                } else {
                    "-y -i \"$mp4Path\" -i \"$m4aPath\" -c:v copy -c:a copy -map 0:v:0 -map 1:a:0 -shortest " +
                            "-metadata title=\"$title\" -metadata artist=\"$author\" \"$semiOutput\""
                }

                Log.d("FFmpegCommand", ffmpegArgs)
                val ffmpegResult = runFFmpeg(ffmpegArgs)

                if (ffmpegResult) {
                    FileSaver.saveVideo(context, "$title.mp4", semiOutput, settings.fileUri.ifBlank { null })
                    if (settings.notifyOnFinish) {
                        DownloadNotificationService.showFinishNotification(context, "$title.mp4")
                    }
                    withContext(Dispatchers.Main) {
                        applyQuickDownloadState()
                        showPopup(context.getString(R.string.pt_finished), context.getString(R.string.pm_finished), 1)
                        urlEntryText = ""
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        applyQuickDownloadState()
                        showPopup(context.getString(R.string.pt_failed), context.getString(R.string.pm_failed), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_failed))
                        }
                    }
                }
            }

        } catch (_: kotlinx.coroutines.CancellationException) {
            FFmpegKit.cancel()
            deleteFiles()
            stopService()
            DownloadNotificationService.setProgressType(false)
            withContext(Dispatchers.Main) {
                applyQuickDownloadState()
                showPopup(context.getString(R.string.pt_canceled), context.getString(R.string.pm_canceled), 0)
            }
        } catch (e: Exception) {
            FFmpegKit.cancel()
            deleteFiles()
            stopService()
            DownloadNotificationService.setProgressType(false)
            withContext(Dispatchers.Main) {
                applyQuickDownloadState()
                when {
                    e.message?.contains("403") == true || e.message?.contains("404") == true -> {
                        showPopup(context.getString(R.string.pt_unavailable), context.getString(R.string.pm_unavailable), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_unavailable))
                        }
                    }
                    e.message?.contains("ID or URL") == true -> {
                        showPopup(context.getString(R.string.pt_invalid_url), context.getString(R.string.pm_invalid_url), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_invalid_url))
                        }
                    }
                    e.message?.contains("Software caused connection abort") == true || e.message?.contains("No address associated with hostname") == true -> {
                        showPopup(context.getString(R.string.pt_network_error), context.getString(R.string.pm_network_error), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_network_error))
                        }
                    }
                    else -> {
                        showPopup(context.getString(R.string.pt_error), e.message ?: context.getString(R.string.pm_error), 2)
                        if (settings.notifyOnFail) {
                            DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_error))
                        }
                    }
                }
            }
        } finally {
            deleteFiles()
            stopService()
            DownloadNotificationService.setProgressType(false)
            settings.isDownloadRunning = false
        }
    }

    // ─── Cancel ───
    fun onCancelClicked() {
        FFmpegKit.cancel()
        downloadJob?.cancel()
        applyQuickDownloadState()
        settings.isDownloadRunning = false
    }

    // ─── Dialog confirm ───
    fun onTitleAuthorConfirmed(title: String, author: String) {
        pendingOnConfirm?.invoke(title, author)
        pendingOnConfirm = null
    }

    fun onTitleAuthorDismissed() {
        showTitleAuthorDialog = false
        downloadJob?.cancel()
        applyQuickDownloadState()
    }

    // ─── Helpers ───
    fun applyQuickDownloadState() {
        val quick = settings.quickDwnld
        downloadOptionsIsVisible = true
        formatPickerIsEnabled = true
        qualityPickerIsVisible = !quick
        qualityPickerIsEnabled = true
        qualityPickerSelectedIndex = 0
        loadButtonIsVisible = !quick
        loadButtonIsEnabled = true
        downloadButtonIsVisible = quick
        cancelButtonIsVisible = false
        dwnldProgressIsVisible = false
        downloadIndicatorIsVisible = false
        statusLabelIsVisible = false
    }

    fun showPopup(title: String, message: String, type: Int = 0, buttonText: String = "OK") {
        popupBackground = when (type) {
            1 -> PopupSuccess  // success green
            2 -> PopupError  // error red
            else -> PopupDefault // default
        }
        popupTitle = title
        popupMessage = message
        popupButtonText = buttonText
        popupIsVisible = true
    }

    private fun stopService() {
        context.stopService(Intent(context, DownloadNotificationService::class.java))
    }

    private suspend fun downloadStream(
        url: String,
        outputPath: String,
        onProgress: (Float) -> Unit,
        urlRefresher: (suspend () -> String)? = null
    ) = DownloadUtils.downloadStream(url, outputPath, onProgress, urlRefresher)

    private fun detectImageExtension(bytes: ByteArray): String =
        DownloadUtils.detectImageExtension(bytes)

    private suspend fun runFFmpeg(command: String): Boolean =
        DownloadUtils.runFFmpeg(command)
}