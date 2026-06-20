package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * AT Protocol feed/post models.
 * Maps to app.bsky.feed.defs and app.bsky.feed.post.
 */

@Serializable
data class TimelineResponse(
    val feed: List<FeedViewPost> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class GetPostsResponse(
    val posts: List<PostView> = emptyList(),
)

@Serializable
data class FeedViewPost(
    val post: PostView,
    val reply: ReplyRef? = null,
    val reason: FeedReason? = null,
)

@Serializable
data class PostView(
    val uri: String,
    val cid: String,
    val author: ProfileViewBasic,
    val record: JsonElement,  // PostRecord as raw JSON for flexible parsing
    val embed: PostEmbedView? = null,
    val replyCount: Int? = null,
    val repostCount: Int? = null,
    val likeCount: Int? = null,
    val quoteCount: Int? = null,
    val indexedAt: String,
    val viewer: PostViewerState? = null,
    val labels: List<ContentLabel> = emptyList(),
)

@Serializable
data class PostRecord(
    val text: String,
    val createdAt: String,
    val facets: List<Facet> = emptyList(),
    val reply: PostReplyRef? = null,
    val embed: JsonElement? = null,
    val langs: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("\$via") val via: String? = null,
)

/** Response from com.atproto.repo.getRecord */
@Serializable
data class GetRecordResponse(
    val uri: String = "",
    val cid: String? = null,
    val value: RecordValue? = null,
)

/** Generic record value — covers repost records which have a subject ref */
@Serializable
data class RecordValue(
    @SerialName("\$type") val type: String? = null,
    val subject: PostRef? = null,
    val createdAt: String? = null,
)

@Serializable
data class PostReplyRef(
    val root: PostRef,
    val parent: PostRef,
)

@Serializable
data class PostRef(
    val uri: String,
    val cid: String,
)

@Serializable
data class PostViewerState(
    val like: String? = null,
    val repost: String? = null,
    val bookmark: String? = null,
    val replyDisabled: Boolean? = null,
    val threadMuted: Boolean? = null,
    val embeddingDisabled: Boolean? = null,
)

@Serializable
data class ProfileViewBasic(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val labels: List<ContentLabel> = emptyList(),
    val verification: VerificationState? = null,
)

/**
 * app.bsky.actor.defs#verificationState — Bluesky verification status for an account.
 * verifiedStatus / trustedVerifierStatus are one of "valid" | "invalid" | "none".
 */
@Serializable
data class VerificationState(
    val verifiedStatus: String? = null,
    val trustedVerifierStatus: String? = null,
)

@Serializable
data class ContentLabel(
    val src: String? = null,
    val uri: String? = null,
    val cid: String? = null,
    @SerialName("val") val labelValue: String? = null,
    val neg: Boolean? = null,
    val cts: String? = null,
)

/**
 * app.bsky.feed.defs#replyRef — `root` and `parent` are unions of
 * postView | notFoundPost | blockedPost. They are kept as raw [JsonElement]
 * because notFoundPost/blockedPost lack a full post (e.g. blockedPost's author
 * is a `blockedAuthor` with no `handle`), which would otherwise fail strict
 * deserialization and abort parsing of the entire feed response.
 */
@Serializable
data class ReplyRef(
    val root: JsonElement? = null,
    val parent: JsonElement? = null,
) {
    /** Reply root as a full [PostView], or null when it is a notFoundPost / blockedPost. */
    val rootPost: PostView? get() = root?.toPostViewOrNull()

    /** Reply parent as a full [PostView], or null when it is a notFoundPost / blockedPost. */
    val parentPost: PostView? get() = parent?.toPostViewOrNull()
}

private fun JsonElement.toPostViewOrNull(): PostView? = try {
    com.kazahana.app.data.AppJson.decodeFromJsonElement<PostView>(this)
} catch (_: Exception) {
    null
}

@Serializable
data class FeedReason(
    @SerialName("\$type") val type: String? = null,
    val by: ProfileViewBasic? = null,
    val indexedAt: String? = null,
)

// Facets (rich text)
@Serializable
data class Facet(
    val index: FacetByteSlice,
    val features: List<FacetFeature>,
)

@Serializable
data class FacetByteSlice(
    val byteStart: Int,
    val byteEnd: Int,
)

@Serializable
data class FacetFeature(
    @SerialName("\$type") val type: String,
    val uri: String? = null,     // for links
    val did: String? = null,     // for mentions
    val tag: String? = null,     // for hashtags
)

// Embeds
@Serializable
data class PostEmbedView(
    @SerialName("\$type") val type: String? = null,
    val images: List<ImageView>? = null,     // app.bsky.embed.images#view
    val items: List<GalleryViewImage>? = null, // app.bsky.embed.gallery#view (Bluesky v1.123+)
    val external: ExternalView? = null,
    // Raw embedded record. For app.bsky.embed.record#view this is the
    // `#viewRecord` directly; for app.bsky.embed.recordWithMedia#view it is a
    // nested `#view` wrapper ({ record: <viewRecord> }). Use [quotedRecord] to
    // resolve the actual viewRecord regardless of nesting.
    val record: JsonElement? = null,
    val media: PostEmbedView? = null,       // recordWithMedia
    val playlist: String? = null,            // video HLS
    val thumbnail: String? = null,           // video thumbnail
    val aspectRatio: AspectRatio? = null,
    val alt: String? = null,                 // video alt text
) {
    /**
     * Unified image list for rendering, sourced from either the legacy
     * `app.bsky.embed.images#view` (`images`) or the newer
     * `app.bsky.embed.gallery#view` (`items`, 5–10 photos). Gallery `#viewImage`
     * entries are mapped onto [ImageView]; unknown union members (e.g. a future
     * `#viewVideo`) are skipped so they never break rendering. Returns null when
     * there are no displayable images.
     */
    val displayImages: List<ImageView>?
        get() = when {
            !images.isNullOrEmpty() -> images
            !items.isNullOrEmpty() -> items.mapNotNull { it.toImageView() }.ifEmpty { null }
            else -> null
        }

    /**
     * The quoted post (`app.bsky.embed.record#viewRecord`) as raw JSON, resolved
     * for both embed shapes:
     * - `app.bsky.embed.record#view`: [record] is the `#viewRecord` itself.
     * - `app.bsky.embed.recordWithMedia#view`: [record] is a `#view` wrapper whose
     *   own `record` field holds the `#viewRecord`.
     * Discrimination is structural: a `#view` wrapper has a nested `record` object,
     * while a `#viewRecord` (uri/cid/author/value/embeds) does not. Returns null
     * when there is no embedded record.
     */
    val quotedRecord: JsonElement?
        get() {
            val rec = record ?: return null
            val obj = rec as? JsonObject ?: return rec
            val nested = obj["record"]
            return if (nested != null && nested !is JsonNull) nested else rec
        }
}

@Serializable
data class ImageView(
    val thumb: String,
    val fullsize: String,
    val alt: String = "",
    val aspectRatio: AspectRatio? = null,
)

/**
 * app.bsky.embed.gallery#viewImage — a single hydrated gallery photo.
 * Note the field is `thumbnail` (not `thumb` as in embed.images#viewImage).
 * `thumbnail`/`fullsize` are nullable so that an unknown future union member
 * deserializes without throwing; [toImageView] returns null for such entries.
 */
@Serializable
data class GalleryViewImage(
    @SerialName("\$type") val type: String? = null,
    val thumbnail: String? = null,
    val fullsize: String? = null,
    val alt: String = "",
    val aspectRatio: AspectRatio? = null,
) {
    fun toImageView(): ImageView? {
        if (thumbnail == null || fullsize == null) return null
        return ImageView(thumb = thumbnail, fullsize = fullsize, alt = alt, aspectRatio = aspectRatio)
    }
}

@Serializable
data class AspectRatio(
    val width: Int,
    val height: Int,
)

@Serializable
data class ExternalView(
    val uri: String,
    val title: String = "",
    val description: String = "",
    val thumb: String? = null,
    // Standard Site (site.standard.*) extended fields — all optional for backward compat
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val readingTime: Int? = null,
    val source: ExternalSource? = null,
    val associatedRefs: List<StrongRef> = emptyList(),
    val associatedProfiles: List<ProfileViewBasic> = emptyList(),
)

/** Standard Site publication info merged into the external embed view. */
@Serializable
data class ExternalSource(
    val uri: String,
    val icon: String? = null,
    val title: String,
    val description: String? = null,
    val theme: ExternalSourceTheme? = null,
)

@Serializable
data class ExternalSourceTheme(
    val backgroundRGB: ColorRGB? = null,
    val foregroundRGB: ColorRGB? = null,
    val accentRGB: ColorRGB? = null,
    val accentForegroundRGB: ColorRGB? = null,
)

@Serializable
data class ColorRGB(
    val r: Int,
    val g: Int,
    val b: Int,
)

/** com.atproto.repo.strongRef — a {uri, cid} pair referencing another record. */
@Serializable
data class StrongRef(
    val uri: String,
    val cid: String,
)

/** Parsed embedded record (app.bsky.embed.record#viewRecord) for quote posts */
@Serializable
data class EmbedViewRecord(
    @SerialName("\$type") val type: String? = null,
    val uri: String = "",
    val cid: String = "",
    val author: ProfileViewBasic? = null,
    val value: JsonElement? = null,
    val embeds: List<PostEmbedView> = emptyList(),
    val indexedAt: String = "",
)

// Blob upload response
@Serializable
data class BlobResponse(
    val blob: BlobData,
)

@Serializable
data class BlobData(
    @SerialName("\$type") val type: String? = null,
    val ref: BlobLink,
    val mimeType: String,
    val size: Int,
)

@Serializable
data class BlobLink(
    @SerialName("\$link") val link: String,
)

// For building image embeds in createRecord
data class BlobRef(
    val link: String,
    val mimeType: String,
    val size: Int,
)

data class ImageEmbedItem(
    val blobRef: BlobRef,
    val alt: String,
    val aspectRatio: AspectRatio? = null,
)

@Serializable
data class ImageEmbed(
    @SerialName("\$type") val type: String = "app.bsky.embed.images",
    val images: List<ImageEmbedEntry>,
)

@Serializable
data class ImageEmbedEntry(
    val alt: String,
    val image: BlobData,
    val aspectRatio: AspectRatio? = null,
)

// createRecord request/response
@Serializable
data class CreateRecordRequest(
    val repo: String,
    val collection: String,
    val record: JsonElement,
)

@Serializable
data class CreateRecordResponse(
    val uri: String,
    val cid: String,
)

// Video upload
@Serializable
data class VideoJobStatusWrapper(
    val jobStatus: VideoJobStatus,
)

@Serializable
data class VideoJobStatus(
    val jobId: String,
    val did: String? = null,
    val state: String,
    val blob: BlobData? = null,
    val error: String? = null,
    val message: String? = null,
)

// app.bsky.embed.getEmbedExternalView response (Standard Site link preview)
@Serializable
data class GetEmbedExternalViewResponse(
    val view: EmbedExternalViewWrapper? = null,
    val associatedRefs: List<StrongRef> = emptyList(),
)

/** app.bsky.embed.external#view — wraps the hydrated external view. */
@Serializable
data class EmbedExternalViewWrapper(
    @SerialName("\$type") val type: String? = null,
    val external: ExternalView? = null,
)

// getQuotes response
@Serializable
data class GetQuotesResponse(
    val uri: String,
    val cid: String? = null,
    val cursor: String? = null,
    val posts: List<PostView> = emptyList(),
)
