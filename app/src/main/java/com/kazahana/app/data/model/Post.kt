package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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

@Serializable
data class ReplyRef(
    val root: PostView? = null,
    val parent: PostView? = null,
)

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
    val images: List<ImageView>? = null,
    val external: ExternalView? = null,
    val record: EmbedRecordView? = null,
    val media: PostEmbedView? = null,       // recordWithMedia
    val playlist: String? = null,            // video HLS
    val thumbnail: String? = null,           // video thumbnail
    val aspectRatio: AspectRatio? = null,
    val alt: String? = null,                 // video alt text
)

@Serializable
data class ImageView(
    val thumb: String,
    val fullsize: String,
    val alt: String = "",
    val aspectRatio: AspectRatio? = null,
)

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
)

@Serializable
data class EmbedRecordView(
    @SerialName("\$type") val type: String? = null,
    val record: JsonElement? = null,  // Flexible — can be PostView, NotFoundPost, etc.
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

// getQuotes response
@Serializable
data class GetQuotesResponse(
    val uri: String,
    val cid: String? = null,
    val cursor: String? = null,
    val posts: List<PostView> = emptyList(),
)
