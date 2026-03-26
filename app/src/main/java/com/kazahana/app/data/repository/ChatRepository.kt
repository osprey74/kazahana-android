package com.kazahana.app.data.repository

import com.kazahana.app.data.model.ConvoListResponse
import com.kazahana.app.data.model.ConvoView
import com.kazahana.app.data.model.GetConvoResponse
import com.kazahana.app.data.model.MessageListResponse
import com.kazahana.app.data.model.ReactionResponse
import com.kazahana.app.data.model.SendMessageResponse
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatRepository(
    private val client: ATProtoClient,
) {
    suspend fun listConvos(cursor: String? = null): Result<ConvoListResponse> {
        return try {
            val params = buildMap {
                put("limit", "50")
                if (cursor != null) put("cursor", cursor)
            }
            val response = client.getWithProxy("chat.bsky.convo.listConvos", params)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConvo(convoId: String): Result<ConvoView> {
        return try {
            val response = client.getWithProxy(
                "chat.bsky.convo.getConvo",
                mapOf("convoId" to convoId),
            )
            if (response.status.isSuccess()) {
                val body = response.body<GetConvoResponse>()
                Result.success(body.convo)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(convoId: String, cursor: String? = null): Result<MessageListResponse> {
        return try {
            val params = buildMap {
                put("convoId", convoId)
                put("limit", "50")
                if (cursor != null) put("cursor", cursor)
            }
            val response = client.getWithProxy("chat.bsky.convo.getMessages", params)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(convoId: String, text: String): Result<SendMessageResponse> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
                put("message", buildJsonObject {
                    put("text", text)
                })
            }
            val response = client.postWithProxy("chat.bsky.convo.sendMessage", body)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConvoForMembers(did: String): Result<ConvoView> {
        return try {
            val body = buildJsonObject {
                put("members", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(did))
                })
            }
            val response = client.getWithProxy(
                "chat.bsky.convo.getConvoForMembers",
                mapOf("members" to did),
            )
            if (response.status.isSuccess()) {
                val resp = response.body<GetConvoResponse>()
                Result.success(resp.convo)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRead(convoId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
            }
            val response = client.postWithProxy("chat.bsky.convo.updateRead", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun muteConvo(convoId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
            }
            val response = client.postWithProxy("chat.bsky.convo.muteConvo", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unmuteConvo(convoId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
            }
            val response = client.postWithProxy("chat.bsky.convo.unmuteConvo", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReaction(convoId: String, messageId: String, value: String): Result<ReactionResponse> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
                put("messageId", messageId)
                put("value", value)
            }
            val response = client.postWithProxy("chat.bsky.convo.addReaction", body)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeReaction(convoId: String, messageId: String, value: String): Result<ReactionResponse> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
                put("messageId", messageId)
                put("value", value)
            }
            val response = client.postWithProxy("chat.bsky.convo.removeReaction", body)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForSelf(convoId: String, messageId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
                put("messageId", messageId)
            }
            val response = client.postWithProxy("chat.bsky.convo.deleteMessageForSelf", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptConvo(convoId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
            }
            val response = client.postWithProxy("chat.bsky.convo.acceptConvo", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveConvo(convoId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
            }
            val response = client.postWithProxy("chat.bsky.convo.leaveConvo", body)
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
