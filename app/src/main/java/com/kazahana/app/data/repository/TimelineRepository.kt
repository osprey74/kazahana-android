package com.kazahana.app.data.repository

import com.kazahana.app.data.model.TimelineResponse
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess
class TimelineRepository(
    private val client: ATProtoClient,
) {
    suspend fun getTimeline(
        cursor: String? = null,
        limit: Int = 30,
    ): Result<TimelineResponse> {
        return try {
            val params = buildMap {
                put("limit", limit.toString())
                cursor?.let { put("cursor", it) }
            }
            val response = client.get(
                nsid = "app.bsky.feed.getTimeline",
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
