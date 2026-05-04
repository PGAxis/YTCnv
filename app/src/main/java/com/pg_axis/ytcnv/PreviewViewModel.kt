package com.pg_axis.ytcnv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

class PreviewViewModel(val videoId: String) : ViewModel() {

    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var videoTitle by mutableStateOf("")
    var savedPositionMs by mutableLongStateOf(0L)
    var videoUrl by mutableStateOf<String?>(null)
    var audioUrl by mutableStateOf<String?>(null)
    var isMerged by mutableStateOf(false)

    init { fetchStreams() }

    private fun fetchStreams() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=$videoId"
                )
                videoTitle = info.name

                val videoOnlyStream = info.videoOnlyStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .filter { it.height <= 480 &&
                            it.format?.name?.contains("mpeg-4", ignoreCase = true) == true }
                    .maxByOrNull { it.height }

                val audioOnlyStream = info.audioStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .maxByOrNull { it.averageBitrate }

                if (videoOnlyStream != null && audioOnlyStream != null) {
                    videoUrl = videoOnlyStream.content
                    audioUrl = audioOnlyStream.content
                    isMerged = true
                } else {
                    // Fallback: best combined progressive stream, capped at 1080p
                    val combined = info.videoStreams
                        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                        .filter { it.height <= 480 &&
                                it.format?.name?.contains("mpeg-4", ignoreCase = true) == true }
                        .maxByOrNull { it.height }
                        ?: info.videoStreams
                            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                            .maxByOrNull { it.height }
                    videoUrl = combined?.content
                    isMerged = false
                }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }
}