package com.pg_axis.ytcnv.search_screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pg_axis.ytcnv.SearchResultItem
import com.pg_axis.ytcnv.mapItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import java.text.Normalizer

enum class ChannelTab { VIDEOS, PLAYLISTS, SEARCH }

class TabContent {
    var items by mutableStateOf<List<SearchResultItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var endReached by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var handle: ListLinkHandler? = null
    var nextPage: Page? = null
    var loaded = false
}

class ChannelViewModel(channelId: String, application: Application) : QueueViewModel(application) {

    var name by mutableStateOf("")
        private set
    var subscribers by mutableStateOf<Long?>(null)
        private set
    var avatarUrl by mutableStateOf<String?>(null)
        private set
    var bannerUrl by mutableStateOf<String?>(null)
        private set
    var headerLoading by mutableStateOf(true)
        private set
    var headerError by mutableStateOf<String?>(null)
        private set

    var selectedTab by mutableStateOf(ChannelTab.VIDEOS)
        private set

    val tabs = mapOf(ChannelTab.VIDEOS to TabContent(), ChannelTab.PLAYLISTS to TabContent())

    private val channelUrl = if (channelId.startsWith("@")) "https://www.youtube.com/$channelId"
    else "https://www.youtube.com/channel/$channelId"

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
                info.tabs.forEach { tab ->
                    when {
                        tab.contentFilters.contains(ChannelTabs.VIDEOS) -> tabs[ChannelTab.VIDEOS]?.handle = tab
                        tab.contentFilters.contains(ChannelTabs.PLAYLISTS) -> tabs[ChannelTab.PLAYLISTS]?.handle = tab
                    }
                }
                withContext(Dispatchers.Main) {
                    name = info.name
                    subscribers = info.subscriberCount.takeIf { it >= 0 }
                    avatarUrl = info.avatars.maxByOrNull { it.height }?.url
                    bannerUrl = info.banners.maxByOrNull { it.height }?.url
                    headerLoading = false
                }
                loadMore(ChannelTab.VIDEOS)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    headerError = e.message ?: e.toString()
                    headerLoading = false
                }
            }
        }
    }

    fun onTabSelected(tab: ChannelTab) {
        selectedTab = tab
        if (tabs[tab]?.loaded == false) loadMore(tab)
    }

    fun loadMore(tab: ChannelTab) {
        val t = tabs[tab] ?: return
        val handle = t.handle ?: return
        if (t.isLoading || t.endReached) return
        t.isLoading = true
        t.error = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (raw, next) = if (!t.loaded) {
                    val i = ChannelTabInfo.getInfo(ServiceList.YouTube, handle)
                    i.relatedItems to i.nextPage
                } else {
                    val p = t.nextPage?.let { ChannelTabInfo.getMoreItems(ServiceList.YouTube, handle, it) }
                    p?.items to p?.nextPage
                }
                val mapped = mapItems(raw, isMusic = false)
                withContext(Dispatchers.Main) {
                    t.items += mapped
                    t.nextPage = next
                    t.endReached = next == null
                    t.loaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { t.error = e.message ?: e.toString() }
            } finally {
                withContext(Dispatchers.Main) { t.isLoading = false }
            }
        }
    }

    /** Per-channel search */
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<SearchResultItem>>(emptyList())
        private set
    var searchScanning by mutableStateOf(false)
        private set
    var searchScanned by mutableIntStateOf(0)
        private set
    var searchDone by mutableStateOf(false)
        private set
    var searchStarted by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null
    private var searchGen = 0
    private var searchNextPage: Page? = null
    private var searchFirstPageDone = false
    private var searchTerms: List<String> = emptyList()

    fun onSearchQueryChanged(value: String) {
        searchQuery = value
    }

    fun onChannelSearch() {
        val terms = normalize(searchQuery).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return

        searchJob?.cancel()
        searchGen++
        searchTerms = terms
        searchResults = emptyList()
        searchScanned = 0
        searchDone = false
        searchError = null
        searchStarted = true
        searchScanning = false
        searchNextPage = null
        searchFirstPageDone = false
        scanMore()
    }

    /** Scans until [SEARCH_BATCH] new matches are found or the channel ends. Also used by "Search further". */
    fun scanMore() {
        if (searchScanning || searchDone) return
        val handle = tabs[ChannelTab.VIDEOS]?.handle
        if (handle == null) {
            searchError = headerError ?: "Videos tab not available"
            return
        }
        val gen = searchGen
        searchScanning = true
        searchError = null

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            var found = 0
            try {
                while (found < SEARCH_BATCH && isActive) {
                    val (raw, next) = if (!searchFirstPageDone) {
                        val i = ChannelTabInfo.getInfo(ServiceList.YouTube, handle)
                        i.relatedItems to i.nextPage
                    } else {
                        val p = searchNextPage?.let { ChannelTabInfo.getMoreItems(ServiceList.YouTube, handle, it) }
                        p?.items to p?.nextPage
                    }
                    searchFirstPageDone = true
                    searchNextPage = next

                    val mapped = mapItems(raw, isMusic = false)
                    val matches = mapped.filter { item ->
                        val title = normalize(item.name)
                        searchTerms.all { title.contains(it) }
                    }
                    found += matches.size

                    withContext(Dispatchers.Main) {
                        if (gen != searchGen) return@withContext
                        searchResults += matches
                        searchScanned += mapped.size
                        if (next == null) searchDone = true
                    }
                    if (next == null) break
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (gen == searchGen) searchError = e.message ?: e.toString()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (gen == searchGen) searchScanning = false
                }
            }
        }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()

    companion object {
        fun factory(channelId: String) = viewModelFactory {
            initializer {
                ChannelViewModel(channelId, this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }
}

private const val SEARCH_BATCH = 10