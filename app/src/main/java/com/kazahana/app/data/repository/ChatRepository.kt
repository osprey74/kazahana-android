package com.kazahana.app.data.repository

import com.kazahana.app.data.model.ConvoListResponse
import com.kazahana.app.data.model.ConvoView
import com.kazahana.app.data.model.GetConvoResponse
import com.kazahana.app.data.model.JoinLinkPreviewState
import com.kazahana.app.data.model.JoinLinkPreviewsResponse
import com.kazahana.app.data.model.JoinLinkResponse
import com.kazahana.app.data.model.JoinLinkView
import com.kazahana.app.data.model.JoinRequestsResponse
import com.kazahana.app.data.model.MessageListResponse
import com.kazahana.app.data.model.ReactionResponse
import com.kazahana.app.data.model.RequestJoinResponse
import com.kazahana.app.data.model.SendMessageResponse
import com.kazahana.app.data.model.toJoinLinkPreviewState
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import com.kazahana.app.data.remote.atprotoErrorName
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
            // 1:1 convo lookup — `members` takes a single recipient DID here.
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

    // --- Group join (Phase 2) ---

    /** Preview a single join link by its code. */
    suspend fun getJoinLinkPreview(code: String): Result<JoinLinkPreviewState> {
        return try {
            val response = client.getWithProxy(
                "chat.bsky.group.getJoinLinkPreviews",
                mapOf("codes" to code),
            )
            if (response.status.isSuccess()) {
                val body = response.body<JoinLinkPreviewsResponse>()
                val state = body.joinLinkPreviews.firstOrNull()?.toJoinLinkPreviewState()
                    ?: JoinLinkPreviewState.Invalid
                Result.success(state)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Request to join a group via its invite code. Returns status joined|pending (+ convo when joined). */
    suspend fun requestJoin(code: String): Result<RequestJoinResponse> {
        return try {
            val body = buildJsonObject {
                put("code", code)
            }
            val response = client.postWithProxy("chat.bsky.group.requestJoin", body)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                // Surface the error code (e.g. ConvoLocked) so the UI can localize it.
                val name = response.atprotoErrorName()
                Result.failure(Exception(name ?: "RequestJoinFailed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Withdraw a pending join request for a group. */
    suspend fun withdrawJoinRequest(convoId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
            }
            val response = client.postWithProxy("chat.bsky.group.withdrawJoinRequest", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Group creation & owner operations (Phase 3) ---

    /** Common handler for procedures returning `{ convo }`. */
    private suspend fun convoResult(nsid: String, body: kotlinx.serialization.json.JsonObject): Result<ConvoView> {
        return try {
            val response = client.postWithProxy(nsid, body)
            if (response.status.isSuccess()) {
                Result.success(response.body<GetConvoResponse>().convo)
            } else {
                Result.failure(Exception(response.atprotoErrorName() ?: response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Common handler for procedures returning `{ joinLink }`. */
    private suspend fun joinLinkResult(nsid: String, body: kotlinx.serialization.json.JsonObject): Result<JoinLinkView?> {
        return try {
            val response = client.postWithProxy(nsid, body)
            if (response.status.isSuccess()) {
                Result.success(response.body<JoinLinkResponse>().joinLink)
            } else {
                Result.failure(Exception(response.atprotoErrorName() ?: response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroup(name: String, members: List<String>): Result<ConvoView> =
        convoResult("chat.bsky.group.createGroup", buildJsonObject {
            put("name", name)
            put("members", buildJsonArray { members.forEach { add(JsonPrimitive(it)) } })
        })

    suspend fun editGroup(convoId: String, name: String): Result<ConvoView> =
        convoResult("chat.bsky.group.editGroup", buildJsonObject {
            put("convoId", convoId)
            put("name", name)
        })

    suspend fun addMembers(convoId: String, members: List<String>): Result<ConvoView> =
        convoResult("chat.bsky.group.addMembers", buildJsonObject {
            put("convoId", convoId)
            put("members", buildJsonArray { members.forEach { add(JsonPrimitive(it)) } })
        })

    suspend fun removeMembers(convoId: String, members: List<String>): Result<ConvoView> =
        convoResult("chat.bsky.group.removeMembers", buildJsonObject {
            put("convoId", convoId)
            put("members", buildJsonArray { members.forEach { add(JsonPrimitive(it)) } })
        })

    suspend fun createJoinLink(
        convoId: String,
        joinRule: String = "anyone",
        requireApproval: Boolean = false,
    ): Result<JoinLinkView?> =
        joinLinkResult("chat.bsky.group.createJoinLink", buildJsonObject {
            put("convoId", convoId)
            put("joinRule", joinRule)
            put("requireApproval", requireApproval)
        })

    suspend fun enableJoinLink(convoId: String): Result<JoinLinkView?> =
        joinLinkResult("chat.bsky.group.enableJoinLink", buildJsonObject { put("convoId", convoId) })

    suspend fun disableJoinLink(convoId: String): Result<JoinLinkView?> =
        joinLinkResult("chat.bsky.group.disableJoinLink", buildJsonObject { put("convoId", convoId) })

    suspend fun lockConvo(convoId: String): Result<ConvoView> =
        convoResult("chat.bsky.convo.lockConvo", buildJsonObject { put("convoId", convoId) })

    suspend fun unlockConvo(convoId: String): Result<ConvoView> =
        convoResult("chat.bsky.convo.unlockConvo", buildJsonObject { put("convoId", convoId) })

    suspend fun approveJoinRequest(convoId: String, member: String): Result<ConvoView> =
        convoResult("chat.bsky.group.approveJoinRequest", buildJsonObject {
            put("convoId", convoId)
            put("member", member)
        })

    suspend fun rejectJoinRequest(convoId: String, member: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("convoId", convoId)
                put("member", member)
            }
            val response = client.postWithProxy("chat.bsky.group.rejectJoinRequest", body)
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception(response.atprotoError()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateJoinRequestsRead(convoId: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("convoId", convoId) }
            val response = client.postWithProxy("chat.bsky.group.updateJoinRequestsRead", body)
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception(response.atprotoError()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listJoinRequests(convoId: String, cursor: String? = null): Result<JoinRequestsResponse> {
        return try {
            val params = buildMap {
                put("convoId", convoId)
                put("limit", "50")
                if (cursor != null) put("cursor", cursor)
            }
            val response = client.getWithProxy("chat.bsky.group.listJoinRequests", params)
            if (response.status.isSuccess()) Result.success(response.body())
            else Result.failure(Exception(response.atprotoError()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
