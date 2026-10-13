package com.pg_axis.ytcnv

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.ytcnv.dialogs.UpdateChecker
import com.pg_axis.ytcnv.dialogs.UpdateInfo
import com.pg_axis.ytcnv.models.DownloadQueue
import com.pg_axis.ytcnv.models.FormatOption
import com.pg_axis.ytcnv.models.LoadOnlyCallbacks
import com.pg_axis.ytcnv.models.TargetFormat
import com.pg_axis.ytcnv.models.TrackType
import com.pg_axis.ytcnv.models.Video
import com.pg_axis.ytcnv.models.VideoCallbacks
import com.pg_axis.ytcnv.services.MusicAxsClient
import com.pg_axis.ytcnv.settings.*
import com.pg_axis.ytcnv.ui.theme.PopupSuccess
import com.pg_axis.ytcnv.ui.theme.PopupError
import com.pg_axis.ytcnv.ui.theme.PopupDefault
import com.pg_axis.ytcnv.utils.StringUtils.cleanUrl
import com.pg_axis.ytcnv.utils.StringUtils.isValidId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

enum class PopupType {
    SUCCESS,
    ERROR,
    MESSAGE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsSave.getInstance(application)

    private val isQuick get() = settings.quickDwnld
    private val context get() = getApplication<Application>()

