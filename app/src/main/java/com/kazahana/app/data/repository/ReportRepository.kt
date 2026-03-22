package com.kazahana.app.data.repository

import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.atprotoError
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReportRepository(
    private val client: ATProtoClient,
) {
    suspend fun reportPost(
        postUri: String,
        postCid: String,
        reasonType: String,
        reason: String = "",
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("reasonType", "com.atproto.moderation.defs#$reasonType")
                if (reason.isNotBlank()) put("reason", reason)
                put("subject", buildJsonObject {
                    put("\$type", "com.atproto.repo.strongRef")
                    put("uri", postUri)
                    put("cid", postCid)
                })
            }
            val response = client.post("com.atproto.moderation.createReport", body)
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.atprotoError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportAccount(
        did: String,
        reasonType: String,
        reason: String = "",
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("reasonType", "com.atproto.moderation.defs#$reasonType")
                if (reason.isNotBlank()) put("reason", reason)
                put("subject", buildJsonObject {
                    put("\$type", "com.atproto.admin.defs#repoRef")
                    put("did", did)
                })
            }
            val response = client.post("com.atproto.moderation.createReport", body)
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
