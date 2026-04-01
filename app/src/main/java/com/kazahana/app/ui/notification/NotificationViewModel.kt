package com.kazahana.app.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.NotificationItem
import com.kazahana.app.data.model.PostView
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.model.ProfileViewBasic
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A grouped notification representing one or more notifications of the same type
 * targeting the same post. For like/repost, multiple users are grouped together.
 */
data class NotificationGroup(
    val reason: String,
    val reasonSubject: String?,
    val authors: List<ProfileViewBasic>,
    val indexedAt: String,
    val isRead: Boolean,
    /** The first notification's URI (used as key) */
    val uri: String,
    /** The first notification's record (for reply/mention/quote text) */
    val record: kotlinx.serialization.json.JsonElement,
)

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val groupedNotifications: List<NotificationGroup> = emptyList(),
    val subjectPosts: Map<String, PostView> = emptyMap(),
    val resolvedRepostURIs: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val interactionRepository: InteractionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private var hasLoadedNotifications = false

    init {
        loadUnreadCount()
    }

    fun ensureLoaded() {
        if (!hasLoadedNotifications) {
            hasLoadedNotifications = true
            loadNotifications()
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.listNotifications()
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            notifications = response.notifications,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoading = false,
                        )
                    }
                    updateGroupedNotifications()
                    fetchSubjectPosts(response.notifications)
                    repository.updateSeen()
                    _unreadCount.value = 0
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            repository.listNotifications()
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            notifications = response.notifications,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isRefreshing = false,
                            subjectPosts = emptyMap(),
                            resolvedRepostURIs = emptyMap(),
                        )
                    }
                    updateGroupedNotifications()
                    fetchSubjectPosts(response.notifications)
                    repository.updateSeen()
                    _unreadCount.value = 0
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

            repository.listNotifications(cursor = state.cursor)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            notifications = it.notifications + response.notifications,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoadingMore = false,
                        )
                    }
                    updateGroupedNotifications()
                    fetchSubjectPosts(response.notifications)
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            repository.getUnreadCount()
                .onSuccess { count -> _unreadCount.value = count }
        }
    }

    /**
     * Group notifications by (reasonSubject + reason) for like/repost types.
     * reply/mention/quote/follow remain as individual items.
     */
    private fun groupNotifications(notifications: List<NotificationItem>): List<NotificationGroup> {
        val groups = mutableListOf<NotificationGroup>()
        val groupableReasons = setOf("like", "repost", "like-via-repost", "repost-via-repost")

        // Use a map to aggregate groupable notifications by key
        val groupMap = linkedMapOf<String, MutableList<NotificationItem>>()
        val nonGroupable = mutableListOf<NotificationItem>()

        for (notif in notifications) {
            if (notif.reason in groupableReasons && notif.reasonSubject != null) {
                val key = "${notif.reason}::${notif.reasonSubject}"
                groupMap.getOrPut(key) { mutableListOf() }.add(notif)
            } else {
                nonGroupable.add(notif)
            }
        }

        // Merge into a single ordered list maintaining relative order by first occurrence
        val processed = mutableSetOf<String>()
        for (notif in notifications) {
            if (notif.reason in groupableReasons && notif.reasonSubject != null) {
                val key = "${notif.reason}::${notif.reasonSubject}"
                if (key in processed) continue
                processed.add(key)
                val items = groupMap[key]!!
                groups.add(
                    NotificationGroup(
                        reason = items.first().reason,
                        reasonSubject = items.first().reasonSubject,
                        authors = items.map { it.author },
                        indexedAt = items.first().indexedAt,
                        isRead = items.all { it.isRead },
                        uri = items.first().uri,
                        record = items.first().record,
                    )
                )
            } else {
                groups.add(
                    NotificationGroup(
                        reason = notif.reason,
                        reasonSubject = notif.reasonSubject,
                        authors = listOf(notif.author),
                        indexedAt = notif.indexedAt,
                        isRead = notif.isRead,
                        uri = notif.uri,
                        record = notif.record,
                    )
                )
            }
        }

        return groups
    }

    private fun updateGroupedNotifications() {
        _uiState.update { state ->
            state.copy(groupedNotifications = groupNotifications(state.notifications))
        }
    }

    /** Fetch subject posts for notifications, resolving repost URIs when needed */
    private suspend fun fetchSubjectPosts(notifications: List<NotificationItem>) {
        val currentState = _uiState.value

        // Step 1: Resolve like-via-repost / repost-via-repost URIs
        val viaRepostNotifs = notifications.filter { notif ->
            notif.reason in listOf("like-via-repost", "repost-via-repost") &&
                notif.reasonSubject != null &&
                notif.reasonSubject !in currentState.resolvedRepostURIs
        }
        for (notif in viaRepostNotifs) {
            val repostUri = notif.reasonSubject ?: continue
            resolveRepostURI(repostUri)?.let { postUri ->
                _uiState.update {
                    it.copy(resolvedRepostURIs = it.resolvedRepostURIs + (repostUri to postUri))
                }
            }
        }

        // Step 2: Collect post URIs to fetch
        val resolved = _uiState.value.resolvedRepostURIs
        val uris = notifications.mapNotNull { notification ->
            when (notification.reason) {
                "like", "repost" -> notification.reasonSubject
                "like-via-repost", "repost-via-repost" ->
                    notification.reasonSubject?.let { resolved[it] }
                "reply", "mention", "quote" -> notification.uri
                else -> null
            }
        }.distinct().filter { it !in _uiState.value.subjectPosts }

        if (uris.isEmpty()) return

        // getPosts supports up to 25 URIs per call
        uris.chunked(25).forEach { chunk ->
            repository.getPosts(chunk)
                .onSuccess { posts ->
                    val newPosts = posts.associateBy { it.uri }
                    _uiState.update {
                        it.copy(subjectPosts = it.subjectPosts + newPosts)
                    }
                }
        }
    }

    /** Resolve a repost record URI to the original post URI */
    private suspend fun resolveRepostURI(repostUri: String): String? {
        val regex = Regex("""^at://([^/]+)/app\.bsky\.feed\.repost/(.+)$""")
        val match = regex.matchEntire(repostUri) ?: return null
        val repo = match.groupValues[1]
        val rkey = match.groupValues[2]

        return repository.getRecord(repo, "app.bsky.feed.repost", rkey)
            .getOrNull()
            ?.value
            ?.subject
            ?.uri
    }

    fun toggleLike(postUri: String, postCid: String, currentLikeUri: String?) {
        viewModelScope.launch {
            if (currentLikeUri != null) {
                interactionRepository.unlike(currentLikeUri).onSuccess {
                    updateSubjectPostViewer(postUri) { it.copy(like = null) }
                    updateSubjectPostCount(postUri) { it.copy(likeCount = (it.likeCount ?: 1) - 1) }
                }
            } else {
                interactionRepository.like(postUri, postCid).onSuccess { response ->
                    updateSubjectPostViewer(postUri) { it.copy(like = response.uri) }
                    updateSubjectPostCount(postUri) { it.copy(likeCount = (it.likeCount ?: 0) + 1) }
                }
            }
        }
    }

    fun toggleRepost(postUri: String, postCid: String, currentRepostUri: String?) {
        viewModelScope.launch {
            if (currentRepostUri != null) {
                interactionRepository.unrepost(currentRepostUri).onSuccess {
                    updateSubjectPostViewer(postUri) { it.copy(repost = null) }
                    updateSubjectPostCount(postUri) { it.copy(repostCount = (it.repostCount ?: 1) - 1) }
                }
            } else {
                interactionRepository.repost(postUri, postCid).onSuccess { response ->
                    updateSubjectPostViewer(postUri) { it.copy(repost = response.uri) }
                    updateSubjectPostCount(postUri) { it.copy(repostCount = (it.repostCount ?: 0) + 1) }
                }
            }
        }
    }

    fun toggleBookmark(postUri: String, postCid: String, currentBookmarkUri: String?) {
        viewModelScope.launch {
            if (currentBookmarkUri != null) {
                interactionRepository.unbookmark(currentBookmarkUri).onSuccess {
                    updateSubjectPostViewer(postUri) { it.copy(bookmark = null) }
                }
            } else {
                interactionRepository.bookmark(postUri, postCid).onSuccess { response ->
                    updateSubjectPostViewer(postUri) { it.copy(bookmark = response.uri) }
                }
            }
        }
    }

    private fun updateSubjectPostViewer(postUri: String, transform: (PostViewerState) -> PostViewerState) {
        _uiState.update { state ->
            val post = state.subjectPosts[postUri] ?: return
            state.copy(
                subjectPosts = state.subjectPosts + (postUri to post.copy(
                    viewer = transform(post.viewer ?: PostViewerState())
                ))
            )
        }
    }

    private fun updateSubjectPostCount(postUri: String, transform: (PostView) -> PostView) {
        _uiState.update { state ->
            val post = state.subjectPosts[postUri] ?: return
            state.copy(
                subjectPosts = state.subjectPosts + (postUri to transform(post))
            )
        }
    }
}
