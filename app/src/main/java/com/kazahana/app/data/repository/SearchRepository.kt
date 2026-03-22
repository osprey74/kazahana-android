package com.kazahana.app.data.repository

import com.kazahana.app.data.model.SearchActorsResponse
import com.kazahana.app.data.model.SearchPostsResponse
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess

class SearchRepository(
    private val client: ATProtoClient,
) {
    suspend fun searchPosts(
        query: String,
        cursor: String? = null,
        limit: Int = 25,
    ): Result<SearchPostsResponse> {
        return try {
            val params = buildMap {
                put("q", query)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.feed.searchPosts",
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

    suspend fun searchActors(
        query: String,
        cursor: String? = null,
        limit: Int = 25,
    ): Result<SearchActorsResponse> {
        return try {
            val params = buildMap {
                put("q", query)
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.actor.searchActors",
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
