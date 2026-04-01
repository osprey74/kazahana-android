package com.kazahana.app.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.kazahana.app.ui.navigation.ThreadRoute
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.PostView
import com.kazahana.app.data.model.PostViewerState
import com.kazahana.app.data.repository.InteractionRepository
import com.kazahana.app.data.repository.ReportRepository
import com.kazahana.app.data.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private val json = com.kazahana.app.data.AppJson

data class ThreadUiState(
    val parentPosts: List<PostView> = emptyList(),
    val mainPost: PostView? = null,
    val replies: List<PostView> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val threadRepository: ThreadRepository,
    private val interactionRepository: InteractionRepository,
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val postUri: String = savedStateHandle.toRoute<ThreadRoute>().postUri

    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    init {
        if (postUri.isNotBlank()) {
            loadThread()
        }
    }

    fun loadThread() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            threadRepository.getPostThread(postUri)
                .onSuccess { response ->
                    try {
                        val threadObj = response.thread.jsonObject

                        // Parse main post
                        val mainPost = parsePostView(threadObj["post"])

                        // Collect parent chain — walk up to the root
                        val parents = mutableListOf<PostView>()
                        var parentElement: JsonElement? = threadObj["parent"]
                        while (parentElement != null) {
                            val parentObj = try { parentElement.jsonObject } catch (_: Exception) { break }
                            val type = parentObj["\$type"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (!type.contains("threadViewPost")) break
                            parsePostView(parentObj["post"])?.let { parents.add(0, it) }
                            parentElement = parentObj["parent"]
                        }

                        // Collect replies
                        val replies = mutableListOf<PostView>()
                        val repliesArray = threadObj["replies"]
                        if (repliesArray != null) {
                            for (replyElement in repliesArray.jsonArray) {
                                val replyObj = replyElement.jsonObject
                                val type = replyObj["\$type"]?.jsonPrimitive?.contentOrNull ?: ""
                                if (type.contains("threadViewPost")) {
                                    parsePostView(replyObj["post"])?.let { replies.add(it) }
                                }
                            }
                        }

                        _uiState.update {
                            it.copy(
                                parentPosts = parents,
                                mainPost = mainPost,
                                replies = replies,
                                isLoading = false,
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isLoading = false, error = "Parse error: ${e.message}") }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun parsePostView(element: JsonElement?): PostView? {
        if (element == null) return null
        return try {
            json.decodeFromJsonElement(PostView.serializer(), element)
        } catch (_: Exception) {
            null
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
                    state.copy(
                        replies = state.replies.filter { it.uri != postUri },
                    )
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
                _uiState.update { state ->
                    state.copy(
                        replies = state.replies.filter { it.author.did != did },
                    )
                }
            }
        }
    }

    fun blockUser(did: String) {
        viewModelScope.launch {
            interactionRepository.blockActor(did).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        replies = state.replies.filter { it.author.did != did },
                    )
                }
            }
        }
    }

    private fun updatePostViewer(postUri: String, transform: (PostViewerState) -> PostViewerState) {
        _uiState.update { state ->
            state.copy(
                mainPost = state.mainPost?.let {
                    if (it.uri == postUri) it.copy(viewer = transform(it.viewer ?: PostViewerState())) else it
                },
                parentPosts = state.parentPosts.map {
                    if (it.uri == postUri) it.copy(viewer = transform(it.viewer ?: PostViewerState())) else it
                },
                replies = state.replies.map {
                    if (it.uri == postUri) it.copy(viewer = transform(it.viewer ?: PostViewerState())) else it
                },
            )
        }
    }

    private fun updatePostCount(postUri: String, transform: (PostView) -> PostView) {
        _uiState.update { state ->
            state.copy(
                mainPost = state.mainPost?.let { if (it.uri == postUri) transform(it) else it },
                parentPosts = state.parentPosts.map { if (it.uri == postUri) transform(it) else it },
                replies = state.replies.map { if (it.uri == postUri) transform(it) else it },
            )
        }
    }

    fun saveMedia(
        context: android.content.Context,
        imageUrls: List<String>,
        videoUrl: String?,
        videoThumbnail: String?,
    ) {
        viewModelScope.launch {
            var success = true
            imageUrls.forEachIndexed { index, url ->
                if (!com.kazahana.app.data.util.MediaSaver.saveImage(context, url, index)) {
                    success = false
                }
            }
            if (videoUrl != null) {
                if (!com.kazahana.app.data.util.MediaSaver.saveVideo(context, videoUrl, videoThumbnail)) {
                    success = false
                }
            }
            val msgRes = if (success) com.kazahana.app.R.string.post_media_saved
                else com.kazahana.app.R.string.post_media_save_failed
            android.widget.Toast.makeText(context, context.getString(msgRes), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
