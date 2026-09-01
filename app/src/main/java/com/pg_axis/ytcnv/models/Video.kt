package com.pg_axis.ytcnv.models

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.pg_axis.ytcnv.PopupType
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.settings.SettingsSave
import com.pg_axis.ytcnv.utils.DownloadNotificationService
import com.pg_axis.ytcnv.utils.DownloadUtils
import com.pg_axis.ytcnv.utils.FileSaver
import com.pg_axis.ytcnv.utils.StringUtils.cleanAuthor
import com.pg_axis.ytcnv.utils.StringUtils.cleanTitle
import com.pg_axis.ytcnv.utils.StringUtils.cleanUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class QualityOption(
    val key: Double,
    val displayName: String,
    val sizeBytes: Long? = null,
    val isNative: Boolean = true
)

data class AudioTrackOption(
    val bitrate: Int,
    val codecName: String,
    val sizeBytes: Long? = null
)

class InsufficientStorageException(
    val availableBytes: Long,
    val requiredBytes: Long
) : Exception("Not enough free storage space for this download.")

private data class RawSource(
    val path: String,
    val extension: String,
    val label: String,
    val isVideo: Boolean
)

private sealed class MuxTarget {
    data class Direct(val uri: Uri, val ffmpegPath: String) : MuxTarget()
    data class Local(val path: String) : MuxTarget()
}

private enum class MediaType {
    AUDIO,
    VIDEO,
    PROCESSED,
    IMAGE
}

@Keep
enum class TrackType { AUDIO, VIDEO }

@Keep
enum class TargetFormat(val trackType: TrackType, val extension: String) {
    M4A(TrackType.AUDIO, "m4a"),
    OPUS(TrackType.AUDIO, "opus"),
    MP3(TrackType.AUDIO, "mp3"),
    MP4(TrackType.VIDEO, "mp4"),
    WEBM(TrackType.VIDEO, "webm")
}

data class FormatOption(
    val format: TargetFormat,
    val isNative: Boolean,
    val context: Context
) {
    val displayName: String
        get() {
            val codecLabel = when (format) {
                TargetFormat.M4A -> "M4A"
                TargetFormat.OPUS -> "Opus"
                TargetFormat.MP3 -> "MP3"
                TargetFormat.MP4 -> "MP4"
                TargetFormat.WEBM -> "WebM"
            }
            val trackLabel = if (format.trackType == TrackType.AUDIO) context.getString(R.string.audio) else context.getString(R.string.video)
            val nativeLabel = if (isNative) context.getString(R.string.is_native) else context.getString(R.string.converted)
            return "$trackLabel: $codecLabel ($nativeLabel)"
        }
}

interface VideoCallbacks {
    fun onLoadStarted()
    fun onLoadFinished()
    fun onDownloadStarted()
    fun onDownloadInProgress()
    fun onDownloadFinished(savedUri: Uri?, isAudio: Boolean)
    fun onFailed()
    fun onCanceled()
    fun showPopup(title: String, message: String, type: PopupType)
    fun requestTitleAuthorConfirmation(title: String, author: String, onConfirm: (String, String) -> Unit)
    fun offerKeepPartialDownload(sizeBytes: Long, streamLabel: String, onChoice: (Boolean) -> Unit)
    fun offerDownloadWithoutMargin(availableBytes: Long, requiredBytesWithoutMargin: Long, onChoice: (Boolean) -> Unit)
    fun stopService()
}

