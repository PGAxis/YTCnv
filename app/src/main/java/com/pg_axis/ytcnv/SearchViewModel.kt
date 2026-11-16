package com.pg_axis.ytcnv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.pg_axis.ytcnv.search_screens.QueueViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

data class SuggestionEntry(val text: String, val isHistory: Boolean)

class SearchViewModel(application: Application) : QueueViewModel(application) {
    var searchQuery by mutableStateOf(TextFieldValue(""))
    var results by mutableStateOf<List<SearchResultItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var endReached by mutableStateOf(false)
    private var extractor: SearchExtractor? = null
    private var nextPage: Page? = null

    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestionJob: Job? = null

    var selectedFilter by mutableStateOf(SearchFilter.VIDEOS)
        private set

    private var activeSearchIsMusic = false

    fun onFilterToggled(filter: SearchFilter) {
        selectedFilter = filter
        onSearch()
    }

    val panelEntries: List<SuggestionEntry>
        get() {
            val q = searchQuery.text.trim()
            val history = if (q.isEmpty()) settings.searchHistory
            else settings.searchHistory.filter { it.contains(q, ignoreCase = true) }
            val seen = history.map { it.lowercase() }.toSet()
            val dynamic = suggestions.filter { it.lowercase() !in seen }
            return history.map { SuggestionEntry(it, true) } + dynamic.map { SuggestionEntry(it, false) }
        }

    fun onQueryChanged(value: TextFieldValue) {
        val textChanged = value.text != searchQuery.text
        searchQuery = value
        if (textChanged) fetchSuggestions(value.text.trim())
    }

    private fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()
        if (query.isEmpty()) {
            suggestions = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(250.milliseconds)
            val result = try {
                withContext(Dispatchers.IO) {
                    ServiceList.YouTube.suggestionExtractor.suggestionList(query)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            suggestions = result
        }
    }

    fun onSearch() {
        if (searchQuery.text.isBlank()) return

        clearSuggestions()
        updateSearchHistory(searchQuery.text.trim())

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                endReached = false
                isLoading = true
                errorMessage = null
                results = emptyList()
            }

            try {
                val filter = selectedFilter
                activeSearchIsMusic = filter.isMusic

                extractor = ServiceList.YouTube.getSearchExtractor(
                    searchQuery.text.trim(),
                    listOf(filter.key),
                    ""
                )
                extractor?.fetchPage()

                val page = extractor?.initialPage
                val newItems = mapItems(page?.items, activeSearchIsMusic)

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

                val newItems = mapItems(page?.items, activeSearchIsMusic)

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
        clearSuggestions()
        searchQuery = TextFieldValue(
            text = query,
            selection = TextRange(query.length)
        )
    }

    fun onSuggestionTapped(text: String) {
        searchQuery = TextFieldValue(text, TextRange(text.length))
        onSearch()
    }

    fun onRemoveHistoryItem(query: String) {
        val updated = settings.searchHistory.toMutableList()
        updated.remove(query)
        settings.searchHistory = updated
    }

    private fun updateSearchHistory(query: String) {
        val updated = settings.searchHistory.toMutableList()
        updated.remove(query)
        updated.add(0, query)
        settings.searchHistory = updated
    }

    private fun clearSuggestions() {
        suggestionJob?.cancel()
        suggestionJob = null
        suggestions = emptyList()
    }
}