    private val preferredDefaultFormat: TargetFormat
        get() = if (settings.defaultDO.trackType == TrackType.VIDEO) settings.defaultDO.video else settings.defaultDO.audio

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Video.cleanupOrphanedTempFiles(context)
        }
    }

    var showPlaylistPicker by mutableStateOf(false)
    var lastDownloadedSongUri by mutableStateOf<Uri?>(null)

    var urlEntryText by mutableStateOf("")
        private set

    var downloadOptionsIsVisible by mutableStateOf(true)
    var formatPickerIsEnabled by mutableStateOf(true)
    var qualityPickerIsVisible by mutableStateOf(!isQuick)
    var qualityPickerIsEnabled by mutableStateOf(true)
    var loadButtonIsVisible by mutableStateOf(!isQuick)
    var loadButtonIsEnabled by mutableStateOf(true)
    var downloadButtonIsVisible by mutableStateOf(isQuick)

    var selectedFormatIndex by mutableIntStateOf(if (settings.defaultDO.trackType == TrackType.VIDEO) 1 else 0)
        private set

    var selectedDetailedFormatIndex by mutableIntStateOf(0)
        private set

    var selectedQualityIndex by mutableIntStateOf(0)
        private set

    var selectedAudioTrackIndex by mutableIntStateOf(0)
        private set

    val cancelButtonIsVisible: Boolean get() = DownloadQueue.phase != DownloadQueue.Phase.IDLE
    val downloadIndicatorIsVisible: Boolean get() = DownloadQueue.phase == DownloadQueue.Phase.RETRIEVING
    val dwnldProgressIsVisible: Boolean get() = DownloadQueue.phase == DownloadQueue.Phase.DOWNLOADING
    val statusLabelIsVisible: Boolean get() = DownloadQueue.phase != DownloadQueue.Phase.IDLE

    var isLoadingOptions by mutableStateOf(false)
        private set
    var isPreparingDownload by mutableStateOf(false)
        private set

    var popupIsVisible by mutableStateOf(false)
    var popupBackground by mutableStateOf(Color(0xFF2D2D2D))
    var popupTitle by mutableStateOf("")
    var popupMessage by mutableStateOf("")
    var popupButtonText by mutableStateOf("OK")

    var showKeepPartialDialog by mutableStateOf(false)
    var keepPartialSizeBytes by mutableLongStateOf(0L)
    var keepPartialStreamLabel by mutableStateOf("")
    private var pendingKeepPartialChoice: ((Boolean) -> Unit)? = null

    var showCancelConfirmDialog by mutableStateOf(false)

    var showMarginOverrideDialog by mutableStateOf(false)
    var marginOverrideAvailableBytes by mutableLongStateOf(0L)
    var marginOverrideRequiredBytes by mutableLongStateOf(0L)
    private var pendingMarginOverrideChoice: ((Boolean) -> Unit)? = null

    fun onMarginOverrideChosen(proceed: Boolean) {
        showMarginOverrideDialog = false
        pendingMarginOverrideChoice?.invoke(proceed)
        pendingMarginOverrideChoice = null
    }

    fun onKeepPartialChosen(keep: Boolean) {
        showKeepPartialDialog = false
        pendingKeepPartialChoice?.invoke(keep)
        pendingKeepPartialChoice = null
    }

    var showTitleAuthorDialog by mutableStateOf(false)
    var dialogTitle by mutableStateOf("")
    var dialogAuthor by mutableStateOf("")
    private var pendingOnConfirm: ((String, String) -> Unit)? = null
    var autoDownloadSecondsRemaining by mutableIntStateOf(AUTO_DOWNLOAD_SECONDS)
        private set
    private var autoDownloadJob: Job? = null

    fun onDialogTitleChanged(value: String) {
        dialogTitle = value
        onTitleAuthorDialogInteraction()
    }

    fun onDialogAuthorChanged(value: String) {
        dialogAuthor = value
        onTitleAuthorDialogInteraction()
    }

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

    private fun cancelAutoDownloadTimer() {
        autoDownloadJob?.cancel()
        autoDownloadJob = null
    }

    var video by mutableStateOf<Video?>(null)
        private set

    val videoQueue: List<Video> get() = DownloadQueue.videoQueue

    val activeVideo: Video? get() = DownloadQueue.activeVideo

    var updateInfo by mutableStateOf<UpdateInfo?>(null)

    suspend fun checkForUpdates(context: Context) {
        val info = UpdateChecker.checkForUpdates(context, settings)
        if (info != null) {
            withContext(Dispatchers.Main) { updateInfo = info }
        }
    }

    fun onUpdateDialogDismissed(dontShowAgain: Boolean) {
        updateInfo = null
        settings.dontShowUpdate = dontShowAgain
    }

    fun onUrlChanged(value: String) {
        urlEntryText = value
        video = null
        isLoadingOptions = false
        selectedDetailedFormatIndex = 0
        selectedQualityIndex = 0
        selectedAudioTrackIndex = 0
        applyQuickDownloadState()
        if (isValidId(cleanUrl(urlEntryText))) {
            onLoadClicked()
        }
    }

    fun onFormatChanged(index: Int) {
        selectedFormatIndex = index
    }

    fun onDetailedFormatChanged(index: Int) {
        selectedDetailedFormatIndex = index
        selectedQualityIndex = 0
        val option = video?.formatOptions?.getOrNull(index) ?: return
        viewModelScope.launch {
            video?.loadQualitySizes(option.format, option.isNative)
        }
    }

    fun onQualityChanged(index: Int) {
        selectedQualityIndex = index
    }

    fun onAudioTrackChanged(index: Int) {
        selectedAudioTrackIndex = index
    }

    fun onClosePopupClicked() { popupIsVisible = false }

    fun onHistoryItemTapped(urlOrId: String) {
        onUrlChanged(urlOrId)
    }

    val selectedFormatOption: FormatOption?
        get() = video?.formatOptions?.getOrNull(selectedDetailedFormatIndex)

    fun onLoadClicked() {
        selectedDetailedFormatIndex = 0
        selectedQualityIndex = 0
        selectedAudioTrackIndex = 0
        val newVideo = Video(urlEntryText, context, formCallbacks)
        video = newVideo
        viewModelScope.launch {
            newVideo.loadOptions()
        }
    }

    fun onDownloadClicked() {
        if (isPreparingDownload) return
        val currentVideo = video ?: Video(urlEntryText, context, formCallbacks).also { video = it }

        val target: TargetFormat
        val isNative: Boolean
        val qualityIndex: Int
        if (settings.quickDwnld) {
            target = if (selectedFormatIndex == 0) TargetFormat.MP3 else TargetFormat.MP4
            isNative = false
            qualityIndex = 0
        } else {
            val option = selectedFormatOption ?: FormatOption(TargetFormat.MP3, isNative = false, context)
            target = option.format
            isNative = option.isNative
            qualityIndex = selectedQualityIndex
        }

        val preferredAudioBitrate = if (!settings.quickDwnld && target.trackType == TrackType.VIDEO) {
            currentVideo.audioTrackOptions.getOrNull(selectedAudioTrackIndex)?.bitrate
        } else null

        isPreparingDownload = true
        viewModelScope.launch {
            val suggested = try {
                currentVideo.ensureMetadata()
            } finally {
                isPreparingDownload = false
            }
            if (suggested == null) return@launch

            requestTitleAuthor(suggested.first, suggested.second) { title, author ->
                currentVideo.configure(target, isNative, qualityIndex, preferredAudioBitrate, title, author)
                enqueueVideo(currentVideo)
                onUrlChanged("")
            }
        }
    }

    /** Adds a fully configured video (see [Video.configure]) to the app-wide queue. */
    fun enqueueVideo(newVideo: Video) {
        DownloadQueue.enqueue(context, newVideo, downloadCallbacks)
    }

    fun onCancelClicked() {
        showCancelConfirmDialog = true
    }

    fun onCancelConfirmed() {
        showCancelConfirmDialog = false
        // Cancels the ACTIVE video only; the queue then moves on to the next one.
        DownloadQueue.cancelActive()
    }

    fun onCancelDismissed() {
        showCancelConfirmDialog = false
    }

    fun onTitleAuthorConfirmed() {
        cancelAutoDownloadTimer()
        showTitleAuthorDialog = false
        pendingOnConfirm?.invoke(dialogTitle, dialogAuthor)
        pendingOnConfirm = null
    }

    fun onTitleAuthorDismissed() {
        cancelAutoDownloadTimer()
        showTitleAuthorDialog = false
        pendingOnConfirm = null
    }

    private fun requestTitleAuthor(title: String, author: String, onConfirm: (String, String) -> Unit) {
        dialogTitle = title
        dialogAuthor = author
        pendingOnConfirm = onConfirm
        showTitleAuthorDialog = true
        onTitleAuthorDialogInteraction()
    }

    fun applyQuickDownloadState() {
        val quick = settings.quickDwnld
        downloadOptionsIsVisible = true
        formatPickerIsEnabled = true
        qualityPickerIsVisible = !quick
        qualityPickerIsEnabled = true
        loadButtonIsVisible = !quick
        loadButtonIsEnabled = true
        downloadButtonIsVisible = quick
    }

    private fun applyLoadedState() {
        downloadOptionsIsVisible = true
        formatPickerIsEnabled = true
        qualityPickerIsVisible = true
        qualityPickerIsEnabled = true
        loadButtonIsVisible = false
        loadButtonIsEnabled = true
        downloadButtonIsVisible = true
    }

    private fun resetFormAfterFailure() {
        if (!settings.quickDwnld && video?.formatOptions?.isNotEmpty() == true) {
            applyLoadedState()
        } else {
            applyQuickDownloadState()
        }
    }

    fun showPopup(title: String, message: String, type: PopupType = PopupType.MESSAGE, buttonText: String = "OK") {
        popupBackground = when (type) {
            PopupType.SUCCESS -> PopupSuccess
            PopupType.ERROR -> PopupError
            PopupType.MESSAGE -> PopupDefault
        }
        popupTitle = title
        popupMessage = message
        popupButtonText = buttonText
        popupIsVisible = true
    }

    private val formCallbacks = object : LoadOnlyCallbacks() {
        override fun onLoadStarted() {
            loadButtonIsEnabled = false
            isLoadingOptions = true
        }

        override fun onLoadFinished() {
            loadButtonIsVisible = false
            loadButtonIsEnabled = true
            isLoadingOptions = false
            downloadOptionsIsVisible = true
            qualityPickerIsVisible = true
            downloadButtonIsVisible = true

            val options = video?.formatOptions ?: emptyList()
            val defaultIndex = options.indexOfFirst { it.format == preferredDefaultFormat }
            selectedDetailedFormatIndex = if (defaultIndex >= 0) defaultIndex else 0
            selectedQualityIndex = 0

            val chosen = options.getOrNull(selectedDetailedFormatIndex)
            if (chosen != null) {
                viewModelScope.launch { video?.loadQualitySizes(chosen.format, chosen.isNative) }
            }
        }

        override fun onFailed() {
            isLoadingOptions = false
            resetFormAfterFailure()
        }

        override fun showPopup(title: String, message: String, type: PopupType) {
            this@MainViewModel.showPopup(title, message, type)
        }
    }

    private val downloadCallbacks = object : VideoCallbacks {
        override fun onLoadStarted() {}
        override fun onLoadFinished() {}

        override fun onDownloadStarted() {}
        override fun onDownloadInProgress() {}

        override fun onDownloadFinished(savedUri: Uri?, isAudio: Boolean) {
            if (isAudio && savedUri != null &&
                MusicAxsClient.isMusicAxsInstalled(context) &&
                MusicAxsClient.getPlaylists(context).isNotEmpty() &&
                settings.addToMusicAxs
            ) {
                lastDownloadedSongUri = savedUri
                showPlaylistPicker = true
            }
        }

        override fun onFailed() {}
        override fun onCanceled() {}

        override fun showPopup(title: String, message: String, type: PopupType) {
            this@MainViewModel.showPopup(title, message, type)
        }

        override fun offerKeepPartialDownload(sizeBytes: Long, streamLabel: String, onChoice: (Boolean) -> Unit) {
            keepPartialSizeBytes = sizeBytes
            keepPartialStreamLabel = streamLabel
            pendingKeepPartialChoice = onChoice
            showKeepPartialDialog = true
        }

        override fun offerDownloadWithoutMargin(availableBytes: Long, requiredBytesWithoutMargin: Long, onChoice: (Boolean) -> Unit) {
            marginOverrideAvailableBytes = availableBytes
            marginOverrideRequiredBytes = requiredBytesWithoutMargin
            pendingMarginOverrideChoice = onChoice
            showMarginOverrideDialog = true
        }

        override fun stopService() {}
    }

    companion object {
        private const val AUTO_DOWNLOAD_SECONDS = 30
    }
}