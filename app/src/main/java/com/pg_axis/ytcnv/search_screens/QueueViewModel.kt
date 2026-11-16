package com.pg_axis.ytcnv.search_screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.ytcnv.PlaylistResultItem
import com.pg_axis.ytcnv.PopupType
import com.pg_axis.ytcnv.SearchResultItem
import com.pg_axis.ytcnv.extractId
import com.pg_axis.ytcnv.models.DownloadQueue
import com.pg_axis.ytcnv.models.LoadOnlyCallbacks
import com.pg_axis.ytcnv.models.TargetFormat
import com.pg_axis.ytcnv.models.TrackType
import com.pg_axis.ytcnv.models.Video
import com.pg_axis.ytcnv.settings.SettingsSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.time.Duration.Companion.milliseconds

open class QueueViewModel(application: Application) : AndroidViewModel(application) {
    val context = getApplication<Application>()

    val settings = SettingsSave.getInstance(context)

    fun isDownloaded(videoId: String): Boolean {
        return settings.downloadHistory.any { it.urlOrId == videoId && it.downloaded }
    }

    // ------------------------------------------------------------------------------------------
    // "Add to queue" popup: load metadata -> pick format/quality -> confirm title/author -> enqueue
    // ------------------------------------------------------------------------------------------
    private var holdsKeepAlive = false

    var onVideoReady: (Video) -> Unit = {}

    var queueItem by mutableStateOf<SearchResultItem?>(null)
        private set
    var queueVideo by mutableStateOf<Video?>(null)
        private set
    var queueIsLoading by mutableStateOf(false)
        private set
    var queueIsPreparing by mutableStateOf(false)
        private set
    var queueError by mutableStateOf<String?>(null)
        private set

    var queueQuickFormatIndex by mutableIntStateOf(0)
        private set
    var queueDetailedFormatIndex by mutableIntStateOf(0)
        private set
    var queueQualityIndex by mutableIntStateOf(0)
        private set
    var queueAudioTrackIndex by mutableIntStateOf(0)
        private set

    var showTitleAuthorDialog by mutableStateOf(false)
        private set
    var dialogTitle by mutableStateOf("")
        private set
    var dialogAuthor by mutableStateOf("")
        private set

    var playlistPrompt by mutableStateOf<PlaylistResultItem?>(null)
        private set
    var playlistEnqueued by mutableStateOf<Int?>(null)   // null = not loading
        private set
    var playlistError by mutableStateOf<String?>(null)
        private set
    private var playlistJob: Job? = null

    var autoDownloadSecondsRemaining by mutableIntStateOf(AUTO_DOWNLOAD_SECONDS)
        private set
    private var autoDownloadJob: Job? = null

    private var loadJob: Job? = null
    private var queueToken = 0

    private data class PendingSelection(
        val target: TargetFormat,
        val isNative: Boolean,
        val qualityIndex: Int,
        val preferredAudioBitrate: Int?
    )
    private var pendingSelection: PendingSelection? = null

    private val preferredDefaultFormat: TargetFormat
        get() = if (settings.defaultDO.trackType == TrackType.VIDEO) settings.defaultDO.video else settings.defaultDO.audio

    private fun loadCallbacks(token: Int) = object : LoadOnlyCallbacks() {
        override fun onLoadStarted() {
            if (token == queueToken) queueIsLoading = true
        }

        override fun onLoadFinished() {
            if (token != queueToken) return
            queueIsLoading = false
            val video = queueVideo ?: return
            val options = video.formatOptions
            val defaultIndex = options.indexOfFirst { it.format == preferredDefaultFormat }
            queueDetailedFormatIndex = if (defaultIndex >= 0) defaultIndex else 0
            queueQualityIndex = 0
            options.getOrNull(queueDetailedFormatIndex)?.let { chosen ->
                viewModelScope.launch { video.loadQualitySizes(chosen.format, chosen.isNative) }
            }
        }

        override fun onFailed() {
            if (token == queueToken) queueIsLoading = false
        }

        override fun showPopup(title: String, message: String, type: PopupType) {
            if (token == queueToken) queueError = message
        }
    }

    /** opens popup and starts loading the metadata. */
    fun onDownloadClicked(item: SearchResultItem) {
        closeQueueDialog()
        if (item is PlaylistResultItem) {
            if (playlistEnqueued != null) return
            playlistError = null
            playlistPrompt = item
            return
        }
        val token = ++queueToken
        queueItem = item
        queueQuickFormatIndex = if (settings.defaultDO.trackType == TrackType.VIDEO) 1 else 0
        val video = Video(item.itemId, context, loadCallbacks(token))
        queueVideo = video
        loadJob = viewModelScope.launch { video.loadOptions() }
    }

    fun onQueueQuickFormatChanged(index: Int) { queueQuickFormatIndex = index }

    fun onQueueDetailedFormatChanged(index: Int) {
        queueDetailedFormatIndex = index
        queueQualityIndex = 0
        val video = queueVideo ?: return
        val option = video.formatOptions.getOrNull(index) ?: return
        viewModelScope.launch { video.loadQualitySizes(option.format, option.isNative) }
    }

    fun onQueueQualityChanged(index: Int) { queueQualityIndex = index }

    fun onQueueAudioTrackChanged(index: Int) { queueAudioTrackIndex = index }

