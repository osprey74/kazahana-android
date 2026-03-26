package com.kazahana.app.data.repository

import com.kazahana.app.data.model.CreateRecordResponse
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /** Mute a user account */
    suspend fun muteActor(did: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("actor", did) }
            val response = client.post("app.bsky.graph.muteActor", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Unmute a user account */
    suspend fun unmuteActor(did: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("actor", did) }
            val response = client.post("app.bsky.graph.unmuteActor", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Block a user account */
    suspend fun blockActor(did: String): Result<CreateRecordResponse> {
        val repo = did() ?: return Result.failure(Exception("Not authenticated"))
        val record = buildJsonObject {
            put("\$type", "app.bsky.graph.block")
            put("subject", did)
            put("createdAt", now())
        }
        return createRecord(repo, "app.bsky.graph.block", record)
    }

    /** Unblock a user account */
    suspend fun unblockActor(blockUri: String): Result<Unit> {
        return deleteRecord(blockUri)
    }

    /** Mute thread notifications */
    suspend fun muteThread(rootUri: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("root", rootUri) }
            val response = client.post("app.bsky.graph.muteThread", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Unmute thread notifications */
    suspend fun unmuteThread(rootUri: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("root", rootUri) }
            val response = client.post("app.bsky.graph.unmuteThread", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hide a post by adding it to hiddenPosts preferences.
     * Fetches current preferences, modifies the hiddenPostsPref, and saves back.
     */
    suspend fun hidePost(postUri: String): Result<Unit> {
        return updateHiddenPosts(postUri, add = true)
    }

    /** Unhide a post by removing it from hiddenPosts preferences. */
    suspend fun unhidePost(postUri: String): Result<Unit> {
        return updateHiddenPosts(postUri, add = false)
    }

    private suspend fun updateHiddenPosts(postUri: String, add: Boolean): Result<Unit> {
        return try {
            // 1. Get current preferences
            val getResp = client.get(
                "app.bsky.actor.getPreferences",
                params = emptyMap(),
            )
            if (!getResp.status.isSuccess()) {
                return Result.failure(Exception(getResp.atprotoError()))
            }
            val prefsJson = getResp.body<JsonElement>().jsonObject
            val preferences = prefsJson["preferences"]?.jsonArray ?: JsonArray(emptyList())

            // 2. Find or create hiddenPostsPref
            val hiddenPrefType = "app.bsky.actor.defs#hiddenPostsPref"
            val existingIndex = preferences.indexOfFirst {
                it.jsonObject["\$type"]?.jsonPrimitive?.content == hiddenPrefType
            }

            val currentItems = if (existingIndex >= 0) {
                preferences[existingIndex].jsonObject["items"]?.jsonArray
                    ?.map { it.jsonPrimitive.content } ?: emptyList()
            } else {
                emptyList()
            }

            val newItems = if (add) {
                if (postUri in currentItems) currentItems else currentItems + postUri
            } else {
                currentItems.filter { it != postUri }
            }

            val newPref = buildJsonObject {
                put("\$type", hiddenPrefType)
                put("items", buildJsonArray { newItems.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }

            // 3. Build updated preferences array
            val newPreferences = buildJsonArray {
                preferences.forEachIndexed { index, element ->
                    if (index == existingIndex) add(newPref) else add(element)
                }
                if (existingIndex < 0) add(newPref)
            }

            // 4. Save preferences
            val putBody = buildJsonObject { put("preferences", newPreferences) }
            val putResp = client.post("app.bsky.actor.putPreferences", putBody)
            if (putResp.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(putResp.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Add a user to a list */
    suspend fun addToList(listUri: String, subjectDid: String): Result<CreateRecordResponse> {
        val repo = did() ?: return Result.failure(Exception("Not authenticated"))
        val record = buildJsonObject {
            put("\$type", "app.bsky.graph.listitem")
            put("subject", subjectDid)
            put("list", listUri)
            put("createdAt", now())
        }
        return createRecord(repo, "app.bsky.graph.listitem", record)
    }

    /** Remove a user from a list */
    suspend fun removeFromList(listItemUri: String): Result<Unit> {
        return deleteRecord(listItemUri)
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
                Result.failure(Exception(response.atprotoError()))
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
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
