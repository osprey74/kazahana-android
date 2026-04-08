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
import com.kazahana.app.data.ogp.OgpData
import com.kazahana.app.data.ogp.OgpService
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.repository.ExternalEmbedData
import com.kazahana.app.data.util.ImageHelper
import com.kazahana.app.data.richtext.RichTextParser
import com.kazahana.app.R
import com.kazahana.app.data.WatermarkPreset
import com.kazahana.app.data.WatermarkService
import com.kazahana.app.data.WatermarkSettings
import com.kazahana.app.data.local.DraftStore
import com.kazahana.app.data.local.PostDraft
import com.kazahana.app.data.local.SessionStore
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.remote.ClaudeService
import com.kazahana.app.data.repository.PostRepository
import io.ktor.client.call.body
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttachedImage(
    val uri: Uri,
    val alt: String = "",
    val mimeType: String = "image/jpeg",
)

data class AttachedVideo(
    val uri: Uri,
    val mimeType: String = "video/mp4",
    val sizeBytes: Long = 0,
    val alt: String = "",
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

/** Threadgate setting — controls who can reply to a post. */
enum class ThreadgateSetting {
    EVERYONE,              // No restriction (default)
    NO_ONE,                // Block all replies
    MENTION,               // Only mentioned users
    FOLLOWER,              // Only followers
    FOLLOWING,             // Only people you follow
    MENTION_AND_FOLLOWER,  // Mentioned + followers
    MENTION_AND_FOLLOWING, // Mentioned + following
    ;

    /** Returns the allow rules for the AT Protocol threadgate record, or null for EVERYONE. */
    fun toAllowRules(): List<String>? = when (this) {
        EVERYONE -> null
        NO_ONE -> emptyList()
        MENTION -> listOf("app.bsky.feed.threadgate#mentionRule")
        FOLLOWER -> listOf("app.bsky.feed.threadgate#followerRule")
        FOLLOWING -> listOf("app.bsky.feed.threadgate#followingRule")
        MENTION_AND_FOLLOWER -> listOf(
            "app.bsky.feed.threadgate#mentionRule",
            "app.bsky.feed.threadgate#followerRule",
        )
        MENTION_AND_FOLLOWING -> listOf(
            "app.bsky.feed.threadgate#mentionRule",
            "app.bsky.feed.threadgate#followingRule",
        )
    }
}

data class ComposeUiState(
    val text: String = "",
    val images: List<AttachedImage> = emptyList(),
    val video: AttachedVideo? = null,
    val isPosting: Boolean = false,
    val postingStatus: String? = null,
    val error: String? = null,
    val posted: Boolean = false,
    val editingAltIndex: Int? = null,
    val replyTarget: ReplyTarget? = null,
    val quoteTarget: QuoteTarget? = null,
    val threadgateSetting: ThreadgateSetting = ThreadgateSetting.EVERYONE,
    val disableEmbedding: Boolean = false,
    val showThreadgateDialog: Boolean = false,
    val showPostgateDialog: Boolean = false,
    val editingVideoAlt: Boolean = false,
    val linkCard: OgpData? = null,
    val isFetchingLinkCard: Boolean = false,
    val linkCardDismissed: Boolean = false,
    val isGeneratingAlt: Boolean = false,
    val generatingAltIndex: Int? = null,
    val editingImageIndex: Int? = null,
    val showDraftList: Boolean = false,
    val draftSaved: Boolean = false,
) {
    val canPost: Boolean
        get() = (text.isNotBlank() || images.isNotEmpty() || video != null) && !isPosting
    val canAddImage: Boolean
        get() = images.size < MAX_IMAGES && video == null
    val canAddVideo: Boolean
        get() = images.isEmpty() && video == null
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
    val imageHelper: ImageHelper,
    private val settingsStore: SettingsStore,
    private val draftStore: DraftStore,
    private val sessionStore: SessionStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        const val MAX_VIDEO_BYTES = 100_000_000L // 100MB (Bluesky server limit)
        private const val VIA_NAME = "kazahana for Android"
    }

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    val claudeApiKeyFlow = settingsStore.claudeApiKey

    val watermarkSettings: StateFlow<WatermarkSettings> = settingsStore.watermarkSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatermarkSettings())

    val confirmDraftImageQuality: StateFlow<Boolean> = settingsStore.confirmDraftImageQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val currentHandle: String
        get() = sessionStore.load()?.handle ?: "example.bsky.social"

    val drafts: StateFlow<List<PostDraft>> = draftStore.loadAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var ogpFetchJob: Job? = null
    private var lastFetchedUrl: String? = null

    fun setReply(target: ReplyTarget) {
        _uiState.update { it.copy(replyTarget = target) }
    }

    fun setQuote(target: QuoteTarget) {
        _uiState.update { it.copy(quoteTarget = target) }
    }

    fun updateText(text: String) {
        _uiState.update { it.copy(text = text, error = null) }
        // Auto-detect URL and fetch OGP (debounced)
        scheduleOgpFetch(text)
    }

    fun addVideo(uri: Uri, mimeType: String, sizeBytes: Long) {
        if (sizeBytes > MAX_VIDEO_BYTES) {
            _uiState.update { it.copy(error = "Video too large (${sizeBytes / 1_048_576} MB). Max 100 MB.") }
            return
        }
        _uiState.update { state ->
            state.copy(
                video = AttachedVideo(uri = uri, mimeType = mimeType, sizeBytes = sizeBytes),
                // Clear images and link card when adding video (mutually exclusive)
                images = emptyList(),
                linkCard = null,
                linkCardDismissed = true,
                error = null,
            )
        }
    }

    fun removeVideo() {
        _uiState.update { it.copy(video = null, linkCardDismissed = false) }
    }

    fun startEditVideoAlt() {
        _uiState.update { it.copy(editingVideoAlt = true) }
    }

    fun updateVideoAlt(alt: String) {
        _uiState.update { state ->
            state.copy(video = state.video?.copy(alt = alt))
        }
    }

    fun dismissVideoAltEditor() {
        _uiState.update { it.copy(editingVideoAlt = false) }
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

    fun updateImageUri(index: Int, newUri: Uri) {
        _uiState.update { state ->
            val updated = state.images.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(uri = newUri, mimeType = "image/jpeg")
            }
            state.copy(images = updated)
        }
    }

    fun removeImage(index: Int) {
        _uiState.update { state ->
            state.copy(images = state.images.filterIndexed { i, _ -> i != index })
        }
    }

    fun startEditImage(index: Int) {
        _uiState.update { it.copy(editingImageIndex = index) }
    }

    fun dismissImageEditor() {
        _uiState.update { it.copy(editingImageIndex = null) }
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

    fun generateAltText(index: Int) {
        val image = _uiState.value.images.getOrNull(index) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingAlt = true, generatingAltIndex = index) }
            try {
                val apiKey = settingsStore.claudeApiKey.first()
                if (apiKey.isBlank()) {
                    _uiState.update { it.copy(isGeneratingAlt = false, generatingAltIndex = null, error = "Claude API key not set") }
                    return@launch
                }
                val bytes = imageHelper.readBytes(image.uri)
                if (bytes == null) {
                    _uiState.update { it.copy(isGeneratingAlt = false, generatingAltIndex = null, error = "Failed to read image") }
                    return@launch
                }
                val langCode = settingsStore.appLocale.first().ifEmpty {
                    java.util.Locale.getDefault().language
                }
                ClaudeService.generateAltText(bytes, apiKey, langCode)
                    .onSuccess { altText ->
                        updateAlt(index, altText)
                        _uiState.update { it.copy(isGeneratingAlt = false, generatingAltIndex = null) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isGeneratingAlt = false, generatingAltIndex = null, error = "ALT generation failed: ${e.message}") }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingAlt = false, generatingAltIndex = null, error = "ALT generation failed: ${e.message}") }
            }
        }
    }

    fun setThreadgateSetting(setting: ThreadgateSetting) {
        _uiState.update { it.copy(threadgateSetting = setting) }
    }

    fun setDisableEmbedding(disable: Boolean) {
        _uiState.update { it.copy(disableEmbedding = disable) }
    }

    fun showThreadgateDialog() {
        _uiState.update { it.copy(showThreadgateDialog = true) }
    }

    fun dismissThreadgateDialog() {
        _uiState.update { it.copy(showThreadgateDialog = false) }
    }

    fun showPostgateDialog() {
        _uiState.update { it.copy(showPostgateDialog = true) }
    }

    fun dismissPostgateDialog() {
        _uiState.update { it.copy(showPostgateDialog = false) }
    }

    fun dismissLinkCard() {
        ogpFetchJob?.cancel()
        lastFetchedUrl = null
        _uiState.update { it.copy(linkCard = null, isFetchingLinkCard = false, linkCardDismissed = true) }
    }

    private fun scheduleOgpFetch(text: String) {
        val state = _uiState.value
        // Skip if user dismissed the link card, has images, or is posting
        if (state.linkCardDismissed || state.images.isNotEmpty()) return

        val url = OgpService.extractUrl(text)
        if (url == null) {
            // URL removed from text — clear link card
            if (state.linkCard != null || state.isFetchingLinkCard) {
                ogpFetchJob?.cancel()
                lastFetchedUrl = null
                _uiState.update { it.copy(linkCard = null, isFetchingLinkCard = false) }
            }
            return
        }
        // Already fetched this URL
        if (url == lastFetchedUrl) return

        ogpFetchJob?.cancel()
        ogpFetchJob = viewModelScope.launch {
            delay(500) // debounce
            lastFetchedUrl = url
            _uiState.update { it.copy(isFetchingLinkCard = true) }
            val ogp = OgpService.fetch(url)
            _uiState.update { it.copy(linkCard = ogp, isFetchingLinkCard = false) }
        }
    }

    /**
     * Build localized label map for watermark burn-in text.
     */
    private fun buildWatermarkLabels(): Map<WatermarkPreset, String> {
        return WatermarkService.buildLabelMap(
            copyrightLabel = context.getString(R.string.watermark_label_copyright),
            aiJaLabel = context.getString(R.string.watermark_label_ai_ja),
            photoLabel = context.getString(R.string.watermark_label_photo),
        )
    }

    /**
     * Apply watermark to image bytes, returning JPEG bytes.
     */
    private fun applyWatermarkToBytes(imageBytes: ByteArray, wmSettings: WatermarkSettings, handle: String): ByteArray {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return imageBytes
        val labels = buildWatermarkLabels()
        val result = WatermarkService.apply(bitmap, wmSettings, handle, labels)
        if (result !== bitmap) bitmap.recycle()
        val out = java.io.ByteArrayOutputStream()
        result.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        result.recycle()
        return out.toByteArray()
    }

    fun post(skipWatermark: Boolean = false, preComputedWmImages: List<Pair<ByteArray, String>>? = null) {
        val state = _uiState.value
        if (!state.canPost || state.isOverLimit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, error = null) }

            try {
                val wmSettings = watermarkSettings.value
                val shouldApplyWm = !skipWatermark && wmSettings.enabled && state.images.isNotEmpty()

                // Upload images first
                val imageEmbeds = mutableListOf<ImageEmbedItem>()
                if (preComputedWmImages != null) {
                    // Use pre-computed watermarked images (from confirm modal)
                    for ((index, wmData) in preComputedWmImages.withIndex()) {
                        val (bytes, mime) = wmData
                        // Compress if needed
                        val finalBytes = if (bytes.size > 950_000) {
                            compressBytes(bytes)
                        } else bytes
                        val uploadResult = postRepository.uploadBlob(finalBytes, mime)
                        uploadResult.onSuccess { blob ->
                            val image = state.images.getOrNull(index)
                            val aspectRatio = image?.let { imageHelper.getAspectRatio(it.uri) }
                            imageEmbeds.add(
                                ImageEmbedItem(
                                    blobRef = BlobRef(
                                        link = blob.blob.ref.link,
                                        mimeType = blob.blob.mimeType,
                                        size = blob.blob.size,
                                    ),
                                    alt = image?.alt ?: "",
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
                } else {
                    for (image in state.images) {
                        var (bytes, compressedMime) = imageHelper.readAndCompressImage(image.uri, image.mimeType) ?: continue
                        if (shouldApplyWm) {
                            bytes = applyWatermarkToBytes(bytes, wmSettings, currentHandle)
                            compressedMime = "image/jpeg"
                            // Compress after watermark if needed
                            if (bytes.size > 950_000) {
                                bytes = compressBytes(bytes)
                            }
                        }
                        val uploadResult = postRepository.uploadBlob(bytes, compressedMime)
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

                // Upload video if attached
                var videoEmbed: com.kazahana.app.data.repository.VideoEmbedData? = null
                if (state.video != null) {
                    _uiState.update { it.copy(postingStatus = "Uploading video…") }
                    val videoBytes = imageHelper.readBytes(state.video.uri)
                    if (videoBytes != null) {
                        postRepository.uploadVideo(videoBytes, state.video.mimeType)
                            .onSuccess { embed ->
                                videoEmbed = embed.copy(alt = state.video.alt)
                            }
                            .onFailure { e ->
                                _uiState.update {
                                    it.copy(isPosting = false, postingStatus = null, error = "Video upload failed: ${e.message}")
                                }
                                return@launch
                            }
                    }
                    _uiState.update { it.copy(postingStatus = null) }
                }

                // Build external link card embed (only when no images and no video)
                val externalEmbed = if (state.linkCard != null && imageEmbeds.isEmpty() && videoEmbed == null) {
                    val ogp = state.linkCard
                    var thumbBlob: BlobRef? = null
                    if (ogp.imageUrl != null) {
                        val imageBytes = OgpService.downloadImage(ogp.imageUrl)
                        if (imageBytes != null) {
                            postRepository.uploadBlob(imageBytes, "image/jpeg")
                                .onSuccess { blob ->
                                    thumbBlob = BlobRef(
                                        link = blob.blob.ref.link,
                                        mimeType = blob.blob.mimeType,
                                        size = blob.blob.size,
                                    )
                                }
                        }
                    }
                    ExternalEmbedData(
                        uri = ogp.url,
                        title = ogp.title,
                        description = ogp.description,
                        thumbBlob = thumbBlob,
                    )
                } else null

                // Create post
                val via = if (settingsStore.showVia.first()) VIA_NAME else null
                val langs = settingsStore.resolvePostLanguages()
                postRepository.createPost(
                    text = state.text,
                    facets = facets,
                    images = imageEmbeds,
                    reply = replyRef,
                    quoteUri = quoteUri,
                    quoteCid = quoteCid,
                    externalEmbed = externalEmbed,
                    videoEmbed = videoEmbed,
                    langs = langs,
                    via = via,
                ).onSuccess { response ->
                    // Threadgate — only for new posts (not replies)
                    if (state.replyTarget == null) {
                        val allowRules = state.threadgateSetting.toAllowRules()
                        if (allowRules != null) {
                            postRepository.createThreadgate(response.uri, allowRules)
                        }
                    }
                    // Postgate — disable quoting
                    if (state.disableEmbedding) {
                        postRepository.createPostgate(response.uri)
                    }
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

    // ── Drafts ──

    fun showDraftList() {
        _uiState.update { it.copy(showDraftList = true) }
    }

    fun dismissDraftList() {
        _uiState.update { it.copy(showDraftList = false) }
    }

    fun dismissDraftSaved() {
        _uiState.update { it.copy(draftSaved = false) }
    }

    /**
     * Returns true if the compose screen has unsaved content.
     */
    fun hasContent(): Boolean {
        val state = _uiState.value
        return state.text.isNotBlank() || state.images.isNotEmpty() || state.video != null
    }

    /**
     * Save current compose state as a draft.
     */
    fun saveDraft() {
        val state = _uiState.value
        if (!hasContent()) return

        viewModelScope.launch {
            val imageBytes = state.images.mapNotNull { image ->
                val bytes = imageHelper.readBytes(image.uri) ?: return@mapNotNull null
                Pair(bytes, image.mimeType)
            }
            val videoBytes = state.video?.let { video ->
                val bytes = imageHelper.readBytes(video.uri) ?: return@let null
                Pair(bytes, video.mimeType)
            }

            val threadgateIndex = ThreadgateSetting.entries.indexOf(state.threadgateSetting)

            draftStore.saveDraft(
                text = state.text,
                images = imageBytes,
                video = videoBytes,
                threadgateIndex = threadgateIndex,
                disableEmbedding = state.disableEmbedding,
            )
            _uiState.update { it.copy(draftSaved = true) }
        }
    }

    /**
     * Load a draft into the compose state.
     */
    fun loadDraft(draft: PostDraft) {
        viewModelScope.launch {
            // Restore images — save draft bytes to cache and create URIs
            val images = draft.imageFileNames.mapIndexedNotNull { index, _ ->
                val bytes = draftStore.loadImageBytes(draft, index) ?: return@mapIndexedNotNull null
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@mapIndexedNotNull null
                val uri = imageHelper.saveBitmapToCache(bitmap)
                bitmap.recycle()
                AttachedImage(uri = uri, mimeType = "image/jpeg")
            }

            // Restore video — save to cache file and create URI
            val video = draft.videoFileName?.let {
                val bytes = draftStore.loadVideoBytes(draft) ?: return@let null
                val cacheFile = java.io.File(context.cacheDir, "draft_video_${System.currentTimeMillis()}.mp4")
                cacheFile.writeBytes(bytes)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile,
                )
                AttachedVideo(uri = uri, mimeType = "video/mp4", sizeBytes = bytes.size.toLong())
            }

            val threadgateSetting = ThreadgateSetting.entries.getOrElse(draft.threadgateIndex) {
                ThreadgateSetting.EVERYONE
            }

            _uiState.update {
                it.copy(
                    text = draft.text,
                    images = images,
                    video = video,
                    threadgateSetting = threadgateSetting,
                    disableEmbedding = draft.disableEmbedding,
                    showDraftList = false,
                )
            }

            // Delete the loaded draft
            draftStore.delete(draft.id)
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            draftStore.delete(id)
        }
    }

    fun deleteAllDrafts() {
        viewModelScope.launch {
            draftStore.deleteAll()
        }
    }

    /**
     * Compress JPEG bytes to fit under Bluesky's 950KB limit.
     */
    private fun compressBytes(bytes: ByteArray): ByteArray {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        var quality = 85
        while (quality >= 30) {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            if (out.toByteArray().size <= 950_000) {
                bitmap.recycle()
                return out.toByteArray()
            }
            quality -= 10
        }
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 20, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /**
     * Prepare watermarked images for preview in the confirmation modal.
     * Returns list of (bitmap, jpegBytes) pairs.
     */
    fun prepareWatermarkedImages(): List<Pair<android.graphics.Bitmap, ByteArray>> {
        val state = _uiState.value
        val wmSettings = watermarkSettings.value
        val handle = currentHandle
        return state.images.mapNotNull { image ->
            val originalBytes = imageHelper.readBytes(image.uri) ?: return@mapNotNull null
            val wmBytes = applyWatermarkToBytes(originalBytes, wmSettings, handle)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(wmBytes, 0, wmBytes.size)
                ?: return@mapNotNull null
            Pair(bitmap, wmBytes)
        }
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
