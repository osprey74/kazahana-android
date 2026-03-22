package com.kazahana.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.model.PostView
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.model.ProfileViewDetailed
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import com.kazahana.app.R
import javax.inject.Inject

enum class SearchTab(@StringRes val labelRes: Int) {
    POSTS(R.string.search_posts),
    USERS(R.string.search_users),
}

data class SearchUiState(
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.POSTS,
    val posts: List<PostView> = emptyList(),
    val users: List<ProfileViewDetailed> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val postsCursor: String? = null,
    val usersCursor: String? = null,
    val hasMorePosts: Boolean = false,
    val hasMoreUsers: Boolean = false,
    val hasSearched: Boolean = false,
    val searchHistory: List<String> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val interactionRepository: InteractionRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.searchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        // Debounced search
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(posts = emptyList(), users = emptyList(), hasSearched = false)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            search()
        }
    }

    fun selectTab(tab: SearchTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (_uiState.value.query.isNotBlank()) {
            when (tab) {
                SearchTab.POSTS -> if (_uiState.value.posts.isEmpty()) searchPosts()
                SearchTab.USERS -> if (_uiState.value.users.isEmpty()) searchUsers()
            }
        }
    }

    fun search(saveToHistory: Boolean = false) {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        if (saveToHistory) {
            viewModelScope.launch { settingsStore.addSearchHistory(query) }
        }

        _uiState.update {
            it.copy(
                isSearching = true,
                posts = emptyList(),
                users = emptyList(),
                postsCursor = null,
                usersCursor = null,
                hasSearched = true,
            )
        }
        searchPosts()
        searchUsers()
    }

    fun searchFromHistory(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = query) }
        search(saveToHistory = true)
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { settingsStore.removeSearchHistory(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { settingsStore.clearSearchHistory() }
    }

    private fun searchPosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            searchRepository.searchPosts(_uiState.value.query.trim())
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            posts = response.posts,
                            postsCursor = response.cursor,
                            hasMorePosts = response.cursor != null,
                            isSearching = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isSearching = false) }
                }
        }
    }

    private fun searchUsers() {
        viewModelScope.launch {
            searchRepository.searchActors(_uiState.value.query.trim())
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            users = response.actors,
                            usersCursor = response.cursor,
                            hasMoreUsers = response.cursor != null,
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore) return

        when (state.selectedTab) {
            SearchTab.POSTS -> {
                if (!state.hasMorePosts || state.postsCursor == null) return
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoadingMore = true) }
                    searchRepository.searchPosts(state.query.trim(), cursor = state.postsCursor)
                        .onSuccess { response ->
                            _uiState.update {
                                it.copy(
                                    posts = it.posts + response.posts,
                                    postsCursor = response.cursor,
                                    hasMorePosts = response.cursor != null,
                                    isLoadingMore = false,
                                )
                            }
                        }
                        .onFailure {
                            _uiState.update { it.copy(isLoadingMore = false) }
                        }
                }
            }
            SearchTab.USERS -> {
                if (!state.hasMoreUsers || state.usersCursor == null) return
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoadingMore = true) }
                    searchRepository.searchActors(state.query.trim(), cursor = state.usersCursor)
                        .onSuccess { response ->
                            _uiState.update {
                                it.copy(
                                    users = it.users + response.actors,
                                    usersCursor = response.cursor,
                                    hasMoreUsers = response.cursor != null,
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
    }

    fun toggleLike(postUri: String, postCid: String, currentLikeUri: String?) {
        viewModelScope.launch {
            if (currentLikeUri != null) {
                interactionRepository.unlike(currentLikeUri).onSuccess {
                    updatePost(postUri) { post ->
                        post.copy(
                            viewer = (post.viewer ?: PostViewerState()).copy(like = null),
                            likeCount = (post.likeCount ?: 1) - 1,
                        )
                    }
                }
            } else {
                interactionRepository.like(postUri, postCid).onSuccess { response ->
                    updatePost(postUri) { post ->
                        post.copy(
                            viewer = (post.viewer ?: PostViewerState()).copy(like = response.uri),
                            likeCount = (post.likeCount ?: 0) + 1,
                        )
                    }
                }
            }
        }
    }

    fun toggleRepost(postUri: String, postCid: String, currentRepostUri: String?) {
        viewModelScope.launch {
            if (currentRepostUri != null) {
                interactionRepository.unrepost(currentRepostUri).onSuccess {
                    updatePost(postUri) { post ->
                        post.copy(
                            viewer = (post.viewer ?: PostViewerState()).copy(repost = null),
                            repostCount = (post.repostCount ?: 1) - 1,
                        )
                    }
                }
            } else {
                interactionRepository.repost(postUri, postCid).onSuccess { response ->
                    updatePost(postUri) { post ->
                        post.copy(
                            viewer = (post.viewer ?: PostViewerState()).copy(repost = response.uri),
                            repostCount = (post.repostCount ?: 0) + 1,
                        )
                    }
                }
            }
        }
    }

    fun toggleBookmark(postUri: String, postCid: String, currentBookmarkUri: String?) {
        viewModelScope.launch {
            if (currentBookmarkUri != null) {
                interactionRepository.unbookmark(currentBookmarkUri).onSuccess {
                    updatePost(postUri) { post ->
                        post.copy(viewer = (post.viewer ?: PostViewerState()).copy(bookmark = null))
                    }
                }
            } else {
                interactionRepository.bookmark(postUri, postCid).onSuccess { response ->
                    updatePost(postUri) { post ->
                        post.copy(viewer = (post.viewer ?: PostViewerState()).copy(bookmark = response.uri))
                    }
                }
            }
        }
    }

    private fun updatePost(postUri: String, transform: (PostView) -> PostView) {
        _uiState.update { state ->
            state.copy(posts = state.posts.map { if (it.uri == postUri) transform(it) else it })
        }
    }
}