    /** ask for title/author. */
    fun onQueueOptionsConfirmed() {
        val video = queueVideo ?: return
        if (queueIsPreparing) return

        val target: TargetFormat
        val isNative: Boolean
        val qualityIndex: Int
        if (settings.quickDwnld) {
            target = if (queueQuickFormatIndex == 0) TargetFormat.MP3 else TargetFormat.MP4
            isNative = false
            qualityIndex = 0
        } else {
            val option = video.formatOptions.getOrNull(queueDetailedFormatIndex) ?: return
            target = option.format
            isNative = option.isNative
            qualityIndex = queueQualityIndex
        }
        val bitrate = if (!settings.quickDwnld && target.trackType == TrackType.VIDEO) {
            video.audioTrackOptions.getOrNull(queueAudioTrackIndex)?.bitrate
        } else null
        pendingSelection = PendingSelection(target, isNative, qualityIndex, bitrate)

        val token = queueToken
        queueIsPreparing = true
        viewModelScope.launch {
            val suggested = try {
                video.ensureMetadata()
            } finally {
                queueIsPreparing = false
            }
            if (suggested == null || token != queueToken) return@launch
            dialogTitle = suggested.first
            dialogAuthor = suggested.second
            DownloadQueue.acquireKeepAlive(context, video.notificationId)
            holdsKeepAlive = true
            showTitleAuthorDialog = true
            onTitleAuthorDialogInteraction()
        }
    }

    fun onDialogTitleChanged(value: String) {
        dialogTitle = value
        onTitleAuthorDialogInteraction()
    }

    fun onDialogAuthorChanged(value: String) {
        dialogAuthor = value
        onTitleAuthorDialogInteraction()
    }

    /** auto-confirms after [AUTO_DOWNLOAD_SECONDS] without interaction. */
    fun onTitleAuthorDialogInteraction() {
        autoDownloadJob?.cancel()
        autoDownloadSecondsRemaining = AUTO_DOWNLOAD_SECONDS
        autoDownloadJob = viewModelScope.launch {
            delay(1000.milliseconds)
            while (autoDownloadSecondsRemaining > 0) {
                delay(1000.milliseconds)
                autoDownloadSecondsRemaining--
            }
            onTitleAuthorConfirmed()
        }
    }

    /** Configure the Video and hand it over to MainViewModel's queue. */
    fun onTitleAuthorConfirmed() {
        val video = queueVideo
        val selection = pendingSelection
        val title = dialogTitle
        val author = dialogAuthor
        if (video != null && selection != null) {
            video.configure(selection.target, selection.isNative, selection.qualityIndex, selection.preferredAudioBitrate, title, author)
            onVideoReady(video) // enqueue before releasing
        }
        closeQueueDialog()
    }

    fun onPlaylistFormatChosen(audio: Boolean) {
        val item = playlistPrompt ?: return
        if (playlistEnqueued != null) return
        val target = if (audio) TargetFormat.MP3 else TargetFormat.MP4

        playlistEnqueued = 0
        playlistJob = viewModelScope.launch {
            DownloadQueue.acquireKeepAlive(context, item.url.hashCode())
            try {
                withContext(Dispatchers.IO) {
                    val info = PlaylistInfo.getInfo(ServiceList.YouTube, item.url)
                    var total = enqueueBatch(info.relatedItems, target, MAX_PLAYLIST_ITEMS)
                    var next: Page? = info.nextPage

                    while (next != null && total < MAX_PLAYLIST_ITEMS) {
                        val page = PlaylistInfo.getMoreItems(ServiceList.YouTube, item.url, next)
                        total += enqueueBatch(page.items, target, MAX_PLAYLIST_ITEMS - total)
                        next = page.nextPage
                    }
                }
                playlistPrompt = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                playlistError = e.message ?: e.toString()
            } finally {
                playlistEnqueued = null
                DownloadQueue.releaseKeepAlive(context)
            }
        }
    }

    private suspend fun enqueueBatch(items: List<StreamInfoItem>, target: TargetFormat, limit: Int): Int {
        val videos = items
            .filter { it.duration > 0 }
            .take(limit)
            .map { stream ->
                Video(extractId(stream), context, object : LoadOnlyCallbacks() {
                    override fun onLoadStarted() {}
                    override fun onLoadFinished() {}
                    override fun onFailed() {}
                    override fun showPopup(title: String, message: String, type: PopupType) {}
                }).also {
                    it.configure(target, isNative = false, qualityIndex = 0,
                        preferredAudioBitrate = null, title = null, author = null)
                }
            }
        withContext(Dispatchers.Main) {
            videos.forEach { onVideoReady(it) }
            playlistEnqueued = (playlistEnqueued ?: 0) + videos.size
        }
        return videos.size
    }

    /** Cancel / dismiss at any step. */
    fun closeQueueDialog() {
        queueToken++
        autoDownloadJob?.cancel()
        autoDownloadJob = null
        loadJob?.cancel()
        loadJob = null
        showTitleAuthorDialog = false
        queueItem = null
        queueVideo = null
        queueIsLoading = false
        queueIsPreparing = false
        queueError = null
        queueDetailedFormatIndex = 0
        queueQualityIndex = 0
        queueAudioTrackIndex = 0
        pendingSelection = null
        if (holdsKeepAlive) {
            holdsKeepAlive = false
            DownloadQueue.releaseKeepAlive(context)
        }
    }

    fun closePlaylistDialog() {
        playlistJob?.cancel()
        playlistJob = null
        playlistPrompt = null
        playlistEnqueued = null
        playlistError = null
    }
}

private const val AUTO_DOWNLOAD_SECONDS = 30

private const val MAX_PLAYLIST_ITEMS = 500