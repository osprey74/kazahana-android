package com.kazahana.app.data.repository

import com.kazahana.app.data.model.GetPostsResponse
import com.kazahana.app.data.model.GetRecordResponse
import com.kazahana.app.data.model.NotificationListResponse
import com.kazahana.app.data.model.PostView
import com.kazahana.app.data.model.UnreadCountResponse
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationRepository(
    private val client: ATProtoClient,
) {
    suspend fun listNotifications(
        cursor: String? = null,
        limit: Int = 30,
    ): Result<NotificationListResponse> {
        return try {
            val params = buildMap {
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.notification.listNotifications",
                params = params,
            )
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUnreadCount(): Result<Int> {
        return try {
            val response = client.get(
                nsid = "app.bsky.notification.getUnreadCount",
            )
            if (response.status.isSuccess()) {
                val body = response.body<UnreadCountResponse>()
                Result.success(body.count)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch a single record (e.g. repost record to resolve original post URI) */
    suspend fun getRecord(repo: String, collection: String, rkey: String): Result<GetRecordResponse> {
        return try {
            val response = client.get(
                nsid = "com.atproto.repo.getRecord",
                params = mapOf("repo" to repo, "collection" to collection, "rkey" to rkey),
            )
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch multiple posts by URI (up to 25 per call) */
    suspend fun getPosts(uris: List<String>): Result<List<PostView>> {
        if (uris.isEmpty()) return Result.success(emptyList())
        return try {
            val response = client.getMultiParam(
                nsid = "app.bsky.feed.getPosts",
                params = mapOf("uris" to uris),
            )
            if (response.status.isSuccess()) {
                Result.success(response.body<GetPostsResponse>().posts)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSeen(): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("seenAt", java.time.Instant.now().toString())
            }
            val response = client.post(
                nsid = "app.bsky.notification.updateSeen",
                body = body,
            )
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
