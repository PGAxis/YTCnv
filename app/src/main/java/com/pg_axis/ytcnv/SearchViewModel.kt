package com.pg_axis.ytcnv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.time.Duration.Companion.milliseconds

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    val context = getApplication<Application>()

    val settings = SettingsSave.getInstance(context)
    var searchQuery by mutableStateOf(TextFieldValue(""))
    var results by mutableStateOf<List<SearchResultItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var endReached by mutableStateOf(false)
    var isMusicSearch by mutableStateOf(false)
    private var extractor: SearchExtractor? = null
    private var nextPage: Page? = null

    fun onQueryChanged(value: TextFieldValue) { searchQuery = value }

    fun onSearch() {
        if (searchQuery.text.isBlank()) return

        updateSearchHistory(searchQuery.text.trim())

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                endReached = false
                isLoading = true
                errorMessage = null
                results = emptyList()
            }

            try {
                val contentFilters = if (isMusicSearch)
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
                else
                    emptyList()

                extractor = ServiceList.YouTube.getSearchExtractor(searchQuery.text.trim(), contentFilters, "")
                extractor?.fetchPage()

                val page = extractor?.initialPage
                val newItems = mapItems(page?.items)

                withContext(Dispatchers.Main) {
                    results = newItems
                    nextPage = page?.nextPage
                    endReached = nextPage == null
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { errorMessage = e.toString() }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun onLoadMore() {
        if (isLoading || endReached || nextPage == null) return

        viewModelScope.launch(Dispatchers.IO) {
            isLoadingMore = true

            try {
                val page = extractor?.getPage(nextPage)
                nextPage = page?.nextPage

                val newItems = mapItems(page?.items)

                results += newItems
                endReached = nextPage == null

            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoadingMore = false
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // "Add to queue" popup: load metadata -> pick format/quality -> confirm title/author -> enqueue
    // ------------------------------------------------------------------------------------------

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
        val token = ++queueToken
        queueItem = item
        queueQuickFormatIndex = if (settings.defaultDO.trackType == TrackType.VIDEO) 1 else 0
        val video = Video(item.videoId, context, loadCallbacks(token))
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
            if (suggested == null || token != queueToken) return@launch // error is shown in the popup
            dialogTitle = suggested.first
            dialogAuthor = suggested.second
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

    /** auto-confirms after AUTO_DOWNLOAD_SECONDS without interaction. */
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
        closeQueueDialog()
        if (video != null && selection != null) {
            video.configure(selection.target, selection.isNative, selection.qualityIndex, selection.preferredAudioBitrate, title, author)
            onVideoReady(video)
        }
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
    }

    fun onHistoryItemTapped(query: String) {
        searchQuery = TextFieldValue(
            text = query,
            selection = TextRange(query.length)
        )
    }

    fun onRemoveHistoryItem(query: String) {
        val updated = settings.searchHistory.toMutableList()
        updated.remove(query)
        settings.searchHistory = updated
    }

    fun isDownloaded(videoId: String): Boolean {
        return settings.downloadHistory.any { it.urlOrId == videoId && it.downloaded }
    }

    private fun updateSearchHistory(query: String) {
        val updated = settings.searchHistory.toMutableList()
        updated.remove(query)
        updated.add(0, query)
        settings.searchHistory = updated
    }

    private fun extractVideoId(url: String): String {
        return url.substringAfter("v=").substringBefore("&")
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "Live"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    private fun mapItems(items: List<InfoItem>?) =
        items?.filterIsInstance<StreamInfoItem>()?.map { item ->
            SearchResultItem(
                title = item.name,
                videoId = extractVideoId(item.url),
                uploader = item.uploaderName ?: "",
                duration = formatDuration(item.duration),
                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url ?: "",
                url = item.url
            )
        } ?: emptyList()
}

private const val AUTO_DOWNLOAD_SECONDS = 30

data class SearchResultItem(
    val title: String,
    val videoId: String,
    val uploader: String,
    val duration: String,
    val thumbnailUrl: String,
    val url: String
)