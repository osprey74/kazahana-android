package com.kazahana.app.data.repository

import com.kazahana.app.data.model.CreateRecordResponse
import com.kazahana.app.data.remote.ATProtoClient
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class InteractionRepository(
    private val client: ATProtoClient,
) {
    private fun now(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

    private fun did(): String? = client.session?.did

    suspend fun like(uri: String, cid: String): Result<CreateRecordResponse> {
        val repo = did() ?: return Result.failure(Exception("Not authenticated"))
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.like")
            put("subject", buildJsonObject {
                put("uri", uri)
                put("cid", cid)
            })
            put("createdAt", now())
        }
        return createRecord(repo, "app.bsky.feed.like", record)
    }

    suspend fun unlike(likeUri: String): Result<Unit> {
        return deleteRecord(likeUri)
    }

    suspend fun repost(uri: String, cid: String): Result<CreateRecordResponse> {
        val repo = did() ?: return Result.failure(Exception("Not authenticated"))
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.repost")
            put("subject", buildJsonObject {
                put("uri", uri)
                put("cid", cid)
            })
            put("createdAt", now())
        }
        return createRecord(repo, "app.bsky.feed.repost", record)
    }

    suspend fun unrepost(repostUri: String): Result<Unit> {
        return deleteRecord(repostUri)
    }

    suspend fun bookmark(uri: String, cid: String): Result<CreateRecordResponse> {
        val repo = did() ?: return Result.failure(Exception("Not authenticated"))
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.bookmark")
            put("subject", buildJsonObject {
                put("uri", uri)
                put("cid", cid)
            })
            put("createdAt", now())
        }
        return createRecord(repo, "app.bsky.feed.bookmark", record)
    }

    suspend fun unbookmark(bookmarkUri: String): Result<Unit> {
        return deleteRecord(bookmarkUri)
    }

    private suspend fun createRecord(
        repo: String,
        collection: String,
        record: JsonElement,
    ): Result<CreateRecordResponse> {
        return try {
            val body = buildJsonObject {
                put("repo", repo)
                put("collection", collection)
                put("record", record)
            }
            val response = client.post("com.atproto.repo.createRecord", body)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteRecord(recordUri: String): Result<Unit> {
        return try {
            // at://did:plc:xxx/collection/rkey
            val parts = recordUri.removePrefix("at://").split("/")
            if (parts.size < 3) return Result.failure(Exception("Invalid URI"))
            val repo = parts[0]
            val collection = parts[1]
            val rkey = parts[2]

            val body = buildJsonObject {
                put("repo", repo)
                put("collection", collection)
                put("rkey", rkey)
            }
            val response = client.post("com.atproto.repo.deleteRecord", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
