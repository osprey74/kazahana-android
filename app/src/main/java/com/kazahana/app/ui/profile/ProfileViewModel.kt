package com.kazahana.app.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.model.ProfileViewDetailed
import com.kazahana.app.data.model.ProfileViewerState
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import com.kazahana.app.R
import javax.inject.Inject

enum class ProfileTab(@StringRes val labelRes: Int, val filter: String) {
    POSTS(R.string.profile_tab_posts, "posts_no_replies"),
    REPLIES(R.string.profile_tab_replies, "posts_with_replies"),
    MEDIA(R.string.profile_tab_media, "posts_with_media"),
    LIKES(R.string.profile_tab_likes, "likes"),
}

data class ProfileUiState(
    val profile: ProfileViewDetailed? = null,
    val posts: List<FeedViewPost> = emptyList(),
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val isLoading: Boolean = false,
    val isLoadingPosts: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val isFollowLoading: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val interactionRepository: InteractionRepository,
    private val client: ATProtoClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Actor DID — from navigation arg or self
    private val actorDid: String = savedStateHandle.get<String>("actorDid")
        ?: client.session?.did ?: ""

    val isSelf: Boolean
        get() = actorDid == client.session?.did

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (actorDid.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            profileRepository.getProfile(actorDid)
                .onSuccess { profile ->
                    _uiState.update { it.copy(profile = profile, isLoading = false) }
                    loadPosts()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun selectTab(tab: ProfileTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab, posts = emptyList(), cursor = null, hasMore = true) }
        loadPosts()
    }

    fun loadPosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPosts = true) }

            val filter = _uiState.value.selectedTab.filter
            // Likes tab uses a different API
            if (_uiState.value.selectedTab == ProfileTab.LIKES) {
                loadLikes()
                return@launch
            }

            profileRepository.getAuthorFeed(actor = actorDid, filter = filter)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            posts = response.feed,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoadingPosts = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingPosts = false) }
                }
        }
    }

    private suspend fun loadLikes(cursor: String? = null) {
        profileRepository.getActorLikes(actor = actorDid, cursor = cursor)
            .onSuccess { response ->
                _uiState.update {
                    if (cursor == null) {
                        it.copy(
                            posts = response.feed,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoadingPosts = false,
                            isLoadingMore = false,
                        )
                    } else {
                        it.copy(
                            posts = it.posts + response.feed,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoadingMore = false,
                        )
                    }
                }
            }
            .onFailure {
                _uiState.update { it.copy(isLoadingPosts = false, isLoadingMore = false) }
            }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.cursor == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            if (state.selectedTab == ProfileTab.LIKES) {
                loadLikes(cursor = state.cursor)
                return@launch
            }

            val filter = state.selectedTab.filter
            profileRepository.getAuthorFeed(actor = actorDid, filter = filter, cursor = state.cursor)
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

    fun toggleFollow() {
        val profile = _uiState.value.profile ?: return
        val isFollowing = profile.viewer?.following != null

        viewModelScope.launch {
            _uiState.update { it.copy(isFollowLoading = true) }

            if (isFollowing) {
                val followUri = profile.viewer?.following ?: return@launch
                profileRepository.unfollow(followUri)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                profile = it.profile?.copy(
                                    viewer = (it.profile.viewer ?: ProfileViewerState()).copy(following = null),
                                    followersCount = (it.profile.followersCount ?: 1) - 1,
                                ),
                                isFollowLoading = false,
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isFollowLoading = false) }
                    }
            } else {
                profileRepository.follow(profile.did)
                    .onSuccess { response ->
                        _uiState.update {
                            it.copy(
                                profile = it.profile?.copy(
                                    viewer = (it.profile.viewer ?: ProfileViewerState()).copy(following = response.uri),
                                    followersCount = (it.profile.followersCount ?: 0) + 1,
                                ),
                                isFollowLoading = false,
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isFollowLoading = false) }
                    }
            }
        }
    }

    fun toggleLike(postUri: String, postCid: String, currentLikeUri: String?) {
        viewModelScope.launch {
            if (currentLikeUri != null) {
                interactionRepository.unlike(currentLikeUri).onSuccess {
                    updatePostViewer(postUri) { it.copy(like = null) }
                    updatePostCount(postUri) { it.copy(likeCount = (it.likeCount ?: 1) - 1) }
                }
            } else {
                interactionRepository.like(postUri, postCid).onSuccess { response ->
                    updatePostViewer(postUri) { it.copy(like = response.uri) }
                    updatePostCount(postUri) { it.copy(likeCount = (it.likeCount ?: 0) + 1) }
                }
            }
        }
    }

    fun toggleRepost(postUri: String, postCid: String, currentRepostUri: String?) {
        viewModelScope.launch {
            if (currentRepostUri != null) {
                interactionRepository.unrepost(currentRepostUri).onSuccess {
                    updatePostViewer(postUri) { it.copy(repost = null) }
                    updatePostCount(postUri) { it.copy(repostCount = (it.repostCount ?: 1) - 1) }
                }
            } else {
                interactionRepository.repost(postUri, postCid).onSuccess { response ->
                    updatePostViewer(postUri) { it.copy(repost = response.uri) }
                    updatePostCount(postUri) { it.copy(repostCount = (it.repostCount ?: 0) + 1) }
                }
            }
        }
    }

    fun toggleBookmark(postUri: String, postCid: String, currentBookmarkUri: String?) {
        viewModelScope.launch {
            if (currentBookmarkUri != null) {
                interactionRepository.unbookmark(currentBookmarkUri).onSuccess {
                    updatePostViewer(postUri) { it.copy(bookmark = null) }
                }
            } else {
                interactionRepository.bookmark(postUri, postCid).onSuccess { response ->
                    updatePostViewer(postUri) { it.copy(bookmark = response.uri) }
                }
            }
        }
    }

    private fun updatePostViewer(postUri: String, transform: (PostViewerState) -> PostViewerState) {
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map { feedPost ->
                    if (feedPost.post.uri == postUri) {
                        feedPost.copy(post = feedPost.post.copy(viewer = transform(feedPost.post.viewer ?: PostViewerState())))
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
