package com.kazahana.app.data.repository

import com.kazahana.app.data.model.AuthorFeedResponse
import com.kazahana.app.data.model.CreateRecordResponse
import com.kazahana.app.data.model.GetActorFeedsResponse
import com.kazahana.app.data.model.GetActorStarterPacksResponse
import com.kazahana.app.data.model.GetListsResponse
import com.kazahana.app.data.model.GetPostsResponse
import com.kazahana.app.data.model.PostView
import com.kazahana.app.data.model.ProfileViewDetailed
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ProfileRepository(
    private val client: ATProtoClient,
) {
    private fun now(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

    suspend fun getProfile(actor: String): Result<ProfileViewDetailed> {
        return try {
            val response = client.get(
                nsid = "app.bsky.actor.getProfile",
                params = mapOf("actor" to actor),
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

    suspend fun getAuthorFeed(
        actor: String,
        filter: String = "posts_no_replies",
        cursor: String? = null,
        limit: Int = 30,
    ): Result<AuthorFeedResponse> {
        return try {
            val params = buildMap {
                put("actor", actor)
                put("filter", filter)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.feed.getAuthorFeed",
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

    suspend fun getActorLikes(
        actor: String,
        cursor: String? = null,
        limit: Int = 30,
    ): Result<AuthorFeedResponse> {
        return try {
            val params = buildMap {
                put("actor", actor)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.feed.getActorLikes",
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

    suspend fun follow(did: String): Result<CreateRecordResponse> {
        val repo = client.session?.did ?: return Result.failure(Exception("Not authenticated"))
        val record = buildJsonObject {
            put("\$type", "app.bsky.graph.follow")
            put("subject", did)
            put("createdAt", now())
        }
        return try {
            val body = buildJsonObject {
                put("repo", repo)
                put("collection", "app.bsky.graph.follow")
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

    suspend fun getActorFeeds(
        actor: String,
        cursor: String? = null,
        limit: Int = 50,
    ): Result<GetActorFeedsResponse> {
        return try {
            val params = buildMap {
                put("actor", actor)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.feed.getActorFeeds",
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

    suspend fun getActorLists(
        actor: String,
        cursor: String? = null,
        limit: Int = 50,
    ): Result<GetListsResponse> {
        return try {
            val params = buildMap {
                put("actor", actor)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.graph.getLists",
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

    suspend fun getActorStarterPacks(
        actor: String,
        cursor: String? = null,
        limit: Int = 50,
    ): Result<GetActorStarterPacksResponse> {
        return try {
            val params = buildMap {
                put("actor", actor)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.graph.getActorStarterPacks",
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

    suspend fun unfollow(followUri: String): Result<Unit> {
        return try {
            val parts = followUri.removePrefix("at://").split("/")
            if (parts.size < 3) return Result.failure(Exception("Invalid URI"))
            val body = buildJsonObject {
                put("repo", parts[0])
                put("collection", parts[1])
                put("rkey", parts[2])
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
