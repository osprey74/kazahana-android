package com.kazahana.app.data.repository

import com.kazahana.app.data.model.FeedGeneratorView
import com.kazahana.app.data.model.FeedResponse
import com.kazahana.app.data.model.GetFeedGeneratorsResponse
import com.kazahana.app.data.model.PreferencesResponse
import com.kazahana.app.data.model.SavedFeedItem
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess

class FeedRepository(
    private val client: ATProtoClient,
) {
    suspend fun getSavedFeeds(): Result<List<SavedFeedItem>> {
        return try {
            val response = client.get(nsid = "app.bsky.actor.getPreferences")
            if (response.status.isSuccess()) {
                val prefs = response.body<PreferencesResponse>()
                val savedFeeds = prefs.preferences
                    .firstOrNull { it.type == "app.bsky.actor.defs#savedFeedsPrefV2" }
                    ?.items
                    ?.filter { it.pinned }
                    ?: emptyList()
                Result.success(savedFeeds)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeedGenerators(feeds: List<String>): Result<List<FeedGeneratorView>> {
        return try {
            val response = client.getMultiParam(
                nsid = "app.bsky.feed.getFeedGenerators",
                params = mapOf("feeds" to feeds),
            )
            if (response.status.isSuccess()) {
                val body = response.body<GetFeedGeneratorsResponse>()
                Result.success(body.feeds)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeed(
        feedUri: String,
        cursor: String? = null,
        limit: Int = 30,
    ): Result<FeedResponse> {
        return try {
            val params = buildMap {
                put("feed", feedUri)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.feed.getFeed",
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
}
