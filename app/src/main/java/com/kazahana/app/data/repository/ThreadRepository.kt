package com.kazahana.app.data.repository

import com.kazahana.app.data.model.ThreadResponse
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess

class ThreadRepository(
    private val client: ATProtoClient,
) {
    suspend fun getPostThread(
        uri: String,
        depth: Int = 10,
        parentHeight: Int = 80,
    ): Result<ThreadResponse> {
        return try {
            val response = client.get(
                nsid = "app.bsky.feed.getPostThread",
                params = buildMap {
                    put("uri", uri)
                    put("depth", depth.toString())
                    put("parentHeight", parentHeight.toString())
                },
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
