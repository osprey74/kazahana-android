package com.kazahana.app.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.model.ProfileViewDetailed
import com.kazahana.app.data.model.ProfileViewerState
import com.kazahana.app.data.model.StarterPackViewBasic
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.model.ListView
import com.kazahana.app.data.repository.FeedRepository
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.ProfileRepository
import com.kazahana.app.data.repository.ReportRepository
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
    STARTER_PACKS(R.string.profile_tab_starter_packs, "starter_packs"),
}

data class ProfileUiState(
    val profile: ProfileViewDetailed? = null,
    val posts: List<FeedViewPost> = emptyList(),
    val pinnedPost: FeedViewPost? = null,
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val isLoading: Boolean = false,
    val isLoadingPosts: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val isFollowLoading: Boolean = false,
    // Starter Packs tab
    val actorStarterPacks: List<StarterPackViewBasic> = emptyList(),
    val isLoadingStarterPacks: Boolean = false,
    val starterPacksCursor: String? = null,
    val hasMoreStarterPacks: Boolean = true,
    // List management
    val curateLists: List<ListView> = emptyList(),
    val listMembership: Map<String, String?> = emptyMap(), // listUri -> listItemUri (null = not member)
    val isLoadingLists: Boolean = false,
    val showListSheet: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val interactionRepository: InteractionRepository,
    private val reportRepository: ReportRepository,
    private val feedRepository: FeedRepository,
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
                    loadPinnedPost(profile)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun loadPinnedPost(profile: ProfileViewDetailed) {
        val uri = profile.pinnedPost?.uri ?: return
        viewModelScope.launch {
            profileRepository.getPosts(listOf(uri))
                .onSuccess { posts ->
                    val post = posts.firstOrNull() ?: return@onSuccess
                    _uiState.update { it.copy(pinnedPost = FeedViewPost(post = post)) }
                }
        }
    }

    fun refresh() {
        if (actorDid.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            profileRepository.getProfile(actorDid)
                .onSuccess { profile ->
                    val currentTab = _uiState.value.selectedTab
                    _uiState.update {
                        it.copy(
                            profile = profile,
                            isRefreshing = false,
                            posts = emptyList(),
                            pinnedPost = null,
                            cursor = null,
                            hasMore = true,
                            actorStarterPacks = emptyList(),
                            starterPacksCursor = null,
                            hasMoreStarterPacks = true,
                        )
                    }
                    when (currentTab) {
                        ProfileTab.STARTER_PACKS -> loadActorStarterPacks()
                        else -> loadPosts()
                    }
                    loadPinnedPost(profile)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRefreshing = false, error = e.message) }
                }
        }
    }

    fun selectTab(tab: ProfileTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab, posts = emptyList(), cursor = null, hasMore = true) }
        when (tab) {
            ProfileTab.STARTER_PACKS -> {
                if (_uiState.value.actorStarterPacks.isEmpty()) loadActorStarterPacks()
            }
            else -> loadPosts()
        }
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


    fun loadActorStarterPacks(cursor: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStarterPacks = true) }
            profileRepository.getActorStarterPacks(actor = actorDid, cursor = cursor)
                .onSuccess { response ->
                    _uiState.update {
                        if (cursor == null) {
                            it.copy(
                                actorStarterPacks = response.starterPacks,
                                starterPacksCursor = response.cursor,
                                hasMoreStarterPacks = response.cursor != null,
                                isLoadingStarterPacks = false,
                            )
                        } else {
                            it.copy(
                                actorStarterPacks = it.actorStarterPacks + response.starterPacks,
                                starterPacksCursor = response.cursor,
                                hasMoreStarterPacks = response.cursor != null,
                                isLoadingStarterPacks = false,
                            )
                        }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingStarterPacks = false) }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value

        // Handle non-post tabs separately
        when (state.selectedTab) {
            ProfileTab.STARTER_PACKS -> {
                if (state.isLoadingStarterPacks || !state.hasMoreStarterPacks || state.starterPacksCursor == null) return
                loadActorStarterPacks(cursor = state.starterPacksCursor)
                return
            }
            else -> { /* fall through to post-based loading */ }
        }

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

    fun hidePost(postUri: String) {
        viewModelScope.launch {
            interactionRepository.hidePost(postUri).onSuccess {
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

    fun toggleMute() {
        val profile = _uiState.value.profile ?: return
        val isMuted = profile.viewer?.muted == true

        viewModelScope.launch {
            if (isMuted) {
                interactionRepository.unmuteActor(profile.did).onSuccess {
                    _uiState.update {
                        it.copy(
                            profile = it.profile?.copy(
                                viewer = (it.profile.viewer ?: ProfileViewerState()).copy(muted = false),
                            ),
                        )
                    }
                }
            } else {
                interactionRepository.muteActor(profile.did).onSuccess {
                    _uiState.update {
                        it.copy(
                            profile = it.profile?.copy(
                                viewer = (it.profile.viewer ?: ProfileViewerState()).copy(muted = true),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun toggleBlock() {
        val profile = _uiState.value.profile ?: return
        val blockingUri = profile.viewer?.blocking

        viewModelScope.launch {
            if (blockingUri != null) {
                interactionRepository.unblockActor(blockingUri).onSuccess {
                    _uiState.update {
                        it.copy(
                            profile = it.profile?.copy(
                                viewer = (it.profile.viewer ?: ProfileViewerState()).copy(blocking = null),
                            ),
                        )
                    }
                }
            } else {
                interactionRepository.blockActor(profile.did).onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            profile = it.profile?.copy(
                                viewer = (it.profile.viewer ?: ProfileViewerState()).copy(blocking = response.uri),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun reportUserAsync(reasonType: String, reason: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(reportRepository.reportAccount(actorDid, reasonType, reason))
        }
    }

    fun reportPostAsync(postUri: String, postCid: String, reasonType: String, reason: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(reportRepository.reportPost(postUri, postCid, reasonType, reason))
        }
    }

    fun muteUser(did: String) {
        viewModelScope.launch {
            interactionRepository.muteActor(did).onSuccess {
                _uiState.update { state ->
                    state.copy(posts = state.posts.filter { it.post.author.did != did })
                }
            }
        }
    }

    fun showListSheet() {
        _uiState.update { it.copy(showListSheet = true) }
        loadCurateLists()
    }

    fun hideListSheet() {
        _uiState.update { it.copy(showListSheet = false) }
    }

    private fun loadCurateLists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLists = true) }
            feedRepository.getMyCurateLists()
                .onSuccess { lists ->
                    _uiState.update { it.copy(curateLists = lists, isLoadingLists = false) }
                    // Check membership for each list
                    for (list in lists) {
                        checkListMembership(list.uri)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingLists = false) }
                }
        }
    }

    private fun checkListMembership(listUri: String) {
        viewModelScope.launch {
            feedRepository.getListWithItems(listUri, actorDid)
                .onSuccess { (_, listItemUri) ->
                    _uiState.update {
                        it.copy(listMembership = it.listMembership + (listUri to listItemUri))
                    }
                }
        }
    }

    fun toggleListMembership(listUri: String, onResult: (Result<Boolean>) -> Unit) {
        val currentItemUri = _uiState.value.listMembership[listUri]
        viewModelScope.launch {
            if (currentItemUri != null) {
                // Remove from list
                interactionRepository.removeFromList(currentItemUri)
                    .onSuccess {
                        _uiState.update {
                            it.copy(listMembership = it.listMembership + (listUri to null))
                        }
                        onResult(Result.success(false)) // removed
                    }
                    .onFailure { e -> onResult(Result.failure(e)) }
            } else {
                // Add to list
                interactionRepository.addToList(listUri, actorDid)
                    .onSuccess { response ->
                        _uiState.update {
                            it.copy(listMembership = it.listMembership + (listUri to response.uri))
                        }
                        onResult(Result.success(true)) // added
                    }
                    .onFailure { e -> onResult(Result.failure(e)) }
            }
        }
    }

    fun blockUser(did: String) {
        viewModelScope.launch {
            interactionRepository.blockActor(did).onSuccess {
                _uiState.update { state ->
                    state.copy(posts = state.posts.filter { it.post.author.did != did })
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
                },
                pinnedPost = state.pinnedPost?.let { pinned ->
                    if (pinned.post.uri == postUri) {
                        pinned.copy(post = pinned.post.copy(viewer = transform(pinned.post.viewer ?: PostViewerState())))
                    } else pinned
                },
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
                },
                pinnedPost = state.pinnedPost?.let { pinned ->
                    if (pinned.post.uri == postUri) {
                        pinned.copy(post = transform(pinned.post))
                    } else pinned
                },
            )
        }
    }
}
