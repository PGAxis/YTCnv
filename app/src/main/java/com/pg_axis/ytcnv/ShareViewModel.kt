package com.pg_axis.ytcnv

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.ytcnv.models.FormatOption
import com.pg_axis.ytcnv.models.QualityOption
import com.pg_axis.ytcnv.models.TargetFormat
import com.pg_axis.ytcnv.models.TrackType
import com.pg_axis.ytcnv.models.Video
import com.pg_axis.ytcnv.models.VideoCallbacks
import com.pg_axis.ytcnv.settings.SettingsSave
import com.pg_axis.ytcnv.utils.DownloadNotificationService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

enum class SheetState { LOADING_METADATA, PICKING }

class ShareViewModel(application: Application, rawUrl: String) : AndroidViewModel(application), VideoCallbacks {
    private val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    private val video = Video(rawUrl, context, this)

    private val preferredDefaultFormat: TargetFormat
        get() = if (settings.defaultDO.trackType == TrackType.VIDEO) settings.defaultDO.video else settings.defaultDO.audio

    var sheetState by mutableStateOf(
        if (settings.quickDwnld) SheetState.PICKING else SheetState.LOADING_METADATA
    )

    var formatIndex by mutableIntStateOf(if (settings.defaultDO.trackType == TrackType.VIDEO) 1 else 0)
        private set

    var selectedDetailedFormatIndex by mutableIntStateOf(0)
        private set

    var qualityIndex by mutableIntStateOf(0)
        private set

    val formatOptions: List<FormatOption>
        get() = video.formatOptions

    val selectedFormatOption: FormatOption?
        get() = video.formatOptions.getOrNull(selectedDetailedFormatIndex)

    val qualityOptions: List<QualityOption>
        get() = video.qualityOptionsWithSizes

    val isFetchingQualitySizes: Boolean
        get() = video.isFetchingQualitySizes

    init {
        if (!settings.quickDwnld) {
            viewModelScope.launch {
                video.loadOptions()
            }
        }
    }

    fun onFormatChanged(index: Int) {
        formatIndex = index
    }

    fun onDetailedFormatChanged(index: Int) {
        selectedDetailedFormatIndex = index
        qualityIndex = 0
        val option = video.formatOptions.getOrNull(index) ?: return
        viewModelScope.launch {
            video.loadQualitySizes(option.format, option.isNative)
        }
    }

    fun onQualityChanged(index: Int) {
        qualityIndex = index
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startDownload(onDone: () -> Unit) {
        val target: TargetFormat
        val isNative: Boolean
        val quality: Int
        if (settings.quickDwnld) {
            target = if (formatIndex == 0) TargetFormat.MP3 else TargetFormat.MP4
            isNative = false
            quality = 0
        } else {
            val option = selectedFormatOption ?: FormatOption(TargetFormat.MP3, isNative = false, context)
            target = option.format
            isNative = option.isNative
            quality = qualityIndex
        }

        GlobalScope.launch(Dispatchers.IO) {
            video.download(target, isNative, quality)
        }
        onDone()
    }


    override fun onLoadStarted() {
    }

    override fun onLoadFinished() {
        val options = video.formatOptions
        val defaultIndex = options.indexOfFirst { it.format == preferredDefaultFormat }
        selectedDetailedFormatIndex = if (defaultIndex >= 0) defaultIndex else 0
        qualityIndex = 0
        sheetState = SheetState.PICKING

        val chosen = options.getOrNull(selectedDetailedFormatIndex)
        if (chosen != null) {
            viewModelScope.launch { video.loadQualitySizes(chosen.format, chosen.isNative) }
        }
    }

    override fun onDownloadStarted() {
    }

    override fun onDownloadInProgress() {
    }

    override fun onDownloadFinished(savedUri: Uri?, isAudio: Boolean) {
    }

    override fun onFailed() {
        sheetState = SheetState.PICKING
    }

    override fun onCanceled() {
    }

    override fun showPopup(title: String, message: String, type: PopupType) {
    }

    override fun requestTitleAuthorConfirmation(title: String, author: String, onConfirm: (String, String) -> Unit) {
        onConfirm(title, author)
    }

    override fun offerKeepPartialDownload(sizeBytes: Long, streamLabel: String, onChoice: (Boolean) -> Unit) {
        onChoice(true)
    }

    override fun offerDownloadWithoutMargin(availableBytes: Long, requiredBytesWithoutMargin: Long, onChoice: (Boolean) -> Unit) {
        onChoice(false)
    }

    override fun stopService() {
        context.stopService(Intent(context, DownloadNotificationService::class.java))
    }
}