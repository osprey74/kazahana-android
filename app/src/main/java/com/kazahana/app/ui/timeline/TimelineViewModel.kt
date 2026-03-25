package com.kazahana.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.AppJson
import com.kazahana.app.data.bsaf.BsafService
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.model.BsafDuplicateInfo
import com.kazahana.app.data.model.BsafParsedTags
import com.kazahana.app.data.model.BsafRegisteredBot
import com.kazahana.app.data.model.FeedResponse
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.repository.FeedRepository
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.ReportRepository
import com.kazahana.app.data.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
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
    val feeds: List<FeedInfo> = emptyList(),
    val allFeeds: List<FeedInfo> = emptyList(),
    val hiddenFeeds: List<FeedInfo> = emptyList(),
    val selectedFeed: FeedInfo? = null,
    val isFeedsLoading: Boolean = false,
    val showAllInSelector: Boolean = true,
    // BSAF
    val bsafTags: Map<String, BsafParsedTags> = emptyMap(),        // postUri → parsed tags
    val bsafDuplicates: Map<String, BsafDuplicateInfo> = emptyMap(), // postUri → duplicate info
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: TimelineRepository,
    private val interactionRepository: InteractionRepository,
    private val feedRepository: FeedRepository,
    private val reportRepository: ReportRepository,
    private val settingsStore: SettingsStore,
    private val bsafService: BsafService,
    private val client: ATProtoClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    val currentDid: String
        get() = client.session?.did ?: ""

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

                    // Hidden feeds only (for dropdown selector)
                    // Resolve any hidden URIs that were not returned by the API
                    val knownUris = feedInfos.map { it.uri }.toSet()
                    val missingHiddenUris = hiddenURIs.filter { it !in knownUris }
                    for (uri in missingHiddenUris) {
                        try {
                            if (uri.contains("/app.bsky.feed.generator/")) {
                                val genResult = feedRepository.getFeedGenerators(listOf(uri))
                                genResult.getOrNull()?.firstOrNull()?.let { gen ->
                                    feedInfos.add(FeedInfo(id = gen.uri, displayName = gen.displayName, uri = gen.uri, type = "feed"))
                                }
                            } else {
                                val listResult = feedRepository.getListInfo(uri)
                                listResult.getOrNull()?.let { list ->
                                    feedInfos.add(FeedInfo(id = list.uri, displayName = list.name, uri = list.uri, type = "list"))
                                }
                            }
                        } catch (_: Exception) { /* skip unresolvable */ }
                    }
                    val hidden = feedInfos.filter { it.uri != null && it.uri in hiddenURIs }

                    _uiState.update {
                        it.copy(
                            feeds = visibleResult,
                            allFeeds = allResult,
                            hiddenFeeds = hidden,
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

    /** Returns true when the same feed was re-selected (caller should scroll to top). */
    fun selectFeed(feed: FeedInfo): Boolean {
        if (feed == _uiState.value.selectedFeed) {
            refresh()
            return true
        }
        _uiState.update { it.copy(selectedFeed = feed, posts = emptyList(), cursor = null, hasMore = true) }
        loadTimeline()
        return false
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
                    val filtered = processBsaf(response.feed)
                    _uiState.update {
                        it.copy(
                            posts = filtered,
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
                    val filtered = processBsaf(response.feed)
                    _uiState.update {
                        it.copy(
                            posts = filtered,
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
                    val filtered = processBsaf(response.feed, append = true)
                    _uiState.update {
                        it.copy(
                            posts = it.posts + filtered,
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

    fun hidePost(postUri: String) {
        viewModelScope.launch {
            interactionRepository.hidePost(postUri).onSuccess {
                // Remove the post from the list
                _uiState.update { state ->
                    state.copy(posts = state.posts.filter { it.post.uri != postUri })
                }
            }
        }
    }

    fun muteThread(postUri: String, mute: Boolean) {
        viewModelScope.launch {
            val result = if (mute) {
                interactionRepository.muteThread(postUri)
            } else {
                interactionRepository.unmuteThread(postUri)
            }
            result.onSuccess {
                updatePostViewer(postUri) { it.copy(threadMuted = mute) }
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

    fun reportPostAsync(postUri: String, postCid: String, reasonType: String, reason: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(reportRepository.reportPost(postUri, postCid, reasonType, reason))
        }
    }

    fun reportUserAsync(did: String, reasonType: String, reason: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(reportRepository.reportAccount(did, reasonType, reason))
        }
    }

    fun muteUser(did: String) {
        viewModelScope.launch {
            interactionRepository.muteActor(did).onSuccess {
                // Remove all posts by this user from timeline
                _uiState.update { state ->
                    state.copy(posts = state.posts.filter { it.post.author.did != did })
                }
            }
        }
    }

    fun blockUser(did: String) {
        viewModelScope.launch {
            interactionRepository.blockActor(did).onSuccess {
                // Remove all posts by this user from timeline
                _uiState.update { state ->
                    state.copy(posts = state.posts.filter { it.post.author.did != did })
                }
            }
        }
    }

    // ── BSAF processing ──

    private suspend fun processBsaf(
        posts: List<FeedViewPost>,
        append: Boolean = false,
    ): List<FeedViewPost> {
        val bsafEnabled = settingsStore.bsafEnabled.first()
        if (!bsafEnabled) {
            _uiState.update { it.copy(bsafTags = emptyMap(), bsafDuplicates = emptyMap()) }
            return posts
        }

        val registeredBots = settingsStore.bsafRegisteredBots.first()
        val registeredDids = registeredBots.associateBy { it.did }

        val newTagsMap = mutableMapOf<String, BsafParsedTags>()
        val dupGroups = mutableMapOf<String, MutableList<FeedViewPost>>()

        // Parse tags and group duplicates
        for (feedPost in posts) {
            val record = try {
                AppJson.decodeFromJsonElement<PostRecord>(feedPost.post.record)
            } catch (_: Exception) { continue }
            val parsed = bsafService.parseBsafTags(record.tags) ?: continue
            newTagsMap[feedPost.post.uri] = parsed
            val key = bsafService.duplicateKey(parsed)
            dupGroups.getOrPut(key) { mutableListOf() }.add(feedPost)
        }

        // Build duplicate info and determine hidden URIs
        val newDuplicatesMap = mutableMapOf<String, BsafDuplicateInfo>()
        val hiddenUris = mutableSetOf<String>()

        for ((_, group) in dupGroups) {
            if (group.size <= 1) continue
            val primary = group.first()
            val others = group.drop(1)
            newDuplicatesMap[primary.post.uri] = BsafDuplicateInfo(
                duplicateUris = others.map { it.post.uri },
                duplicateHandles = others.map { it.post.author.handle },
            )
            hiddenUris.addAll(others.map { it.post.uri })
        }

        // Filter: hide duplicates + apply registered bot filters
        val filtered = posts.filter { feedPost ->
            if (feedPost.post.uri in hiddenUris) return@filter false
            val parsed = newTagsMap[feedPost.post.uri] ?: return@filter true
            val bot = registeredDids[feedPost.post.author.did] ?: return@filter true
            bsafService.shouldShowBsafPost(parsed, bot)
        }

        // Merge with existing data when appending (loadMore)
        _uiState.update { state ->
            val mergedTags = if (append) state.bsafTags + newTagsMap else newTagsMap
            val mergedDups = if (append) state.bsafDuplicates + newDuplicatesMap else newDuplicatesMap
            state.copy(bsafTags = mergedTags, bsafDuplicates = mergedDups)
        }
        return filtered
    }
}
