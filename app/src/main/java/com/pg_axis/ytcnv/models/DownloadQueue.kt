package com.pg_axis.ytcnv.models

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arthenica.ffmpegkit.FFmpegKit
import com.pg_axis.ytcnv.PopupType
import com.pg_axis.ytcnv.settings.SettingsSave
import com.pg_axis.ytcnv.utils.DownloadNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DownloadQueue {

    enum class Phase { IDLE, RETRIEVING, DOWNLOADING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var videoQueue by mutableStateOf<List<Video>>(emptyList())
        private set

    var activeVideo by mutableStateOf<Video?>(null)
        private set

    var phase by mutableStateOf(Phase.IDLE)
        private set

    private var queueRunning = false
    private var activeJob: Job? = null

    fun enqueue(context: Context, video: Video, callbacks: VideoCallbacks) {
        video.attachCallbacks(QueueCallbacks(callbacks))
        videoQueue = videoQueue + video
        processQueue(context.applicationContext)
    }

    /**
     * Downloads videoQueue[0]; when it completes (or fails / is canceled) it is removed and the next
     * one starts. Videos added while this is running are picked up by the same loop.
     */
    private fun processQueue(appContext: Context) {
        if (queueRunning) return
        queueRunning = true
        val settings = SettingsSave.getInstance(appContext)
        settings.isDownloadRunning = true

        scope.launch {
            try {
                while (videoQueue.isNotEmpty()) {
                    val next = videoQueue.first()
                    activeVideo = next

                    val job = launch { next.download() }
                    activeJob = job
                    job.join()
                    activeJob = null

                    videoQueue = videoQueue.filter { it !== next }
                    phase = Phase.IDLE
                }
            } finally {
                activeJob = null
                activeVideo = null
                phase = Phase.IDLE
                queueRunning = false
                settings.isDownloadRunning = false
                appContext.stopService(Intent(appContext, DownloadNotificationService::class.java))
            }
        }
    }

    /**
     * Moves the waiting video [fromUid] to the position of the waiting video [toUid].
     * The video being downloaded can neither be moved nor be displaced.
     */
    fun move(fromUid: Long, toUid: Long) {
        val list = videoQueue.toMutableList()
        val from = list.indexOfFirst { it.uid == fromUid }
        val to = list.indexOfFirst { it.uid == toUid }
        if (from < 0 || to < 0 || from == to) return
        if (list[from] === activeVideo || list[to] === activeVideo) return
        list.add(to, list.removeAt(from))
        videoQueue = list
    }

    /** Removes a WAITING video; the one being downloaded is ignored (use [cancelActive]). */
    fun remove(video: Video) {
        if (video === activeVideo) return
        videoQueue = videoQueue.filter { it !== video }
    }

    /** Cancels the ACTIVE video only; the queue then continues with the next one. */
    fun cancelActive() {
        FFmpegKit.cancel()
        activeJob?.cancel()
    }

    private class QueueCallbacks(private val delegate: VideoCallbacks) : VideoCallbacks {
        override fun onLoadStarted() = delegate.onLoadStarted()
        override fun onLoadFinished() = delegate.onLoadFinished()

        override fun onDownloadStarted() {
            phase = Phase.RETRIEVING
            delegate.onDownloadStarted()
        }

        override fun onDownloadInProgress() {
            phase = Phase.DOWNLOADING
            delegate.onDownloadInProgress()
        }

        override fun onDownloadFinished(savedUri: Uri?, isAudio: Boolean) = delegate.onDownloadFinished(savedUri, isAudio)
        override fun onFailed() = delegate.onFailed()
        override fun onCanceled() = delegate.onCanceled()
        override fun showPopup(title: String, message: String, type: PopupType) = delegate.showPopup(title, message, type)

        override fun offerKeepPartialDownload(sizeBytes: Long, streamLabel: String, onChoice: (Boolean) -> Unit) =
            delegate.offerKeepPartialDownload(sizeBytes, streamLabel, onChoice)

        override fun offerDownloadWithoutMargin(availableBytes: Long, requiredBytesWithoutMargin: Long, onChoice: (Boolean) -> Unit) =
            delegate.offerDownloadWithoutMargin(availableBytes, requiredBytesWithoutMargin, onChoice)

        override fun stopService() {}
    }
}