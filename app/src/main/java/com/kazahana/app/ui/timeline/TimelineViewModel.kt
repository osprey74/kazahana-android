package com.kazahana.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.model.FeedResponse
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.repository.FeedRepository
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedInfo(
    val id: String,
    val displayName: String,
    val uri: String?,       // null for "Following" (default timeline)
    val type: String,       // "timeline", "feed", "list"
    @androidx.annotation.StringRes val labelRes: Int? = null,  // optional string resource override
)

data class TimelineUiState(
    val posts: List<FeedViewPost> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val feeds: List<FeedInfo> = emptyList(),         // visible feeds (tab bar)
    val allFeeds: List<FeedInfo> = emptyList(),       // all feeds including hidden (dropdown)
    val selectedFeed: FeedInfo? = null,
    val isFeedsLoading: Boolean = false,
    val showAllInSelector: Boolean = true,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: TimelineRepository,
    private val interactionRepository: InteractionRepository,
    private val feedRepository: FeedRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        loadTimeline()
        loadSavedFeeds()
        viewModelScope.launch {
            settingsStore.showAllFeedsInSelector.collect { enabled ->
                _uiState.update { it.copy(showAllInSelector = enabled) }
            }
        }
    }

    fun loadSavedFeeds() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFeedsLoading = true) }
            feedRepository.getAllSavedFeedItems()
                .onSuccess { result ->
                    val feedInfos = mutableListOf<FeedInfo>()

                    // Map feeds (display names already resolved by getAllSavedFeedItems)
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

                    // Map lists (display names already resolved by getAllSavedFeedItems)
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

                    // Apply visibility and order settings from local store
                    val hiddenURIs = settingsStore.hiddenFeedURIs.first().toSet()
                    val pinnedURIs = settingsStore.pinnedFeedURIs.first()

                    val visible = feedInfos.filter { it.uri == null || it.uri !in hiddenURIs }

                    val sorted = if (pinnedURIs.isNotEmpty()) {
                        val orderMap = pinnedURIs.withIndex().associate { (i, uri) -> uri to i }
                        visible.sortedBy { orderMap[it.uri] ?: Int.MAX_VALUE }
                    } else {
                        visible
                    }

                    // "Following" is always first
                    val following = FeedInfo(
                        id = "following",
                        displayName = "Following",
                        uri = null,
                        type = "timeline",
                        labelRes = com.kazahana.app.R.string.feed_following,
                    )
                    val visibleResult = listOf(following) + sorted

                    // All feeds (for dropdown when showAllInSelector is on)
                    val allSorted = if (pinnedURIs.isNotEmpty()) {
                        val orderMap = pinnedURIs.withIndex().associate { (i, uri) -> uri to i }
                        feedInfos.sortedBy { orderMap[it.uri] ?: Int.MAX_VALUE }
                    } else {
                        feedInfos
                    }
                    val allResult = listOf(following) + allSorted

                    _uiState.update {
                        it.copy(
                            feeds = visibleResult,
                            allFeeds = allResult,
                            selectedFeed = it.selectedFeed ?: visibleResult.first(),
                            isFeedsLoading = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isFeedsLoading = false) }
                }
        }
    }

    fun selectFeed(feed: FeedInfo) {
        if (feed == _uiState.value.selectedFeed) return
        _uiState.update { it.copy(selectedFeed = feed, posts = emptyList(), cursor = null, hasMore = true) }
        loadTimeline()
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val feed = _uiState.value.selectedFeed
            val result: Result<FeedResponse> = when {
                feed == null || feed.uri == null ->
                    repository.getTimeline().map { FeedResponse(feed = it.feed, cursor = it.cursor) }
                feed.type == "list" -> feedRepository.getListFeed(feed.uri)
                else -> feedRepository.getFeed(feed.uri)
            }
            result
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            posts = response.feed,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            val feed = _uiState.value.selectedFeed
            val result: Result<FeedResponse> = when {
                feed == null || feed.uri == null ->
                    repository.getTimeline().map { FeedResponse(feed = it.feed, cursor = it.cursor) }
                feed.type == "list" -> feedRepository.getListFeed(feed.uri)
                else -> feedRepository.getFeed(feed.uri)
            }
            result
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            posts = response.feed,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRefreshing = false, error = e.message) }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.cursor == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val feed = state.selectedFeed
            val result: Result<FeedResponse> = when {
                feed == null || feed.uri == null ->
                    repository.getTimeline(cursor = state.cursor).map { FeedResponse(feed = it.feed, cursor = it.cursor) }
                feed.type == "list" -> feedRepository.getListFeed(feed.uri, cursor = state.cursor)
                else -> feedRepository.getFeed(feed.uri, cursor = state.cursor)
            }
            result
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            posts = it.posts + response.feed,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    fun toggleLike(postUri: String, postCid: String, currentLikeUri: String?) {
        viewModelScope.launch {
            if (currentLikeUri != null) {
                interactionRepository.unlike(currentLikeUri).onSuccess {
                    updatePostViewer(postUri) { viewer ->
                        viewer.copy(like = null)
                    }
                    updatePostCount(postUri) { it.copy(likeCount = (it.likeCount ?: 1) - 1) }
                }
            } else {
                interactionRepository.like(postUri, postCid).onSuccess { response ->
                    updatePostViewer(postUri) { viewer ->
                        viewer.copy(like = response.uri)
                    }
                    updatePostCount(postUri) { it.copy(likeCount = (it.likeCount ?: 0) + 1) }
                }
            }
        }
    }

    fun toggleRepost(postUri: String, postCid: String, currentRepostUri: String?) {
        viewModelScope.launch {
            if (currentRepostUri != null) {
                interactionRepository.unrepost(currentRepostUri).onSuccess {
                    updatePostViewer(postUri) { viewer ->
                        viewer.copy(repost = null)
                    }
                    updatePostCount(postUri) { it.copy(repostCount = (it.repostCount ?: 1) - 1) }
                }
            } else {
                interactionRepository.repost(postUri, postCid).onSuccess { response ->
                    updatePostViewer(postUri) { viewer ->
                        viewer.copy(repost = response.uri)
                    }
                    updatePostCount(postUri) { it.copy(repostCount = (it.repostCount ?: 0) + 1) }
                }
            }
        }
    }

    fun toggleBookmark(postUri: String, postCid: String, currentBookmarkUri: String?) {
        viewModelScope.launch {
            if (currentBookmarkUri != null) {
                interactionRepository.unbookmark(currentBookmarkUri).onSuccess {
                    updatePostViewer(postUri) { viewer ->
                        viewer.copy(bookmark = null)
                    }
                }
            } else {
                interactionRepository.bookmark(postUri, postCid).onSuccess { response ->
                    updatePostViewer(postUri) { viewer ->
                        viewer.copy(bookmark = response.uri)
                    }
                }
            }
        }
    }

    private fun updatePostViewer(postUri: String, transform: (PostViewerState) -> PostViewerState) {
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map { feedPost ->
                    if (feedPost.post.uri == postUri) {
                        feedPost.copy(
                            post = feedPost.post.copy(
                                viewer = transform(feedPost.post.viewer ?: PostViewerState())
                            )
                        )
                    } else feedPost
                }
            )
        }
    }

    private fun updatePostCount(postUri: String, transform: (com.kazahana.app.data.model.PostView) -> com.kazahana.app.data.model.PostView) {
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map { feedPost ->
                    if (feedPost.post.uri == postUri) {
                        feedPost.copy(post = transform(feedPost.post))
                    } else feedPost
                }
            )
        }
    }
}
