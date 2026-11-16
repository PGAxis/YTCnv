package com.pg_axis.ytcnv.search_screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pg_axis.ytcnv.PlaylistResultItem
import com.pg_axis.ytcnv.SearchResultItem
import com.pg_axis.ytcnv.mapItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

class PlaylistViewModel(val playlistId: String, application: Application) : QueueViewModel(application) {

    var name by mutableStateOf("")
        private set
    var uploader by mutableStateOf("")
        private set
    var uploaderChannelId by mutableStateOf<String?>(null)
        private set
    var headerLoading by mutableStateOf(true)
        private set

    var items by mutableStateOf<List<SearchResultItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var endReached by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
    private var nextPage: Page? = null
    private var loaded = false

    val asResultItem: PlaylistResultItem
        get() = PlaylistResultItem(
            name = name,
            itemId = playlistId,
            uploader = uploader,
            imageUrl = "",
            url = playlistUrl,
            uploaderId = uploaderChannelId
        )

    init { loadMore() }

    fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        error = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (raw, next) = if (!loaded) {
                    val info = PlaylistInfo.getInfo(ServiceList.YouTube, playlistUrl)
                    withContext(Dispatchers.Main) {
                        name = info.name
                        uploader = info.uploaderName ?: ""
                        uploaderChannelId = info.uploaderUrl
                            ?.takeIf { it.isNotBlank() }
                            ?.substringAfterLast("/")?.substringBefore("?")
                        headerLoading = false
                    }
                    info.relatedItems to info.nextPage
                } else {
                    val page = PlaylistInfo.getMoreItems(ServiceList.YouTube, playlistUrl, nextPage)
                    page.items to page.nextPage
                }
                val mapped = mapItems(raw, isMusic = false)
                withContext(Dispatchers.Main) {
                    items += mapped
                    nextPage = next
                    endReached = next == null
                    loaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: e.toString()
                    headerLoading = false
                }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    companion object {
        fun factory(playlistId: String) = viewModelFactory {
            initializer {
                PlaylistViewModel(playlistId, this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }
}