package com.kazahana.app.data.repository

import com.kazahana.app.data.model.BlobRef
import com.kazahana.app.data.model.BlobResponse
import com.kazahana.app.data.model.CreateRecordResponse
import com.kazahana.app.data.model.Facet
import com.kazahana.app.data.model.GetQuotesResponse
import com.kazahana.app.data.model.ImageEmbedItem
import com.kazahana.app.data.model.VideoJobStatus
import com.kazahana.app.data.model.VideoJobStatusWrapper
import io.ktor.client.statement.bodyAsText
import com.kazahana.app.data.model.PostReplyRef
import com.kazahana.app.data.remote.ATProtoClient
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Data for building an app.bsky.embed.video embed. */
data class VideoEmbedData(
    val blobLink: String,
    val mimeType: String,
    val size: Int,
    val alt: String = "",
    val aspectRatio: com.kazahana.app.data.model.AspectRatio? = null,
)

/** Data for building an app.bsky.embed.external embed. */
data class ExternalEmbedData(
    val uri: String,
    val title: String,
    val description: String,
    val thumbBlob: BlobRef? = null,
)

class PostRepository(
    private val client: ATProtoClient,
) {
    private val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun uploadBlob(
        data: ByteArray,
        mimeType: String,
        legacyFallback: (suspend () -> ByteArray)? = null,
    ): Result<BlobResponse> {
        return try {
            val response = client.uploadBlob(data, mimeType)
            if (response.status.isSuccess()) {
                // rawClient has no ContentNegotiation, parse manually
                val text = response.bodyAsText()
                val blob = jsonParser.decodeFromString<BlobResponse>(text)
                Result.success(blob)
            } else if (legacyFallback != null && isLegacySizeRejection(response.status.value)) {
                // atproto `#4823` raised the image blob limit to 2 MB, but older PDSes
                // still reject with 413/400. Retry once with a recompressed ≤1 MB payload.
                val smaller = legacyFallback()
                val retry = client.uploadBlob(smaller, mimeType)
                if (retry.status.isSuccess()) {
                    val blob = jsonParser.decodeFromString<BlobResponse>(retry.bodyAsText())
                    Result.success(blob)
                } else {
                    Result.failure(Exception("Upload failed: HTTP ${retry.status.value}"))
                }
            } else {
                Result.failure(Exception("Upload failed: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isLegacySizeRejection(status: Int): Boolean = status == 413 || status == 400

    suspend fun createPost(
        text: String,
        facets: List<Facet> = emptyList(),
        images: List<ImageEmbedItem> = emptyList(),
        reply: PostReplyRef? = null,
        quoteUri: String? = null,
        quoteCid: String? = null,
        externalEmbed: ExternalEmbedData? = null,
        videoEmbed: VideoEmbedData? = null,
        langs: List<String> = listOf("ja"),
        via: String? = null,
    ): Result<CreateRecordResponse> {
        return try {
            val did = client.session?.did
                ?: return Result.failure(Exception("Not authenticated"))

            val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

            val record = buildJsonObject {
                put("\$type", "app.bsky.feed.post")
                put("text", text)
                put("createdAt", now)
                if (facets.isNotEmpty()) {
                    put("facets", buildFacetsJson(facets))
                }
                // Build embed: images, video, quote, external, or combinations
                val hasImages = images.isNotEmpty()
                val hasQuote = quoteUri != null && quoteCid != null
                val hasExternal = externalEmbed != null
                val hasVideo = videoEmbed != null
                when {
                    hasImages && hasQuote -> {
                        put("embed", buildRecordWithMediaEmbed(images, quoteUri!!, quoteCid!!))
                    }
                    hasImages -> {
                        put("embed", buildImagesEmbed(images))
                    }
                    hasVideo -> {
                        put("embed", buildVideoEmbed(videoEmbed!!))
                    }
                    hasExternal && hasQuote -> {
                        put("embed", buildJsonObject {
                            put("\$type", "app.bsky.embed.recordWithMedia")
                            put("record", buildJsonObject {
                                put("\$type", "app.bsky.embed.record")
                                put("record", buildJsonObject {
                                    put("uri", quoteUri!!)
                                    put("cid", quoteCid!!)
                                })
                            })
                            put("media", buildExternalEmbed(externalEmbed!!))
                        })
                    }
                    hasExternal -> {
                        put("embed", buildExternalEmbed(externalEmbed!!))
                    }
                    hasQuote -> {
                        put("embed", buildQuoteEmbed(quoteUri!!, quoteCid!!))
                    }
                }
                if (reply != null) {
                    put("reply", buildJsonObject {
                        put("root", buildJsonObject {
                            put("uri", reply.root.uri)
                            put("cid", reply.root.cid)
                        })
                        put("parent", buildJsonObject {
                            put("uri", reply.parent.uri)
                            put("cid", reply.parent.cid)
                        })
                    })
                }
                put("langs", buildJsonArray {
                    langs.forEach { add(JsonPrimitive(it)) }
                })
                if (via != null) {
                    put("\$via", via)
                }
            }

            val request = buildJsonObject {
                put("repo", did)
                put("collection", "app.bsky.feed.post")
                put("record", record)
            }

            val response = client.post(
                nsid = "com.atproto.repo.createRecord",
                body = request,
            )
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Post failed: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildFacetsJson(facets: List<Facet>): JsonElement {
        return buildJsonArray {
            facets.forEach { facet ->
                add(buildJsonObject {
                    put("index", buildJsonObject {
                        put("byteStart", facet.index.byteStart)
                        put("byteEnd", facet.index.byteEnd)
                    })
                    put("features", buildJsonArray {
                        facet.features.forEach { feature ->
                            add(buildJsonObject {
                                put("\$type", feature.type)
                                feature.uri?.let { put("uri", it) }
                                feature.did?.let { put("did", it) }
                                feature.tag?.let { put("tag", it) }
                            })
                        }
                    })
                })
            }
        }
    }

    private fun buildQuoteEmbed(quoteUri: String, quoteCid: String): JsonElement {
        return buildJsonObject {
            put("\$type", "app.bsky.embed.record")
            put("record", buildJsonObject {
                put("uri", quoteUri)
                put("cid", quoteCid)
            })
        }
    }

    /**
     * Upload a video via video.bsky.app with service auth, then poll until processed.
     * Returns a [VideoEmbedData] ready for embedding, or failure.
     */
    suspend fun uploadVideo(
        data: ByteArray,
        mimeType: String,
    ): Result<VideoEmbedData> {
        return try {
            val did = client.session?.did
                ?: return Result.failure(Exception("Not authenticated"))

            // Step 1: Get service auth token
            val aud = client.pdsDid()
            val authResponse = client.getServiceAuth(aud, "com.atproto.repo.uploadBlob")
                ?: return Result.failure(Exception("Failed to get service auth (aud=$aud)"))
            val serviceToken = authResponse

            // Step 2: Upload to video.bsky.app
            val ext = if (mimeType.contains("quicktime") || mimeType.contains("mov")) "mov" else "mp4"
            val fileName = "${java.util.UUID.randomUUID()}.$ext"

            val uploadResponse = client.uploadVideo(data, mimeType, did, fileName, serviceToken)
            val uploadText = uploadResponse.bodyAsText()

            // HTTP 409 "already_exists" means the video was already uploaded/processed.
            // Parse the jobId from the 409 response and poll for the blob.
            val jobId: String
            var jobStatus: VideoJobStatus

            if (uploadResponse.status.value == 409) {
                // Video already uploaded — fetch current status via polling endpoint
                val alreadyExists = jsonParser.decodeFromString<VideoJobStatus>(uploadText)
                jobId = alreadyExists.jobId
                val pollResponse = client.getVideoJobStatus(jobId)
                if (pollResponse.status.isSuccess()) {
                    val wrapper = jsonParser.decodeFromString<VideoJobStatusWrapper>(pollResponse.bodyAsText())
                    jobStatus = wrapper.jobStatus
                } else {
                    jobStatus = alreadyExists.copy(error = null)
                }
            } else if (!uploadResponse.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${uploadResponse.status.value}: $uploadText"))
            } else {
                val initialStatus = jsonParser.decodeFromString<VideoJobStatusWrapper>(uploadText)
                jobId = initialStatus.jobStatus.jobId
                jobStatus = initialStatus.jobStatus
            }

            // Step 3: Poll for completion (max 60 attempts, 2s interval = ~120s timeout)
            for (i in 0 until 60) {
                if (jobStatus.state == "JOB_STATE_COMPLETED" && jobStatus.blob != null) break
                if (jobStatus.error != null) {
                    return Result.failure(Exception("Video processing failed: ${jobStatus.error}"))
                }
                delay(2000)
                val pollResponse = client.getVideoJobStatus(jobId)
                if (pollResponse.status.isSuccess()) {
                    val pollText = pollResponse.bodyAsText()
                    val wrapper = jsonParser.decodeFromString<VideoJobStatusWrapper>(pollText)
                    jobStatus = wrapper.jobStatus
                }
            }

            val blob = jobStatus.blob
                ?: return Result.failure(Exception("Video processing timed out"))

            Result.success(
                VideoEmbedData(
                    blobLink = blob.ref.link,
                    mimeType = blob.mimeType,
                    size = blob.size,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildVideoEmbed(video: VideoEmbedData): JsonElement {
        return buildJsonObject {
            put("\$type", "app.bsky.embed.video")
            put("video", buildJsonObject {
                put("\$type", "blob")
                put("ref", buildJsonObject {
                    put("\$link", video.blobLink)
                })
                put("mimeType", video.mimeType)
                put("size", video.size)
            })
            if (video.alt.isNotBlank()) {
                put("alt", video.alt)
            }
            video.aspectRatio?.let { ar ->
                put("aspectRatio", buildJsonObject {
                    put("width", ar.width)
                    put("height", ar.height)
                })
            }
        }
    }

    private fun buildRecordWithMediaEmbed(
        images: List<ImageEmbedItem>,
        quoteUri: String,
        quoteCid: String,
    ): JsonElement {
        return buildJsonObject {
            put("\$type", "app.bsky.embed.recordWithMedia")
            put("record", buildJsonObject {
                put("\$type", "app.bsky.embed.record")
                put("record", buildJsonObject {
                    put("uri", quoteUri)
                    put("cid", quoteCid)
                })
            })
            put("media", buildImagesEmbed(images))
        }
    }

    /**
     * Create a threadgate record to restrict who can reply to a post.
     * The rkey must match the post's rkey (AT Protocol spec).
     */
    suspend fun createThreadgate(
        postUri: String,
        allowRules: List<String>,  // list of $type values for allow rules
    ): Result<CreateRecordResponse> {
        return try {
            val did = client.session?.did
                ?: return Result.failure(Exception("Not authenticated"))
            val rkey = postUri.substringAfterLast("/")
            val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

            val record = buildJsonObject {
                put("\$type", "app.bsky.feed.threadgate")
                put("post", postUri)
                put("allow", buildJsonArray {
                    allowRules.forEach { ruleType ->
                        add(buildJsonObject {
                            put("\$type", ruleType)
                        })
                    }
                })
                put("createdAt", now)
            }

            val request = buildJsonObject {
                put("repo", did)
                put("collection", "app.bsky.feed.threadgate")
                put("rkey", rkey)
                put("record", record)
            }

            val response = client.post(
                nsid = "com.atproto.repo.createRecord",
                body = request,
            )
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Threadgate failed: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a postgate record to disable quoting of a post.
     * The rkey must match the post's rkey (AT Protocol spec).
     */
    suspend fun createPostgate(postUri: String): Result<CreateRecordResponse> {
        return try {
            val did = client.session?.did
                ?: return Result.failure(Exception("Not authenticated"))
            val rkey = postUri.substringAfterLast("/")
            val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

            val record = buildJsonObject {
                put("\$type", "app.bsky.feed.postgate")
                put("post", postUri)
                put("embeddingRules", buildJsonArray {
                    add(buildJsonObject {
                        put("\$type", "app.bsky.feed.postgate#disableRule")
                    })
                })
                put("createdAt", now)
            }

            val request = buildJsonObject {
                put("repo", did)
                put("collection", "app.bsky.feed.postgate")
                put("rkey", rkey)
                put("record", record)
            }

            val response = client.post(
                nsid = "com.atproto.repo.createRecord",
                body = request,
            )
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Postgate failed: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getQuotes(
        uri: String,
        cursor: String? = null,
        limit: Int = 25,
    ): Result<GetQuotesResponse> {
        return try {
            val response = client.get(
                nsid = "app.bsky.feed.getQuotes",
                params = buildMap {
                    put("uri", uri)
                    put("limit", limit.toString())
                    if (cursor != null) put("cursor", cursor)
                },
            )
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("getQuotes failed: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildExternalEmbed(external: ExternalEmbedData): JsonElement {
        return buildJsonObject {
            put("\$type", "app.bsky.embed.external")
            put("external", buildJsonObject {
                put("uri", external.uri)
                put("title", external.title)
                put("description", external.description)
                external.thumbBlob?.let { blob ->
                    put("thumb", buildJsonObject {
                        put("\$type", "blob")
                        put("ref", buildJsonObject {
                            put("\$link", blob.link)
                        })
                        put("mimeType", blob.mimeType)
                        put("size", blob.size)
                    })
                }
            })
        }
    }

    private fun buildImagesEmbed(images: List<ImageEmbedItem>): JsonElement {
        return buildJsonObject {
            put("\$type", "app.bsky.embed.images")
            put("images", buildJsonArray {
                images.forEach { image ->
                    add(buildJsonObject {
                        put("alt", image.alt)
                        put("image", buildJsonObject {
                            put("\$type", "blob")
                            put("ref", buildJsonObject {
                                put("\$link", image.blobRef.link)
                            })
                            put("mimeType", image.blobRef.mimeType)
                            put("size", image.blobRef.size)
                        })
                        image.aspectRatio?.let { ar ->
                            put("aspectRatio", buildJsonObject {
                                put("width", ar.width)
                                put("height", ar.height)
                            })
                        }
                    })
                }
            })
        }
    }
}
