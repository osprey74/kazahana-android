package com.kazahana.app.data.remote

import com.kazahana.app.data.local.SessionStore
import com.kazahana.app.data.model.RefreshSessionResponse
import com.kazahana.app.data.model.Session
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class ATProtoClient(
    private val sessionStore: SessionStore,
) {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
        install(Logging) {
            level = LogLevel.NONE
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    /** Separate client for blob uploads — no JSON content negotiation */
    private val rawClient = HttpClient(OkHttp) {
        install(Logging) {
            level = LogLevel.NONE
        }
    }

    private val refreshMutex = Mutex()

    var pdsEndpoint: String = DEFAULT_PDS
        private set

    val session: Session?
        get() = sessionStore.load()

    fun updatePds(endpoint: String) {
        pdsEndpoint = endpoint.trimEnd('/')
    }

    /** Unauthenticated GET */
    suspend fun getUnauthenticated(
        baseUrl: String,
        nsid: String,
        params: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val url = if (nsid.isEmpty()) baseUrl else "$baseUrl/xrpc/$nsid"
        return client.get(url) {
            params.forEach { (k, v) -> parameter(k, v) }
        }
    }

    /** Unauthenticated POST */
    suspend fun postUnauthenticated(
        baseUrl: String,
        nsid: String,
        body: JsonElement,
    ): HttpResponse {
        return client.post("$baseUrl/xrpc/$nsid") {
            setBody(body)
        }
    }

    /** Authenticated GET with auto token refresh */
    suspend fun get(
        nsid: String,
        params: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val response = executeGet(nsid, params)
        if (response.status.value == 401 && refreshToken()) {
            return executeGet(nsid, params)
        }
        return response
    }

    /** Authenticated POST with auto token refresh */
    suspend fun post(
        nsid: String,
        body: JsonElement,
    ): HttpResponse {
        val response = executePost(nsid, body)
        if (response.status.value == 401 && refreshToken()) {
            return executePost(nsid, body)
        }
        return response
    }

    /** Authenticated GET with proxy header (for chat API) */
    suspend fun getWithProxy(
        nsid: String,
        params: Map<String, String> = emptyMap(),
        proxyHeader: String = CHAT_PROXY,
    ): HttpResponse {
        val response = executeGet(nsid, params, proxyHeader)
        if (response.status.value == 401 && refreshToken()) {
            return executeGet(nsid, params, proxyHeader)
        }
        return response
    }

    /** Authenticated POST with proxy header (for chat API) */
    suspend fun postWithProxy(
        nsid: String,
        body: JsonElement,
        proxyHeader: String = CHAT_PROXY,
    ): HttpResponse {
        val response = executePost(nsid, body, proxyHeader)
        if (response.status.value == 401 && refreshToken()) {
            return executePost(nsid, body, proxyHeader)
        }
        return response
    }

    private suspend fun executeGet(
        nsid: String,
        params: Map<String, String>,
        proxy: String? = null,
    ): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return client.get("$pdsEndpoint/xrpc/$nsid") {
            token?.let { header("Authorization", "Bearer $it") }
            proxy?.let { header("atproto-proxy", it) }
            params.forEach { (k, v) -> parameter(k, v) }
        }
    }

    private suspend fun executePost(
        nsid: String,
        body: JsonElement,
        proxy: String? = null,
    ): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return client.post("$pdsEndpoint/xrpc/$nsid") {
            token?.let { header("Authorization", "Bearer $it") }
            proxy?.let { header("atproto-proxy", it) }
            setBody(body)
        }
    }

    /** Refresh access token using refreshJwt */
    suspend fun refreshToken(): Boolean = refreshMutex.withLock {
        val currentSession = sessionStore.load() ?: return false
        return try {
            val response = client.post("$pdsEndpoint/xrpc/com.atproto.server.refreshSession") {
                header("Authorization", "Bearer ${currentSession.refreshJwt}")
            }
            if (response.status.isSuccess()) {
                val refreshed = response.body<RefreshSessionResponse>()
                sessionStore.save(
                    currentSession.copy(
                        accessJwt = refreshed.accessJwt,
                        refreshJwt = refreshed.refreshJwt,
                    )
                )
                true
            } else {
                sessionStore.clear()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun saveSession(session: Session) {
        sessionStore.save(session)
    }

    /** Authenticated blob upload with auto token refresh — uses raw client */
    suspend fun uploadBlob(
        data: ByteArray,
        mimeType: String,
    ): HttpResponse {
        val response = executeUpload(data, mimeType)
        if (response.status.value == 401 && refreshToken()) {
            return executeUpload(data, mimeType)
        }
        return response
    }

    private suspend fun executeUpload(
        data: ByteArray,
        mimeType: String,
    ): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return rawClient.post("$pdsEndpoint/xrpc/com.atproto.repo.uploadBlob") {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.parse(mimeType))
            setBody(data)
        }
    }

    fun clearSession() {
        sessionStore.clear()
        pdsEndpoint = DEFAULT_PDS
    }

    companion object {
        const val DEFAULT_PDS = "https://bsky.social"
        const val PUBLIC_API = "https://public.api.bsky.app"
        const val CHAT_PROXY = "did:web:api.bsky.chat#bsky_chat"
    }
}
