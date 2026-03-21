package com.kazahana.app.data.repository

import com.kazahana.app.data.model.BlobResponse
import com.kazahana.app.data.model.CreateRecordResponse
import com.kazahana.app.data.model.Facet
import com.kazahana.app.data.model.ImageEmbedItem
import io.ktor.client.statement.bodyAsText
import com.kazahana.app.data.model.PostReplyRef
import com.kazahana.app.data.remote.ATProtoClient
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class PostRepository(
    private val client: ATProtoClient,
) {
    private val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun uploadBlob(
        data: ByteArray,
        mimeType: String,
    ): Result<BlobResponse> {
        return try {
            val response = client.uploadBlob(data, mimeType)
            if (response.status.isSuccess()) {
                // rawClient has no ContentNegotiation, parse manually
                val text = response.bodyAsText()
                val blob = jsonParser.decodeFromString<BlobResponse>(text)
                Result.success(blob)
            } else {
                Result.failure(Exception("Upload failed: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPost(
        text: String,
        facets: List<Facet> = emptyList(),
        images: List<ImageEmbedItem> = emptyList(),
        reply: PostReplyRef? = null,
        quoteUri: String? = null,
        quoteCid: String? = null,
        langs: List<String> = listOf("ja"),
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
                // Build embed: images only, quote only, or images + quote (recordWithMedia)
                val hasImages = images.isNotEmpty()
                val hasQuote = quoteUri != null && quoteCid != null
                when {
                    hasImages && hasQuote -> {
                        put("embed", buildRecordWithMediaEmbed(images, quoteUri!!, quoteCid!!))
                    }
                    hasImages -> {
                        put("embed", buildImagesEmbed(images))
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
