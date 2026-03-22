package com.kazahana.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.model.SavedFeedItem
import com.kazahana.app.data.repository.FeedRepository
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedInfo(
    val id: String,
    val displayName: String,
    val uri: String?,       // null for "Following" (default timeline)
    val type: String,       // "timeline", "feed", "list"
)

data class TimelineUiState(
    val posts: List<FeedViewPost> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val feeds: List<FeedInfo> = emptyList(),
    val selectedFeed: FeedInfo? = null,
    val isFeedsLoading: Boolean = false,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: TimelineRepository,
    private val interactionRepository: InteractionRepository,
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        // Load default timeline first, then load feed list in background
        loadTimeline()
        loadSavedFeeds()
    }

    private fun loadSavedFeeds() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFeedsLoading = true) }
            feedRepository.getSavedFeeds()
                .onSuccess { savedFeeds ->
                    val feedInfos = mutableListOf(
                        FeedInfo(
                            id = "following",
                            displayName = "Following",
                            uri = null,
                            type = "timeline",
                        )
                    )
                    savedFeeds.forEach { item ->
                        when (item.type) {
                            "feed" -> feedInfos.add(
                                FeedInfo(
                                    id = item.id,
                                    displayName = item.value.substringAfterLast("/"),
                                    uri = item.value,
                                    type = "feed",
                                )
                            )
                            "list" -> feedInfos.add(
                                FeedInfo(
                                    id = item.id,
                                    displayName = item.value.substringAfterLast("/"),
                                    uri = item.value,
                                    type = "list",
                                )
                            )
                        }
                    }

                    // Resolve feed names
                    val feedUris = feedInfos.filter { it.type == "feed" && it.uri != null }.mapNotNull { it.uri }
                    if (feedUris.isNotEmpty()) {
                        feedRepository.getFeedGenerators(feedUris)
                            .onSuccess { generators ->
                                val nameMap = generators.associate { it.uri to it.displayName }
                                val resolved = feedInfos.map { info ->
                                    if (info.type == "feed" && info.uri != null && nameMap.containsKey(info.uri)) {
                                        info.copy(displayName = nameMap[info.uri]!!)
                                    } else info
                                }
                                _uiState.update { it.copy(feeds = resolved, selectedFeed = resolved.first(), isFeedsLoading = false) }
                            }
                            .onFailure {
                                _uiState.update { it.copy(feeds = feedInfos, selectedFeed = feedInfos.first(), isFeedsLoading = false) }
                            }
                    } else {
                        _uiState.update { it.copy(feeds = feedInfos, selectedFeed = feedInfos.first(), isFeedsLoading = false) }
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
            if (feed == null || feed.uri == null) {
                // Default following timeline
                repository.getTimeline()
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
            } else {
                // Custom feed
                feedRepository.getFeed(feed.uri)
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
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            val feed = _uiState.value.selectedFeed
            if (feed == null || feed.uri == null) {
                repository.getTimeline()
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
            } else {
                feedRepository.getFeed(feed.uri)
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
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.cursor == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val feed = state.selectedFeed
            if (feed == null || feed.uri == null) {
                repository.getTimeline(cursor = state.cursor)
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
            } else {
                feedRepository.getFeed(feed.uri, cursor = state.cursor)
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
