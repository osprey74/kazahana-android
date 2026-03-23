package com.kazahana.app.data.repository

import com.kazahana.app.data.model.AllSavedFeedItems
import com.kazahana.app.data.model.FeedGeneratorView
import com.kazahana.app.data.model.FeedResponse
import com.kazahana.app.data.model.GetFeedGeneratorsResponse
import com.kazahana.app.data.model.GetListResponse
import com.kazahana.app.data.model.GetListsResponse
import com.kazahana.app.data.model.ListView
import com.kazahana.app.data.model.PreferencesResponse
import com.kazahana.app.data.model.SavedFeedItem
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess

class FeedRepository(
    private val client: ATProtoClient,
) {
    /**
     * Fetch all saved feed items (feeds + lists) from Bluesky preferences,
     * plus the user's own curated lists. Matches iOS getAllSavedFeedItems.
     */
    suspend fun getAllSavedFeedItems(): Result<AllSavedFeedItems> {
        return try {
            // Step 1: Get preferences
            val response = client.get(nsid = "app.bsky.actor.getPreferences")
            if (!response.status.isSuccess()) {
                return Result.failure(Exception(response.atprotoError()))
            }
            val prefs = response.body<PreferencesResponse>()

            var feedURIs = mutableListOf<String>()
            var listURIs = mutableListOf<String>()

            // Try v2 format first
            val v2Pref = prefs.preferences
                .firstOrNull { it.type == "app.bsky.actor.defs#savedFeedsPrefV2" }
            if (v2Pref?.items != null) {
                for (item in v2Pref.items) {
                    when (item.type) {
                        "feed" -> feedURIs.add(item.value)
                        "list" -> listURIs.add(item.value)
                    }
                }
            } else {
                // Fallback to v1 format
                val v1Pref = prefs.preferences
                    .firstOrNull { it.type == "app.bsky.actor.defs#savedFeedsPref" }
                val pinned = v1Pref?.pinned.orEmpty()
                val saved = v1Pref?.saved.orEmpty()
                feedURIs.addAll(
                    (pinned + saved)
                        .filter { it.startsWith("at://") && it.contains("/app.bsky.feed.generator/") }
                )
            }

            // Deduplicate
            feedURIs = feedURIs.distinct().toMutableList()
            listURIs = listURIs.distinct().toMutableList()

            // Step 2: Batch fetch feed generators
            val feeds = mutableListOf<FeedGeneratorView>()
            if (feedURIs.isNotEmpty()) {
                for (chunk in feedURIs.chunked(25)) {
                    try {
                        val genResponse = client.getMultiParam(
                            nsid = "app.bsky.feed.getFeedGenerators",
                            params = mapOf("feeds" to chunk),
                        )
                        if (genResponse.status.isSuccess()) {
                            feeds.addAll(genResponse.body<GetFeedGeneratorsResponse>().feeds)
                        }
                    } catch (_: Exception) { /* skip failed chunks */ }
                }
            }

            // Step 3: Fetch saved lists individually (include all purposes — caller filters)
            val savedLists = mutableListOf<ListView>()
            for (listURI in listURIs) {
                try {
                    val listResponse = client.get(
                        nsid = "app.bsky.graph.getList",
                        params = mapOf("list" to listURI, "limit" to "1"),
                    )
                    if (listResponse.status.isSuccess()) {
                        savedLists.add(listResponse.body<GetListResponse>().list)
                    }
                } catch (_: Exception) { /* skip failed lists */ }
            }

            // Step 4: Merge user's own curated lists (like iOS getMyLists)
            val actorDid = client.session?.did
            if (actorDid != null) {
                try {
                    val myListsResponse = client.get(
                        nsid = "app.bsky.graph.getLists",
                        params = mapOf("actor" to actorDid, "limit" to "100"),
                    )
                    if (myListsResponse.status.isSuccess()) {
                        val myLists = myListsResponse.body<GetListsResponse>().lists
                        val seenURIs = savedLists.map { it.uri }.toMutableSet()
                        for (list in myLists) {
                            if (seenURIs.add(list.uri)) {
                                savedLists.add(list)
                            }
                        }
                    }
                } catch (_: Exception) { /* skip if user lists fail */ }
            }

            Result.success(AllSavedFeedItems(feeds = feeds, lists = savedLists))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeedGenerators(feeds: List<String>): Result<List<FeedGeneratorView>> {
        return try {
            val allGenerators = mutableListOf<FeedGeneratorView>()
            for (chunk in feeds.chunked(25)) {
                val response = client.getMultiParam(
                    nsid = "app.bsky.feed.getFeedGenerators",
                    params = mapOf("feeds" to chunk),
                )
                if (response.status.isSuccess()) {
                    val body = response.body<GetFeedGeneratorsResponse>()
                    allGenerators.addAll(body.feeds)
                }
            }
            Result.success(allGenerators)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getListInfo(listUri: String): Result<ListView> {
        return try {
            val response = client.get(
                nsid = "app.bsky.graph.getList",
                params = mapOf("list" to listUri, "limit" to "1"),
            )
            if (response.status.isSuccess()) {
                Result.success(response.body<GetListResponse>().list)
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

    suspend fun getListFeed(
        listUri: String,
        cursor: String? = null,
        limit: Int = 30,
    ): Result<FeedResponse> {
        return try {
            val params = buildMap {
                put("list", listUri)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.feed.getListFeed",
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
