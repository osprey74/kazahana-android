package com.kazahana.app.ui.compose

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.AspectRatio
import com.kazahana.app.data.model.BlobRef
import com.kazahana.app.data.model.ImageEmbedItem
import com.kazahana.app.data.model.PostRef
import com.kazahana.app.data.model.PostReplyRef
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.util.ImageHelper
import com.kazahana.app.data.richtext.RichTextParser
import com.kazahana.app.data.repository.PostRepository
import io.ktor.client.call.body
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttachedImage(
    val uri: Uri,
    val alt: String = "",
    val mimeType: String = "image/jpeg",
)

/** Info about the post being replied to or quoted. */
data class ReplyTarget(
    val uri: String,
    val cid: String,
    val rootUri: String,
    val rootCid: String,
    val authorHandle: String,
    val authorDisplayName: String?,
    val text: String,
)

data class QuoteTarget(
    val uri: String,
    val cid: String,
    val authorHandle: String,
    val authorDisplayName: String?,
    val text: String,
)

data class ComposeUiState(
    val text: String = "",
    val images: List<AttachedImage> = emptyList(),
    val isPosting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false,
    val editingAltIndex: Int? = null,
    val replyTarget: ReplyTarget? = null,
    val quoteTarget: QuoteTarget? = null,
) {
    val canPost: Boolean
        get() = (text.isNotBlank() || images.isNotEmpty()) && !isPosting
    val canAddImage: Boolean
        get() = images.size < MAX_IMAGES
    val charCount: Int
        get() = text.graphemeCount()
    val isOverLimit: Boolean
        get() = charCount > MAX_CHARS

    companion object {
        const val MAX_IMAGES = 4
        const val MAX_CHARS = 300
    }
}

private fun String.graphemeCount(): Int {
    val breaker = java.text.BreakIterator.getCharacterInstance()
    breaker.setText(this)
    var count = 0
    while (breaker.next() != java.text.BreakIterator.DONE) count++
    return count
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val atProtoClient: ATProtoClient,
    private val imageHelper: ImageHelper,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    fun setReply(target: ReplyTarget) {
        _uiState.update { it.copy(replyTarget = target) }
    }

    fun setQuote(target: QuoteTarget) {
        _uiState.update { it.copy(quoteTarget = target) }
    }

    fun updateText(text: String) {
        _uiState.update { it.copy(text = text, error = null) }
    }

    fun addImages(uris: List<Uri>) {
        _uiState.update { state ->
            val remaining = ComposeUiState.MAX_IMAGES - state.images.size
            val newImages = uris.take(remaining).map { uri ->
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                AttachedImage(uri = uri, mimeType = mimeType)
            }
            state.copy(images = state.images + newImages)
        }
    }

    fun removeImage(index: Int) {
        _uiState.update { state ->
            state.copy(images = state.images.filterIndexed { i, _ -> i != index })
        }
    }

    fun startEditAlt(index: Int) {
        _uiState.update { it.copy(editingAltIndex = index) }
    }

    fun updateAlt(index: Int, alt: String) {
        _uiState.update { state ->
            val updated = state.images.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(alt = alt)
            }
            state.copy(images = updated)
        }
    }

    fun dismissAltEditor() {
        _uiState.update { it.copy(editingAltIndex = null) }
    }

    fun post() {
        val state = _uiState.value
        if (!state.canPost || state.isOverLimit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, error = null) }

            try {
                // Upload images first
                val imageEmbeds = mutableListOf<ImageEmbedItem>()
                for (image in state.images) {
                    val bytes = imageHelper.readBytes(image.uri) ?: continue
                    val uploadResult = postRepository.uploadBlob(bytes, image.mimeType)
                    uploadResult.onSuccess { blob ->
                        val aspectRatio = imageHelper.getAspectRatio(image.uri)
                        imageEmbeds.add(
                            ImageEmbedItem(
                                blobRef = BlobRef(
                                    link = blob.blob.ref.link,
                                    mimeType = blob.blob.mimeType,
                                    size = blob.blob.size,
                                ),
                                alt = image.alt,
                                aspectRatio = aspectRatio,
                            )
                        )
                    }.onFailure { e ->
                        _uiState.update {
                            it.copy(isPosting = false, error = "Image upload failed: ${e.message}")
                        }
                        return@launch
                    }
                }

                // Detect and resolve facets
                val detectedFacets = RichTextParser.detectFacets(state.text)
                val resolvedFacets = resolveMentions(state.text, detectedFacets)
                val facets = RichTextParser.toAtProtoFacets(state.text, resolvedFacets)

                // Build reply ref
                val replyRef = state.replyTarget?.let { target ->
                    PostReplyRef(
                        root = PostRef(uri = target.rootUri, cid = target.rootCid),
                        parent = PostRef(uri = target.uri, cid = target.cid),
                    )
                }

                // Build quote embed
                val quoteUri = state.quoteTarget?.uri
                val quoteCid = state.quoteTarget?.cid

                // Create post
                postRepository.createPost(
                    text = state.text,
                    facets = facets,
                    images = imageEmbeds,
                    reply = replyRef,
                    quoteUri = quoteUri,
                    quoteCid = quoteCid,
                ).onSuccess {
                    _uiState.update { it.copy(isPosting = false, posted = true) }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(isPosting = false, error = "Post failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isPosting = false, error = e.message)
                }
            }
        }
    }

    /**
     * Resolve @mention handles to DIDs via the AT Protocol resolveHandle API.
     */
    private suspend fun resolveMentions(
        text: String,
        facets: List<RichTextParser.DetectedFacet>,
    ): List<RichTextParser.DetectedFacet> {
        return facets.map { facet ->
            if (facet.feature.type == "app.bsky.richtext.facet#mention") {
                val handle = RichTextParser.extractHandle(text, facet)
                if (handle != null) {
                    val did = resolveHandle(handle)
                    if (did != null) {
                        facet.copy(feature = facet.feature.copy(did = did))
                    } else {
                        // Could not resolve — drop this facet
                        null
                    }
                } else facet
            } else facet
        }.filterNotNull()
    }

    private suspend fun resolveHandle(handle: String): String? {
        return try {
            val response = atProtoClient.getUnauthenticated(
                baseUrl = ATProtoClient.PUBLIC_API,
                nsid = "com.atproto.identity.resolveHandle",
                params = mapOf("handle" to handle),
            )
            if (response.status.value == 200) {
                val body: com.kazahana.app.data.model.ResolveHandleResponse = response.body()
                body.did
            } else null
        } catch (_: Exception) {
            null
        }
    }

}