class Video(
    urlOrId: String,
    private val context: Context,
    private val callbacks: VideoCallbacks
) {
    val id: String = cleanUrl(urlOrId)

    val notificationId: Int = id.hashCode()

    private val imagePath: String = getPath(MediaType.IMAGE)
    private val audioPath: String = getPath(MediaType.AUDIO)
    private val videoPath: String = getPath(MediaType.VIDEO)
    private val semiOutputPath: String get() = getPath(MediaType.PROCESSED)

    private val settings = SettingsSave.getInstance(context)

    private var cachedStreamInfo: StreamInfo? = null
    private var muxedFallbackStream: VideoStream? = null

    var formatOptions by mutableStateOf<List<FormatOption>>(emptyList())
        private set

    var qualityOptionsWithSizes by mutableStateOf<List<QualityOption>>(emptyList())
        private set
    var isFetchingQualitySizes by mutableStateOf(false)
        private set
    private val qualitySizeCache = mutableMapOf<Pair<TargetFormat, Boolean>, List<QualityOption>>()

    var audioTrackOptions by mutableStateOf<List<AudioTrackOption>>(emptyList())
        private set
    var isFetchingAudioTrackSizes by mutableStateOf(false)
        private set
    private var audioTrackSizeCache: List<AudioTrackOption>? = null

    private var outputFormat: TargetFormat = TargetFormat.MP3
    private var selectedQualityIndex: Int = 0
    private var preferredAudioBitrate: Int? = null

    private var isMp3: Boolean = false

    var downloadProgress by mutableFloatStateOf(0f)
        private set
    var statusLabelText by mutableStateOf(AnnotatedString(""))
        private set

    var elapsedSeconds by mutableLongStateOf(0L)
        private set
    var etaSeconds by mutableStateOf<Long?>(null)
        private set
    var downloadSpeedBytesPerSec by mutableLongStateOf(0L)
        private set
    var bytesDownloaded by mutableLongStateOf(0L)
        private set
    var bytesTotal by mutableLongStateOf(0L)
        private set
    var isDownloaded by mutableStateOf(false)
        private set
    var isMuxing by mutableStateOf(false)
        private set
    var muxEtaSeconds by mutableStateOf<Long?>(null)
        private set
    var muxedBytesEstimate by mutableLongStateOf(0L)
        private set
    var muxedBytesTotalEstimate by mutableLongStateOf(0L)
        private set

    private fun getPath(type: MediaType): String {
        return when (type) {
            MediaType.AUDIO -> File(context.cacheDir, "audio-$id.m4a").absolutePath
            MediaType.VIDEO -> File(context.cacheDir, "video-$id.mp4").absolutePath
            MediaType.PROCESSED -> File(context.filesDir, "semi-output-$id.${outputFormat.extension}").absolutePath
            MediaType.IMAGE -> File(context.cacheDir, "thumbnail-$id.jpg").absolutePath
        }
    }

    private fun deleteFiles() {
        listOf(audioPath, videoPath, semiOutputPath, imagePath)
            .forEach { if (File(it).exists()) File(it).delete() }
    }

    private fun matchesAudioCodec(formatName: String?, target: TargetFormat): Boolean {
        val name = formatName ?: return false
        return when (target) {
            TargetFormat.M4A -> name.contains("m4a", ignoreCase = true)
            TargetFormat.OPUS -> name.contains("opus", ignoreCase = true)
            else -> false
        }
    }

    private fun matchesVideoCodec(formatName: String?, target: TargetFormat): Boolean {
        val name = formatName ?: return false
        return when (target) {
            TargetFormat.MP4 -> name.contains("mpeg-4", ignoreCase = true) || name.contains("mp4", ignoreCase = true)
            TargetFormat.WEBM -> name.contains("webm", ignoreCase = true)
            else -> false
        }
    }

    private fun rawAudioExtension(formatName: String?): String =
        if (formatName?.contains("opus", ignoreCase = true) == true) "opus" else "m4a"

    private fun rawVideoExtension(formatName: String?): String =
        if (formatName?.contains("webm", ignoreCase = true) == true) "webm" else "mp4"

    private fun audioCodecLabel(formatName: String?): String = when {
        formatName?.contains("opus", ignoreCase = true) == true -> "Opus"
        formatName?.contains("m4a", ignoreCase = true) == true -> "M4A"
        else -> formatName ?: "Audio"
    }

    fun qualityOptionsFor(format: TargetFormat, isNative: Boolean): List<QualityOption> {
        val info = cachedStreamInfo ?: return emptyList()
        val options = when (format.trackType) {
            TrackType.AUDIO -> {
                val pool = info.audioStreams.filter {
                    it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) &&
                            if (isNative) matchesAudioCodec(it.format?.name, format)
                            else matchesAudioCodec(it.format?.name, TargetFormat.M4A) || matchesAudioCodec(it.format?.name, TargetFormat.OPUS)
                }
                pool.map { it.averageBitrate }.distinct().sortedDescending()
                    .map { QualityOption(it.toDouble(), "$it kbps", isNative = isNative) }
            }
            TrackType.VIDEO -> {
                if (format == TargetFormat.MP4) {
                    val pool = info.videoOnlyStreams.filter { settings.use4K || it.height <= 1080 }
                    pool.map { it.height }.distinct().sortedDescending().map { height ->
                        val nativeHere = pool.any { it.height == height && matchesVideoCodec(it.format?.name, TargetFormat.MP4) }
                        QualityOption(height.toDouble(), "${height}p", isNative = nativeHere)
                    }
                } else {
                    val pool = info.videoOnlyStreams.filter { stream ->
                        (settings.use4K || stream.height <= 1080) && matchesVideoCodec(stream.format?.name, format)
                    }
                    pool.map { it.height }.distinct().sortedDescending()
                        .map { QualityOption(it.toDouble(), "${it}p", isNative = true) }
                }
            }
        }
        return if (options.isEmpty() && muxedFallbackStream != null) listOf(QualityOption(0.0, "Default")) else options
    }

    private fun findStreamUrlForQuality(info: StreamInfo, format: TargetFormat, isNative: Boolean, key: Double): String? {
        return when (format.trackType) {
            TrackType.AUDIO -> {
                val pool = info.audioStreams.filter {
                    it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) &&
                            if (isNative) matchesAudioCodec(it.format?.name, format)
                            else matchesAudioCodec(it.format?.name, TargetFormat.M4A) || matchesAudioCodec(it.format?.name, TargetFormat.OPUS)
                }
                pool.firstOrNull { it.averageBitrate.toDouble() == key }?.content ?: muxedFallbackStream?.content
            }
            TrackType.VIDEO -> {
                if (format == TargetFormat.MP4) {
                    val atHeight = info.videoOnlyStreams.filter {
                        (settings.use4K || it.height <= 1080) && it.height.toDouble() == key
                    }
                    atHeight.firstOrNull { matchesVideoCodec(it.format?.name, TargetFormat.MP4) }?.content
                        ?: atHeight.firstOrNull()?.content
                        ?: muxedFallbackStream?.content
                } else {
                    val pool = info.videoOnlyStreams.filter {
                        (settings.use4K || it.height <= 1080) && matchesVideoCodec(it.format?.name, format) && it.height.toDouble() == key
                    }
                    pool.firstOrNull()?.content ?: muxedFallbackStream?.content
                }
            }
        }
    }

    suspend fun loadQualitySizes(format: TargetFormat, isNative: Boolean) = withContext(Dispatchers.IO) {
        val cacheKey = format to isNative
        qualitySizeCache[cacheKey]?.let { cached ->
            withContext(Dispatchers.Main) { qualityOptionsWithSizes = cached }
            return@withContext
        }

        val baseOptions = qualityOptionsFor(format, isNative)
        withContext(Dispatchers.Main) {
            isFetchingQualitySizes = true
            qualityOptionsWithSizes = baseOptions
        }

        val info = cachedStreamInfo
        val withSizes = if (info == null) baseOptions else coroutineScope {
            baseOptions.map { option ->
                async {
                    val url = findStreamUrlForQuality(info, format, isNative, option.key)
                    option.copy(sizeBytes = url?.let { getRemoteContentLength(it) })
                }
            }.awaitAll()
        }

        qualitySizeCache[cacheKey] = withSizes
        withContext(Dispatchers.Main) {
            qualityOptionsWithSizes = withSizes
            isFetchingQualitySizes = false
        }
    }

    private fun audioTrackOptionsBase(): List<AudioTrackOption> {
        val info = cachedStreamInfo ?: return emptyList()
        return info.audioStreams
            .filter { it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
            .distinctBy { it.averageBitrate to (it.format?.name ?: "") }
            .sortedByDescending { it.averageBitrate }
            .map { AudioTrackOption(it.averageBitrate, audioCodecLabel(it.format?.name)) }
    }

    suspend fun loadAudioTrackSizes() = withContext(Dispatchers.IO) {
        audioTrackSizeCache?.let { cached ->
            withContext(Dispatchers.Main) { audioTrackOptions = cached }
            return@withContext
        }

        val base = audioTrackOptionsBase()
        withContext(Dispatchers.Main) {
            isFetchingAudioTrackSizes = true
            audioTrackOptions = base
        }

        val info = cachedStreamInfo
        val withSizes = if (info == null) base else coroutineScope {
            base.map { option ->
                async {
                    val stream = info.audioStreams.firstOrNull {
                        it.averageBitrate == option.bitrate && audioCodecLabel(it.format?.name) == option.codecName
                    }
                    option.copy(sizeBytes = stream?.let { getRemoteContentLength(it.content) })
                }
            }.awaitAll()
        }

        audioTrackSizeCache = withSizes
        withContext(Dispatchers.Main) {
            audioTrackOptions = withSizes
            isFetchingAudioTrackSizes = false
        }
    }

    private fun buildFormatOptions(info: StreamInfo) {
        val options = mutableListOf<FormatOption>()

        val eligibleAudio = info.audioStreams.filter {
            it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null)
        }
        val hasM4a = eligibleAudio.any { matchesAudioCodec(it.format?.name, TargetFormat.M4A) }
        val hasOpus = eligibleAudio.any { matchesAudioCodec(it.format?.name, TargetFormat.OPUS) }
        if (hasM4a) options += FormatOption(TargetFormat.M4A, isNative = true, context)
        if (hasOpus) options += FormatOption(TargetFormat.OPUS, isNative = true, context)
        options += FormatOption(TargetFormat.MP3, isNative = false, context)

        val eligibleVideo = info.videoOnlyStreams.filter { settings.use4K || it.height <= 1080 }
        val hasMp4 = eligibleVideo.any { matchesVideoCodec(it.format?.name, TargetFormat.MP4) }
        val hasWebm = eligibleVideo.any { matchesVideoCodec(it.format?.name, TargetFormat.WEBM) }
        options += FormatOption(TargetFormat.MP4, isNative = hasMp4, context)
        if (hasWebm) options += FormatOption(TargetFormat.WEBM, isNative = true, context)

        formatOptions = options
    }


    private fun getRemoteContentLength(url: String): Long? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 6000
                readTimeout = 6000
            }
            connection.connect()
            val length = connection.contentLengthLong
            if (length > 0) length else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private data class StorageCheckResult(
        val hasEnoughWithMargin: Boolean,
        val hasEnoughWithoutMargin: Boolean,
        val availableBytes: Long,
        val requiredWithMargin: Long,
        val requiredWithoutMargin: Long
    )

    private fun checkStorage(estimatedStreamBytes: Long, destinationFolderUri: String?): StorageCheckResult {
        val internalAvailable = getAllocatableOrUsableBytes(context.filesDir)
        val internalRequired = estimatedStreamBytes + INTERNAL_SCRATCH_BUFFER_BYTES
        if (internalAvailable != null && internalAvailable < internalRequired) {
            return StorageCheckResult(
                hasEnoughWithMargin = false,
                hasEnoughWithoutMargin = false,
                availableBytes = internalAvailable,
                requiredWithMargin = internalRequired,
                requiredWithoutMargin = internalRequired
            )
        }

        val marginBytes = settings.storageMarginMb.coerceAtLeast(0).toLong() * 1024 * 1024
        val destinationAvailable = if (destinationFolderUri.isNullOrBlank()) {
            getAllocatableOrUsableBytes(Environment.getExternalStorageDirectory())
        } else {
            getAvailableBytesForTreeUri(destinationFolderUri)
                ?: getAllocatableOrUsableBytes(Environment.getExternalStorageDirectory())
        }
        val requiredWithMargin = estimatedStreamBytes + marginBytes
        val requiredWithoutMargin = estimatedStreamBytes
        val hasEnoughWithMargin = !(destinationAvailable != null && destinationAvailable < requiredWithMargin)
        val hasEnoughWithoutMargin = !(destinationAvailable != null && destinationAvailable < requiredWithoutMargin)

        return StorageCheckResult(
            hasEnoughWithMargin = hasEnoughWithMargin,
            hasEnoughWithoutMargin = hasEnoughWithoutMargin,
            availableBytes = destinationAvailable ?: -1L,
            requiredWithMargin = requiredWithMargin,
            requiredWithoutMargin = requiredWithoutMargin
        )
    }

    private suspend fun ensureEnoughStorage(estimatedBytes: Long, destinationFolderUri: String?) {
        val safeEstimate = estimatedBytes.coerceAtLeast(0L)
        val check = checkStorage(safeEstimate, destinationFolderUri)
        if (check.hasEnoughWithMargin) return

        if (!check.hasEnoughWithoutMargin) {
            throw InsufficientStorageException(check.availableBytes, check.requiredWithMargin)
        }

        val proceed = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                callbacks.offerDownloadWithoutMargin(check.availableBytes, check.requiredWithoutMargin) { choice ->
                    @Suppress("DEPRECATION")
                    cont.resume(choice) {}
                }
            }
        }
        if (!proceed) {
            throw kotlinx.coroutines.CancellationException("Download canceled: storage margin declined.")
        }
    }

    private fun getAllocatableOrUsableBytes(dir: File): Long? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageManager = context.getSystemService(StorageManager::class.java)
                val uuid = storageManager.getUuidForPath(dir)
                return storageManager.getAllocatableBytes(uuid)
            } catch (_: Exception) { }
        }
        return try {
            dir.usableSpace
        } catch (_: Exception) {
            null
        }
    }

    private fun getAvailableBytesForTreeUri(treeUriString: String): Long? {
        return try {
            val treeUri: Uri = treeUriString.toUri()
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootId = docId.substringBefore(':')
            val rootsUri = DocumentsContract.buildRootsUri(treeUri.authority)
            context.contentResolver.query(
                rootsUri,
                arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_AVAILABLE_BYTES),
                null, null, null
            )?.use { cursor ->
                val rootIdIdx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_ROOT_ID)
                val availIdx = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
                if (rootIdIdx < 0 || availIdx < 0) return@use null
                while (cursor.moveToNext()) {
                    if (cursor.getString(rootIdIdx) == rootId) {
                        return@use cursor.getLong(availIdx)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }


    private fun prepareMuxTarget(fileName: String, folderUri: String?): MuxTarget {
        if (!folderUri.isNullOrBlank()) {
            val uri = FileSaver.createFileInChosenFolder(context, fileName, folderUri)
            if (uri != null) {
                val safParam = try {
                    FFmpegKitConfig.getSafParameter(context, uri, "rw")
                } catch (_: Exception) {
                    null
                }
                if (!safParam.isNullOrBlank()) {
                    return MuxTarget.Direct(uri, safParam)
                }
                try { DocumentFile.fromSingleUri(context, uri)?.delete() } catch (_: Exception) { }
            }
        }
        return MuxTarget.Local(semiOutputPath)
    }


    private suspend fun offerToKeepPartialFile(filePath: String, extension: String, title: String, streamLabel: String, isVideo: Boolean) {
        val file = File(filePath)
        if (!file.exists() || file.length() < MIN_SALVAGEABLE_BYTES) return

        val keep = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                callbacks.offerKeepPartialDownload(file.length(), streamLabel) { choice ->
                    @Suppress("DEPRECATION")
                    cont.resume(choice) {}
                }
            }
        }
        if (!keep) return

        val fileName = "$title ($streamLabel, incomplete).$extension"
        if (isVideo) {
            FileSaver.saveVideo(context, fileName, filePath, settings.fileVidUri.ifBlank { null })
        } else {
            FileSaver.saveAudio(context, fileName, filePath, settings.fileUri.ifBlank { null })
        }
    }

    suspend fun loadOptions() = withContext(Dispatchers.IO) {
        settings.isDownloadRunning = true
        withContext(Dispatchers.Main) {
            callbacks.onLoadStarted()
            statusLabelText = AnnotatedString(context.getString(R.string.sl_retrieving_metadata))
        }

        if (id.isBlank()) {
            withContext(Dispatchers.Main) {
                callbacks.showPopup(context.getString(R.string.pt_no_url), context.getString(R.string.pm_no_url), PopupType.ERROR)
                callbacks.onFailed()
            }
            settings.isDownloadRunning = false
            return@withContext
        }

        try {
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
            cachedStreamInfo = streamInfo

            streamInfo.audioStreams.forEach { stream ->
                Log.d("Video", "=== AUDIO STREAM: format=${stream.format?.name}, averageBitrate=${stream.averageBitrate}")
            }
            streamInfo.videoStreams.forEach { stream ->
                Log.d("Video", "=== MUXED STREAM: format=${stream.format?.name}, height=${stream.height}")
            }
            streamInfo.videoOnlyStreams.forEach { stream ->
                Log.d("Video", "=== VIDEO STREAM: format=${stream.format?.name}, height=${stream.height}")
            }

            val hasEligibleAudio = streamInfo.audioStreams.any {
                it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null)
            }
            val hasEligibleVideo = streamInfo.videoOnlyStreams.any { settings.use4K || it.height <= 1080 }

            muxedFallbackStream = if (!hasEligibleAudio || !hasEligibleVideo) {
                if (settings.muxedFallback) {
                    streamInfo.videoStreams.maxByOrNull { it.height }
                        ?: throw Exception("No streams available for this video.")
                } else {
                    throw Exception("No streams available for this video.")
                }
            } else null

            buildFormatOptions(streamInfo)
            loadAudioTrackSizes()

            withContext(Dispatchers.Main) {
                selectedQualityIndex = 0
                callbacks.onLoadFinished()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                reportError(e)
                callbacks.onFailed()
            }
        } finally {
            settings.isDownloadRunning = false
        }
    }


    suspend fun download(target: TargetFormat, isNative: Boolean, qualityIndex: Int, preferredAudioBitrate: Int? = null) = withContext(Dispatchers.IO) {
        outputFormat = target
        isMp3 = target.trackType == TrackType.AUDIO
        selectedQualityIndex = qualityIndex
        this@Video.preferredAudioBitrate = preferredAudioBitrate
        settings.isDownloadRunning = true

        withContext(Dispatchers.Main) {
            callbacks.onDownloadStarted()
            downloadProgress = 0f
            elapsedSeconds = 0L
            etaSeconds = null
            downloadSpeedBytesPerSec = 0L
            bytesDownloaded = 0L
            bytesTotal = 0L
            isMuxing = false
            muxEtaSeconds = null
            muxedBytesEstimate = 0L
            muxedBytesTotalEstimate = 0L
            statusLabelText = AnnotatedString(context.getString(R.string.sl_retrieving_metadata))
        }

        if (id.isBlank()) {
            withContext(Dispatchers.Main) {
                callbacks.showPopup(context.getString(R.string.pt_no_url), context.getString(R.string.pm_no_url), PopupType.ERROR)
                callbacks.onFailed()
            }
            settings.isDownloadRunning = false
            return@withContext
        }

        try {
            val streamInfo = cachedStreamInfo
                ?: StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id").also { cachedStreamInfo = it }

            if (streamInfo.duration <= 0) {
                withContext(Dispatchers.Main) {
                    callbacks.showPopup(context.getString(R.string.pt_live), context.getString(R.string.pm_live), PopupType.MESSAGE)
                    callbacks.onFailed()
                }
                callbacks.stopService()
                settings.isDownloadRunning = false
                DownloadNotificationService.setProgressType(false)
                return@withContext
            }

            DownloadNotificationService.setProgressType(false)
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(
                    context,
                    DownloadNotificationService.startIntent(context, notificationId)
                )
            }

            var author = cleanAuthor(streamInfo.uploaderName ?: "")
            val (cleanedTitle, cleanedAuthor) = cleanTitle(streamInfo.name ?: "YouTube_Video", author)
            author = cleanedAuthor
            var title = cleanedTitle
            val unalteredTitle = streamInfo.name?.trim() ?: title

            // Download thumbnail
            val thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url
            var hasThumbnail = false
            if (thumbnailUrl != null) {
                val bytes = URL(thumbnailUrl).readBytes()
                val ext = DownloadUtils.detectImageExtension(bytes)
                val tempThumbnail = File(context.cacheDir, "tempThumbnail-$id.$ext").absolutePath
                File(tempThumbnail).writeBytes(bytes)
                if (ext == "jpg") {
                    File(tempThumbnail).renameTo(File(imagePath))
                    hasThumbnail = File(imagePath).exists()
                } else {
                    val ffmpegComd = "-y -i \"$tempThumbnail\" -frames:v 1 \"$imagePath\""
                    val result = DownloadUtils.runFFmpeg(ffmpegComd, 0) { }
                    hasThumbnail = result && File(imagePath).exists()
                }
                if (File(tempThumbnail).exists()) File(tempThumbnail).delete()
            }
            if (target == TargetFormat.OPUS || target == TargetFormat.WEBM) hasThumbnail = false

            // Confirm title/author with the user
            val (confirmedTitle, confirmedAuthor) = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    callbacks.requestTitleAuthorConfirmation(title, author) { t, a ->
                        @Suppress("DEPRECATION")
                        cont.resume(Pair(t, a)) { }
                    }
                }
            }
            title = if (confirmedTitle.filter { it !in "/\\:*?\"<>|" } == confirmedTitle) confirmedTitle
            else cleanTitle(confirmedTitle, confirmedAuthor).first

            // Update history (pending)
            withContext(Dispatchers.Main) {
                val existing = settings.downloadHistory.find { it.urlOrId == id }
                val newItem = SettingsSave.HistoryItem(
                    title = unalteredTitle,
                    metadataTitle = confirmedTitle,
                    metadataAuthor = confirmedAuthor,
                    isMp3 = isMp3,
                    urlOrId = id,
                    downloaded = false,
                    uri = ""
                )
                val updated = settings.downloadHistory.toMutableList()
                updated.remove(existing)
                updated.add(0, newItem)
                settings.downloadHistory = updated

                statusLabelText = buildAnnotatedString {
                    append(context.getString(R.string.sl_download))
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(confirmedTitle) }
                }
                DownloadNotificationService.setProgressType(true)
                DownloadNotificationService.startTimer()
                callbacks.onDownloadInProgress()
            }

            var lastNotifiedTime = 0L
            var lastUiUpdateTime = 0L
            var phaseStartTimeMs = System.currentTimeMillis()
            fun beginPhaseTimer() {
                phaseStartTimeMs = System.currentTimeMillis()
                lastUiUpdateTime = 0L
            }
            fun onFfmpegOrDownloadProgress(progress: Float, ffmpeg: Boolean) {
                val nowMs = System.currentTimeMillis()
                val isFinal = progress >= 1f
                val dueForUiUpdate = isFinal || nowMs - lastUiUpdateTime >= UI_UPDATE_INTERVAL_MS

                if (dueForUiUpdate) {
                    lastUiUpdateTime = nowMs
                    downloadProgress = progress
                    elapsedSeconds = (nowMs - phaseStartTimeMs) / 1000

                    if (!ffmpeg && bytesTotal > 0) {
                        val downloaded = (progress * bytesTotal).toLong().coerceIn(0L, bytesTotal)
                        bytesDownloaded = downloaded
                        val elapsedSec = elapsedSeconds.coerceAtLeast(1L)
                        downloadSpeedBytesPerSec = downloaded / elapsedSec
                        etaSeconds = if (downloadSpeedBytesPerSec > 0) (bytesTotal - downloaded) / downloadSpeedBytesPerSec else null
                    } else if (ffmpeg) {
                        downloadSpeedBytesPerSec = 0L
                        etaSeconds = null
                        val elapsedSec = elapsedSeconds.coerceAtLeast(1L)
                        muxEtaSeconds = if (progress > 0.01f) ((elapsedSec / progress) * (1f - progress)).toLong() else null
                        if (muxedBytesTotalEstimate > 0) {
                            muxedBytesEstimate = (progress * muxedBytesTotalEstimate).toLong().coerceIn(0L, muxedBytesTotalEstimate)
                        }
                    }
                }

                val percent = (progress * 100).coerceAtMost(100f)
                if (nowMs - lastNotifiedTime >= 500) {
                    lastNotifiedTime = nowMs
                    DownloadNotificationService.updateProgress(context, percent, notificationId, ffmpeg)
                }
            }
            beginPhaseTimer()

            if (target.trackType == TrackType.AUDIO) {
                downloadAudio(streamInfo, target, isNative, confirmedTitle, confirmedAuthor, title, unalteredTitle, hasThumbnail, ::beginPhaseTimer, ::onFfmpegOrDownloadProgress)
            } else {
                downloadVideo(streamInfo, target, isNative, confirmedTitle, confirmedAuthor, title, unalteredTitle, hasThumbnail, ::beginPhaseTimer, ::onFfmpegOrDownloadProgress)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            FFmpegKit.cancel()
            deleteFiles()
            callbacks.stopService()
            DownloadNotificationService.setProgressType(false)
            withContext(Dispatchers.Main) {
                callbacks.showPopup(context.getString(R.string.pt_canceled), context.getString(R.string.pm_canceled), PopupType.MESSAGE)
                callbacks.onCanceled()
            }
        } catch (e: Exception) {
            FFmpegKit.cancel()
            deleteFiles()
            callbacks.stopService()
            DownloadNotificationService.setProgressType(false)
            withContext(Dispatchers.Main) {
                reportError(e)
                callbacks.onFailed()
            }
        } finally {
            deleteFiles()
            DownloadNotificationService.setProgressType(false)
            callbacks.stopService()
            settings.isDownloadRunning = false
        }
    }

    private fun buildAudioFfmpegCommand(
        inputPath: String, outputPath: String, title: String, author: String,
        target: TargetFormat, reencode: Boolean, hasThumbnail: Boolean
    ): String = buildString {
        append("-y -i \"$inputPath\" ")
        if (hasThumbnail) append("-i \"$imagePath\" ")
        append("-map 0:a ")
        if (hasThumbnail) append("-map 1:v ")
        if (reencode) {
            append("-c:a libmp3lame -b:a 128k -ac 2 -af anull ")
        } else {
            append("-c:a copy ")
        }
        if (hasThumbnail) {
            append("-c:v mjpeg -disposition:v attached_pic ")
            append("-metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover\" ")
        }
        append("-metadata title=\"$title\" -metadata artist=\"$author\" ")
        when (target) {
            TargetFormat.M4A -> append("-f mp4 ")
            TargetFormat.MP3 -> append("-f mp3 ")
            TargetFormat.OPUS -> append("-f opus ")
            else -> { }
        }
        append("-threads 1 \"$outputPath\"")
    }

    private fun buildVideoFfmpegCommand(
        videoInput: String, audioInput: String, outputPath: String,
        title: String, author: String, target: TargetFormat, reencode: Boolean, hasThumbnail: Boolean
    ): String {
        val muxerFlag = if (target == TargetFormat.WEBM) "-f webm " else "-f mp4 "
        return buildString {
            append("-y -i \"$videoInput\" -i \"$audioInput\" ")
            if (hasThumbnail) append("-i \"$imagePath\" ")
            append("-map 0:v:0 -map 1:a:0 ")
            if (hasThumbnail) append("-map 2:v ")
            if (reencode) {
                append("-c:v:0 libx264 -pix_fmt yuv420p -preset superfast -crf 23 -c:a:0 copy ")
            } else {
                append("-c:v:0 copy -c:a:0 copy ")
            }
            if (hasThumbnail) {
                append("-c:v:1 mjpeg -disposition:v:1 attached_pic ")
                append("-metadata:s:v:1 title=\"Album cover\" -metadata:s:v:1 comment=\"Cover\" ")
            }
            append("-shortest ")
            append("-metadata title=\"$title\" -metadata artist=\"$author\" ")
            append(muxerFlag)
            append("\"$outputPath\"")
        }
    }

    private suspend fun downloadAudio(
        streamInfo: StreamInfo,
        target: TargetFormat,
        isNative: Boolean,
        confirmedTitle: String,
        confirmedAuthor: String,
        title: String,
        unalteredTitle: String,
        hasThumbnail: Boolean,
        beginPhaseTimer: () -> Unit,
        onProgress: (Float, Boolean) -> Unit
    ) {
        fun pool() = streamInfo.audioStreams.filter {
            it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) &&
                    if (isNative) matchesAudioCodec(it.format?.name, target)
                    else matchesAudioCodec(it.format?.name, TargetFormat.M4A) || matchesAudioCodec(it.format?.name, TargetFormat.OPUS)
        }
        fun getAudioStream(): AudioStream? {
            val candidates = pool()
            val chosen = if (settings.quickDwnld) {
                candidates.maxByOrNull { it.averageBitrate }
            } else {
                val selectedBitrate = qualityOptionsFor(target, isNative).getOrNull(selectedQualityIndex)?.key?.toInt()
                if (selectedBitrate != null) candidates.minByOrNull { abs(it.averageBitrate - selectedBitrate) }
                else candidates.maxByOrNull { it.averageBitrate }
            }
            return chosen ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
        }

        val audioStream = getAudioStream()
        val muxed = if (settings.muxedFallback) muxedFallbackStream ?: streamInfo.videoStreams.maxByOrNull { it.height } else null
        val inputForFFmpeg: String
        val reencode = !isNative

        val sourceUrl = audioStream?.content ?: muxed?.content
        if (sourceUrl != null) {
            val estimatedBytes = getRemoteContentLength(sourceUrl) ?: 0L
            ensureEnoughStorage(estimatedBytes, settings.fileUri)
            withContext(Dispatchers.Main) { bytesTotal = estimatedBytes }
        }

        val rawPath: String
        val rawExtension: String
        val rawIsVideo: Boolean
        val rawLabel: String

        if (audioStream != null) {
            inputForFFmpeg = audioPath
            rawPath = audioPath
            rawExtension = rawAudioExtension(audioStream.format?.name)
            rawIsVideo = false
            rawLabel = "audio"
            try {
                DownloadUtils.downloadStream(audioStream.content, audioPath,
                    onProgress = { onProgress(it, false) },
                    urlRefresher = { getAudioStream()?.content ?: audioStream.content }
                )
            } catch (e: Exception) {
                offerToKeepPartialFile(rawPath, rawExtension, title, rawLabel, rawIsVideo)
                throw e
            }
        } else if (muxed != null) {
            inputForFFmpeg = videoPath
            rawPath = videoPath
            rawExtension = rawVideoExtension(muxed.format?.name)
            rawIsVideo = true
            rawLabel = "audio+video"
            try {
                DownloadUtils.downloadStream(muxed.content, videoPath,
                    onProgress = { onProgress(it, false) },
                    urlRefresher = {
                        StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
                            .videoStreams.maxByOrNull { it.height }?.content ?: muxed.content
                    }
                )
            } catch (e: Exception) {
                offerToKeepPartialFile(rawPath, rawExtension, title, rawLabel, rawIsVideo)
                throw e
            }
        } else {
            throw Exception("No audio streams available for this video.")
        }

        withContext(Dispatchers.Main) {
            DownloadNotificationService.updateProgress(context, 0f, notificationId, finale = true)
            DownloadNotificationService.startTimer()
            statusLabelText = AnnotatedString(context.getString(R.string.sl_add_metadata))
        }
        beginPhaseTimer()

        val fileName = "$title.${target.extension}"
        val muxTarget = prepareMuxTarget(fileName, settings.fileUri)
        val ffmpegOutputPath = when (muxTarget) {
            is MuxTarget.Direct -> muxTarget.ffmpegPath
            is MuxTarget.Local -> muxTarget.path
        }

        val ffmpegCmd = buildAudioFfmpegCommand(inputForFFmpeg, ffmpegOutputPath, confirmedTitle, confirmedAuthor, target, reencode, hasThumbnail)
        withContext(Dispatchers.Main) {
            isMuxing = true
            muxedBytesTotalEstimate = bytesTotal
            muxedBytesEstimate = 0L
            muxEtaSeconds = null
        }
        val ffmpegResult = DownloadUtils.runFFmpeg(ffmpegCmd, streamInfo.duration) { onProgress(it, true) }
        withContext(Dispatchers.Main) { isMuxing = false }

        listOf(audioPath, videoPath, imagePath).forEach { if (File(it).exists()) File(it).delete() }

        if (ffmpegResult) {
            val savedUri: Uri? = when (muxTarget) {
                is MuxTarget.Direct -> muxTarget.uri
                is MuxTarget.Local -> {
                    val uri = FileSaver.saveAudio(context, fileName, muxTarget.path, settings.fileUri.ifBlank { null })
                    if (File(muxTarget.path).exists()) File(muxTarget.path).delete()
                    uri
                }
            }
            if (settings.notifyOnFinish) {
                DownloadNotificationService.showFinishNotification(context, fileName, notificationId)
            }
            withContext(Dispatchers.Main) {
                if (savedUri != null) {
                    val updated = settings.downloadHistory.toMutableList()
                    val existingIndex = updated.indexOfFirst { it.urlOrId == id }
                    if (existingIndex != -1) {
                        updated[existingIndex] = updated[existingIndex].copy(downloaded = true, uri = savedUri.toString())
                    } else {
                        updated.add(0, SettingsSave.HistoryItem(
                            title = unalteredTitle, metadataTitle = confirmedTitle, metadataAuthor = confirmedAuthor,
                            isMp3 = isMp3, urlOrId = id, downloaded = true, uri = savedUri.toString()
                        ))
                    }
                    settings.downloadHistory = updated
                }
                isDownloaded = true
                callbacks.showPopup(context.getString(R.string.pt_finished), context.getString(R.string.pm_finished), PopupType.SUCCESS)
                callbacks.onDownloadFinished(savedUri, true)
            }
        } else {
            if (muxTarget is MuxTarget.Direct) {
                try { DocumentFile.fromSingleUri(context, muxTarget.uri)?.delete() } catch (_: Exception) { }
            }
            offerToKeepPartialFile(rawPath, rawExtension, title, rawLabel, rawIsVideo)
            withContext(Dispatchers.Main) {
                callbacks.showPopup(context.getString(R.string.pt_failed), context.getString(R.string.pm_failed), PopupType.ERROR)
                callbacks.onFailed()
            }
            if (settings.notifyOnFail) {
                DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_failed), notificationId)
            }
        }
    }

    private suspend fun downloadVideo(
        streamInfo: StreamInfo,
        target: TargetFormat,
        isNative: Boolean,
        confirmedTitle: String,
        confirmedAuthor: String,
        title: String,
        unalteredTitle: String,
        hasThumbnail: Boolean,
        beginPhaseTimer: () -> Unit,
        onProgress: (Float, Boolean) -> Unit
    ) {
        fun getAudioStream(): AudioStream? {
            if (preferredAudioBitrate != null) {
                streamInfo.audioStreams
                    .filter { it.averageBitrate > 0 && (it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null) }
                    .firstOrNull { it.averageBitrate == preferredAudioBitrate }
                    ?.let { return it }
            }
            return streamInfo.audioStreams
                .filter { it.audioTrackType == AudioTrackType.ORIGINAL || it.audioTrackType == null }
                .maxByOrNull { it.averageBitrate }
                ?: streamInfo.audioStreams.maxByOrNull { it.averageBitrate }
        }

        fun pool() = streamInfo.videoOnlyStreams.filter { stream ->
            (settings.use4K || stream.height <= 1080) &&
                    if (target == TargetFormat.MP4) true
                    else if (isNative) matchesVideoCodec(stream.format?.name, target) else true
        }
        fun pickBest(candidates: List<VideoStream>): VideoStream? {
            val maxHeight = candidates.maxOfOrNull { it.height } ?: return null
            val atMaxHeight = candidates.filter { it.height == maxHeight }
            return atMaxHeight.firstOrNull { matchesVideoCodec(it.format?.name, target) } ?: atMaxHeight.firstOrNull()
        }
        fun pickAtHeight(candidates: List<VideoStream>, height: Int): VideoStream? {
            val atHeight = candidates.filter { it.height == height }
            return atHeight.firstOrNull { matchesVideoCodec(it.format?.name, target) } ?: atHeight.firstOrNull()
        }
        fun getVideoStream(): VideoStream? {
            val candidates = pool()
            val chosen = if (settings.quickDwnld) {
                pickBest(candidates)
            } else {
                val selectedHeight = qualityOptionsFor(target, isNative).getOrNull(selectedQualityIndex)?.key?.toInt()
                if (selectedHeight != null) pickAtHeight(candidates, selectedHeight)
                else pickBest(candidates)
            }
            return chosen ?: streamInfo.videoOnlyStreams.maxByOrNull { it.height }
        }

        val videoOnlyStream = getVideoStream()
        val muxed = if (settings.muxedFallback) muxedFallbackStream ?: streamInfo.videoStreams.maxByOrNull { it.height } else null

        if (videoOnlyStream != null) {
            val audioStream = getAudioStream()

            val videoBytes = getRemoteContentLength(videoOnlyStream.content) ?: 0L
            val audioBytes = audioStream?.let { getRemoteContentLength(it.content) } ?: 0L
            val estimatedBytes = videoBytes + audioBytes
            ensureEnoughStorage(estimatedBytes, settings.fileVidUri)
            withContext(Dispatchers.Main) { bytesTotal = estimatedBytes }

            try {
                val videoBytesDone = AtomicLong(0L)
                val audioBytesDone = AtomicLong(0L)

                fun reportCombined() {
                    val combined = videoBytesDone.get() + audioBytesDone.get()
                    val combinedFraction = if (estimatedBytes > 0) {
                        (combined.toFloat() / estimatedBytes).coerceIn(0f, 1f)
                    } else 0f
                    onProgress(combinedFraction, false)
                }

                withContext(Dispatchers.IO) {
                    val audioJob = launch {
                        if (audioStream != null) {
                            DownloadUtils.downloadStream(audioStream.content, audioPath,
                                onProgress = { p ->
                                    audioBytesDone.set((p * audioBytes).toLong())
                                    reportCombined()
                                },
                                urlRefresher = { getAudioStream()?.content ?: audioStream.content })
                        } else if (muxed != null) {
                            DownloadUtils.downloadStream(muxed.content, audioPath,
                                onProgress = { p ->
                                    audioBytesDone.set((p * audioBytes).toLong())
                                    reportCombined()
                                },
                                urlRefresher = {
                                    StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
                                        .videoStreams.maxByOrNull { it.height }?.content ?: muxed.content
                                })
                        } else {
                            throw Exception("No audio streams available for this video.")
                        }
                    }
                    val videoJob = launch {
                        DownloadUtils.downloadStream(videoOnlyStream.content, videoPath,
                            onProgress = { p ->
                                videoBytesDone.set((p * videoBytes).toLong())
                                reportCombined()
                            },
                            urlRefresher = { getVideoStream()?.content ?: videoOnlyStream.content })
                    }
                    audioJob.join()
                    videoJob.join()
                }
            } catch (e: Exception) {
                offerToKeepPartialFile(videoPath, rawVideoExtension(videoOnlyStream.format?.name), title, "video", true)
                offerToKeepPartialFile(audioPath, rawAudioExtension(audioStream?.format?.name ?: muxed?.format?.name), title, "audio", false)
                throw e
            }

            val reencode = !matchesVideoCodec(videoOnlyStream.format?.name, target)

            withContext(Dispatchers.Main) {
                DownloadNotificationService.updateProgress(context, 0f, notificationId, finale = true)
                DownloadNotificationService.startTimer()
                statusLabelText = AnnotatedString(context.getString(R.string.sl_joining_a_and_v))
            }
            beginPhaseTimer()

            val fileName = "$title.${target.extension}"
            val muxTarget = prepareMuxTarget(fileName, settings.fileVidUri)
            val ffmpegOutputPath = when (muxTarget) {
                is MuxTarget.Direct -> muxTarget.ffmpegPath
                is MuxTarget.Local -> muxTarget.path
            }

            val ffmpegArgs = buildVideoFfmpegCommand(videoPath, audioPath, ffmpegOutputPath, confirmedTitle, confirmedAuthor, target, reencode, hasThumbnail)
            val rawSources = listOf(
                RawSource(videoPath, rawVideoExtension(videoOnlyStream.format?.name), "video", true),
                RawSource(audioPath, rawAudioExtension(audioStream?.format?.name ?: muxed?.format?.name), "audio", false)
            )
            finishVideo(ffmpegArgs, streamInfo, title, unalteredTitle, confirmedTitle, confirmedAuthor, rawSources, muxTarget, fileName) { onProgress(it, true) }
        } else if (muxed != null) {
            val estimatedBytes = getRemoteContentLength(muxed.content) ?: 0L
            ensureEnoughStorage(estimatedBytes, settings.fileVidUri)
            withContext(Dispatchers.Main) { bytesTotal = estimatedBytes }

            try {
                DownloadUtils.downloadStream(muxed.content, videoPath,
                    onProgress = { onProgress(it, false) },
                    urlRefresher = {
                        StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
                            .videoStreams.maxByOrNull { it.height }?.content ?: muxed.content
                    }
                )
            } catch (e: Exception) {
                offerToKeepPartialFile(videoPath, rawVideoExtension(muxed.format?.name), title, "video", true)
                throw e
            }

            withContext(Dispatchers.Main) {
                DownloadNotificationService.updateProgress(context, 0f, notificationId, finale = true)
                DownloadNotificationService.startTimer()
                statusLabelText = AnnotatedString(context.getString(R.string.sl_joining_a_and_v))
            }
            beginPhaseTimer()

            val fileName = "$title.${target.extension}"
            val muxTarget = prepareMuxTarget(fileName, settings.fileVidUri)
            val ffmpegOutputPath = when (muxTarget) {
                is MuxTarget.Direct -> muxTarget.ffmpegPath
                is MuxTarget.Local -> muxTarget.path
            }
            val fallbackMuxerFlag = if (target == TargetFormat.WEBM) "-f webm " else "-f mp4 "
            val ffmpegArgs = buildString {
                append("-y -i \"$videoPath\" ")
                if (hasThumbnail) append("-i \"$imagePath\" ")
                append("-map 0:v:0 -map 0:a:0 ")
                if (hasThumbnail) append("-map 1:v ")
                append("-c:v:0 copy -c:a:0 copy ")
                if (hasThumbnail) {
                    append("-c:v:1 mjpeg -disposition:v:1 attached_pic ")
                    append("-metadata:s:v:1 title=\"Album cover\" -metadata:s:v:1 comment=\"Cover\" ")
                }
                append("-metadata title=\"$confirmedTitle\" -metadata artist=\"$confirmedAuthor\" ")
                append(fallbackMuxerFlag)
                append("\"$ffmpegOutputPath\"")
            }

            val rawSources = listOf(RawSource(videoPath, rawVideoExtension(muxed.format?.name), "video", true))
            finishVideo(ffmpegArgs, streamInfo, title, unalteredTitle, confirmedTitle, confirmedAuthor, rawSources, muxTarget, fileName) { onProgress(it, true) }
        } else {
            throw Exception("No video streams available for this video.")
        }
    }

    private suspend fun finishVideo(
        ffmpegArgs: String, streamInfo: StreamInfo, title: String, unalteredTitle: String,
        confirmedTitle: String, confirmedAuthor: String,
        rawSources: List<RawSource>, muxTarget: MuxTarget, fileName: String, onProgress: (Float) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            isMuxing = true
            muxedBytesTotalEstimate = bytesTotal
            muxedBytesEstimate = 0L
            muxEtaSeconds = null
        }
        val ffmpegResult = DownloadUtils.runFFmpeg(ffmpegArgs, streamInfo.duration, onProgress)
        withContext(Dispatchers.Main) { isMuxing = false }

        rawSources.forEach { if (File(it.path).exists()) File(it.path).delete() }

        if (ffmpegResult) {
            val savedUri: Uri? = when (muxTarget) {
                is MuxTarget.Direct -> muxTarget.uri
                is MuxTarget.Local -> {
                    val uri = FileSaver.saveVideo(context, fileName, muxTarget.path, settings.fileVidUri.ifBlank { null })
                    if (File(muxTarget.path).exists()) File(muxTarget.path).delete()
                    uri
                }
            }
            if (settings.notifyOnFinish) {
                DownloadNotificationService.showFinishNotification(context, fileName, notificationId)
            }
            withContext(Dispatchers.Main) {
                if (savedUri != null) {
                    val updated = settings.downloadHistory.toMutableList()
                    val existingIndex = updated.indexOfFirst { it.urlOrId == id }
                    if (existingIndex != -1) {
                        updated[existingIndex] = updated[existingIndex].copy(downloaded = true, uri = savedUri.toString())
                    } else {
                        updated.add(0, SettingsSave.HistoryItem(
                            title = unalteredTitle, metadataTitle = confirmedTitle, metadataAuthor = confirmedAuthor,
                            isMp3 = isMp3, urlOrId = id, downloaded = true, uri = savedUri.toString()
                        ))
                    }
                    settings.downloadHistory = updated
                }
                isDownloaded = true
                callbacks.showPopup(context.getString(R.string.pt_finished), context.getString(R.string.pm_finished), PopupType.SUCCESS)
                callbacks.onDownloadFinished(savedUri, false)
            }
        } else {
            if (muxTarget is MuxTarget.Direct) {
                try { DocumentFile.fromSingleUri(context, muxTarget.uri)?.delete() } catch (_: Exception) { }
            }
            rawSources.forEach { offerToKeepPartialFile(it.path, it.extension, title, it.label, it.isVideo) }
            withContext(Dispatchers.Main) {
                callbacks.showPopup(context.getString(R.string.pt_failed), context.getString(R.string.pm_failed), PopupType.ERROR)
                callbacks.onFailed()
            }
            if (settings.notifyOnFail) {
                DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_failed), notificationId)
            }
        }
    }

    private fun reportError(e: Exception) {
        when {
            e is InsufficientStorageException -> {
                val message = context.getString(
                    R.string.pm_no_space,
                    formatBytes(e.availableBytes.coerceAtLeast(0L)),
                    formatBytes(e.requiredBytes)
                )
                callbacks.showPopup(context.getString(R.string.pt_no_space), message, PopupType.ERROR)
                if (settings.notifyOnFail) DownloadNotificationService.showFailedNotification(context, message, notificationId)
            }
            e.message?.contains("Not enough free storage") == true -> {
                callbacks.showPopup(context.getString(R.string.pt_no_space), context.getString(R.string.pm_no_space), PopupType.ERROR)
                if (settings.notifyOnFail) DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_no_space), notificationId)
            }
            e.message?.contains("403") == true || e.message?.contains("404") == true -> {
                callbacks.showPopup(context.getString(R.string.pt_unavailable), context.getString(R.string.pm_unavailable), PopupType.ERROR)
                if (settings.notifyOnFail) DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_unavailable), notificationId)
            }
            e.message?.contains("ID or URL") == true || e.message?.contains("JSON response is too short") == true -> {
                callbacks.showPopup(context.getString(R.string.pt_invalid_url), context.getString(R.string.pm_invalid_url), PopupType.ERROR)
                if (settings.notifyOnFail) DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_invalid_url), notificationId)
            }
            e.message?.contains("Software caused connection abort") == true || e.message?.contains("No address associated with hostname") == true || e.message?.contains("Connection reset") == true -> {
                callbacks.showPopup(context.getString(R.string.pt_network_error), context.getString(R.string.pm_network_error), PopupType.ERROR)
                if (settings.notifyOnFail) DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_network_error), notificationId)
            }
            e.message?.contains("streams available for this video") == true -> {
                callbacks.showPopup(context.getString(R.string.pt_no_streams), context.getString(R.string.pm_no_streams), PopupType.ERROR)
                if (settings.notifyOnFail) DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_no_streams), notificationId)
            }
            else -> {
                callbacks.showPopup(context.getString(R.string.pt_error), e.message ?: context.getString(R.string.pm_error), PopupType.ERROR)
                Log.d("DownloadError", e.stackTraceToString())
                if (settings.notifyOnFail) DownloadNotificationService.showFailedNotification(context, context.getString(R.string.pm_error), notificationId)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(java.util.Locale.getDefault(), "%.2f GB", gb)
            mb >= 1 -> String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
            else -> String.format(java.util.Locale.getDefault(), "%.0f KB", kb)
        }
    }

    companion object {
        private const val MIN_SALVAGEABLE_BYTES = 1L * 1024 * 1024
        private const val UI_UPDATE_INTERVAL_MS = 500L // 2 updates/sec; use 1000L for 1/sec
        private const val INTERNAL_SCRATCH_BUFFER_BYTES = 100L * 1024 * 1024

        fun cleanupOrphanedTempFiles(context: Context, olderThanMs: Long = 60 * 60 * 1000L) {
            val cutoff = System.currentTimeMillis() - olderThanMs
            val prefixes = listOf("audio-", "video-", "thumbnail-", "tempThumbnail-")
            (context.cacheDir.listFiles() ?: emptyArray())
                .filter { file -> prefixes.any { file.name.startsWith(it) } && file.lastModified() < cutoff }
                .forEach { it.delete() }
            (context.filesDir.listFiles() ?: emptyArray())
                .filter { file -> file.name.startsWith("semi-output-") && file.lastModified() < cutoff }
                .forEach { it.delete() }
        }
    }
}