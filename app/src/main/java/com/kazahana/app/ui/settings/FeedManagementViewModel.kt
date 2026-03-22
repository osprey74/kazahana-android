package com.kazahana.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.repository.FeedRepository
import com.kazahana.app.ui.timeline.FeedInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedManagementUiState(
    val visibleFeeds: List<FeedInfo> = emptyList(),
    val hiddenFeeds: List<FeedInfo> = emptyList(),
    val showAllInSelector: Boolean = true,
    val isLoading: Boolean = false,
)

@HiltViewModel
class FeedManagementViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedManagementUiState())
    val uiState: StateFlow<FeedManagementUiState> = _uiState.asStateFlow()

    private var allFeeds: List<FeedInfo> = emptyList()

    init {
        loadFeeds()
        viewModelScope.launch {
            settingsStore.showAllFeedsInSelector.collect { enabled ->
                _uiState.update { it.copy(showAllInSelector = enabled) }
            }
        }
    }

    private fun loadFeeds() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            feedRepository.getAllSavedFeedItems()
                .onSuccess { result ->
                    val feedInfos = mutableListOf<FeedInfo>()

                    // Map feeds (display names already resolved)
                    result.feeds.forEach { gen ->
                        feedInfos.add(
                            FeedInfo(
                                id = gen.uri,
                                displayName = gen.displayName,
                                uri = gen.uri,
                                type = "feed",
                            )
                        )
                    }

                    // Map lists (display names already resolved)
                    result.lists.forEach { list ->
                        feedInfos.add(
                            FeedInfo(
                                id = list.uri,
                                displayName = list.name,
                                uri = list.uri,
                                type = "list",
                            )
                        )
                    }

                    allFeeds = feedInfos
                    applySettings()
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    private suspend fun applySettings() {
        val pinnedURIs = settingsStore.pinnedFeedURIs.first()
        val hiddenURIs = settingsStore.hiddenFeedURIs.first().toSet()

        val visible = mutableListOf<FeedInfo>()
        val hidden = mutableListOf<FeedInfo>()

        val sorted = if (pinnedURIs.isNotEmpty()) {
            val orderMap = pinnedURIs.withIndex().associate { (i, uri) -> uri to i }
            allFeeds.sortedBy { orderMap[it.uri] ?: Int.MAX_VALUE }
        } else {
            allFeeds
        }

        sorted.forEach { feed ->
            if (feed.uri != null && feed.uri in hiddenURIs) {
                hidden.add(feed)
            } else {
                visible.add(feed)
            }
        }

        _uiState.update {
            it.copy(
                visibleFeeds = visible,
                hiddenFeeds = hidden,
                isLoading = false,
            )
        }
    }

    fun setShowAllInSelector(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setShowAllFeedsInSelector(enabled)
        }
    }

    fun toggleVisibility(feed: FeedInfo) {
        viewModelScope.launch {
            val currentHidden = settingsStore.hiddenFeedURIs.first().toMutableList()
            val uri = feed.uri ?: return@launch

            if (uri in currentHidden) {
                currentHidden.remove(uri)
            } else {
                currentHidden.add(uri)
            }
            settingsStore.setHiddenFeedURIs(currentHidden)
            applySettings()
            saveOrder()
        }
    }

    fun moveUp(feed: FeedInfo) {
        val visible = _uiState.value.visibleFeeds.toMutableList()
        val index = visible.indexOf(feed)
        if (index <= 0) return
        visible.removeAt(index)
        visible.add(index - 1, feed)
        _uiState.update { it.copy(visibleFeeds = visible) }
        saveOrder()
    }

    fun moveDown(feed: FeedInfo) {
        val visible = _uiState.value.visibleFeeds.toMutableList()
        val index = visible.indexOf(feed)
        if (index < 0 || index >= visible.size - 1) return
        visible.removeAt(index)
        visible.add(index + 1, feed)
        _uiState.update { it.copy(visibleFeeds = visible) }
        saveOrder()
    }

    private fun saveOrder() {
        viewModelScope.launch {
            val state = _uiState.value
            val allOrdered = state.visibleFeeds + state.hiddenFeeds
            val uris = allOrdered.mapNotNull { it.uri }
            settingsStore.setPinnedFeedURIs(uris)
        }
    }
}
