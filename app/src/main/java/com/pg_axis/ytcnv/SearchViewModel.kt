package com.pg_axis.ytcnv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pg_axis.ytcnv.settings.ISettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class SearchViewModel(val settings: ISettings) : ViewModel() {

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

data class SearchResultItem(
    val title: String,
    val videoId: String,
    val uploader: String,
    val duration: String,
    val thumbnailUrl: String,
    val url: String
)
