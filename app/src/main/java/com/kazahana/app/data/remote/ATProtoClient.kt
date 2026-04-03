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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException

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
    @Volatile private var lastRefreshedAt = 0L

    var pdsEndpoint: String = DEFAULT_PDS
        private set

    val session: Session?
        get() = sessionStore.load()

    fun updatePds(endpoint: String) {
        pdsEndpoint = endpoint.trimEnd('/')
    }

    /** Replace the active session (for account switching). */
    fun updateSession(session: Session) {
        sessionStore.activeAccountDID = session.did
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

    /** Check if response indicates an expired token (401 or 400, which includes ExpiredToken) */
    private fun shouldRefreshToken(response: HttpResponse): Boolean {
        val status = response.status.value
        return status == 401 || status == 400
    }

    /**
     * Execute a request with PDS fallback.
     * If the primary PDS endpoint fails with a network error (DNS, connection, etc.),
     * retry the request against DEFAULT_PDS (bsky.social entryway).
     */
    private suspend fun <T> withPdsFallback(block: suspend (endpoint: String) -> T): T {
        return try {
            block(pdsEndpoint)
        } catch (e: IOException) {
            if (pdsEndpoint != DEFAULT_PDS) {
                block(DEFAULT_PDS)
            } else {
                throw e
            }
        }
    }

    /** Authenticated GET with auto token refresh */
    suspend fun get(
        nsid: String,
        params: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val response = withPdsFallback { ep -> executeGet(ep, nsid, params) }
        if (shouldRefreshToken(response) && refreshToken()) {
            return withPdsFallback { ep -> executeGet(ep, nsid, params) }
        }
        return response
    }

    /** Authenticated GET with multi-value params (e.g. feeds=a&feeds=b) */
    suspend fun getMultiParam(
        nsid: String,
        params: Map<String, List<String>> = emptyMap(),
    ): HttpResponse {
        val response = withPdsFallback { ep -> executeGetMulti(ep, nsid, params) }
        if (shouldRefreshToken(response) && refreshToken()) {
            return withPdsFallback { ep -> executeGetMulti(ep, nsid, params) }
        }
        return response
    }

    /** Authenticated POST with auto token refresh */
    suspend fun post(
        nsid: String,
        body: JsonElement,
    ): HttpResponse {
        val response = withPdsFallback { ep -> executePost(ep, nsid, body) }
        if (shouldRefreshToken(response) && refreshToken()) {
            return withPdsFallback { ep -> executePost(ep, nsid, body) }
        }
        return response
    }

    /** Authenticated GET with proxy header (for chat API) */
    suspend fun getWithProxy(
        nsid: String,
        params: Map<String, String> = emptyMap(),
        proxyHeader: String = CHAT_PROXY,
    ): HttpResponse {
        val response = withPdsFallback { ep -> executeGet(ep, nsid, params, proxyHeader) }
        if (shouldRefreshToken(response) && refreshToken()) {
            return withPdsFallback { ep -> executeGet(ep, nsid, params, proxyHeader) }
        }
        return response
    }

    /** Authenticated POST with proxy header (for chat API) */
    suspend fun postWithProxy(
        nsid: String,
        body: JsonElement,
        proxyHeader: String = CHAT_PROXY,
    ): HttpResponse {
        val response = withPdsFallback { ep -> executePost(ep, nsid, body, proxyHeader) }
        if (shouldRefreshToken(response) && refreshToken()) {
            return withPdsFallback { ep -> executePost(ep, nsid, body, proxyHeader) }
        }
        return response
    }

    private suspend fun executeGet(
        endpoint: String,
        nsid: String,
        params: Map<String, String>,
        proxy: String? = null,
    ): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return client.get("$endpoint/xrpc/$nsid") {
            token?.let { header("Authorization", "Bearer $it") }
            proxy?.let { header("atproto-proxy", it) }
            params.forEach { (k, v) -> parameter(k, v) }
        }
    }

    private suspend fun executeGetMulti(
        endpoint: String,
        nsid: String,
        params: Map<String, List<String>>,
    ): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return client.get("$endpoint/xrpc/$nsid") {
            token?.let { header("Authorization", "Bearer $it") }
            params.forEach { (k, values) ->
                values.forEach { v -> parameter(k, v) }
            }
        }
    }

    private suspend fun executePost(
        endpoint: String,
        nsid: String,
        body: JsonElement,
        proxy: String? = null,
    ): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return client.post("$endpoint/xrpc/$nsid") {
            token?.let { header("Authorization", "Bearer $it") }
            proxy?.let { header("atproto-proxy", it) }
            setBody(body)
        }
    }

    /** Refresh access token using refreshJwt — with PDS fallback */
    suspend fun refreshToken(): Boolean = refreshMutex.withLock {
        // If another coroutine already refreshed recently, skip
        val now = System.currentTimeMillis()
        if (now - lastRefreshedAt < 5_000) return true

        val currentSession = sessionStore.load() ?: return false
        return try {
            suspend fun doRefresh(endpoint: String): HttpResponse {
                return client.post("$endpoint/xrpc/com.atproto.server.refreshSession") {
                    header("Authorization", "Bearer ${currentSession.refreshJwt}")
                }
            }
            val response = try {
                doRefresh(pdsEndpoint)
            } catch (e: IOException) {
                if (pdsEndpoint != DEFAULT_PDS) doRefresh(DEFAULT_PDS) else throw e
            }
            if (response.status.isSuccess()) {
                val refreshed = response.body<RefreshSessionResponse>()
                sessionStore.save(
                    currentSession.copy(
                        accessJwt = refreshed.accessJwt,
                        refreshJwt = refreshed.refreshJwt,
                    )
                )
                lastRefreshedAt = System.currentTimeMillis()
                true
            } else {
                // Only remove the failed session, not all sessions
                sessionStore.delete(currentSession.did)
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
        val response = withPdsFallback { ep -> executeUpload(ep, data, mimeType) }
        if (shouldRefreshToken(response) && refreshToken()) {
            return withPdsFallback { ep -> executeUpload(ep, data, mimeType) }
        }
        return response
    }

    private suspend fun executeUpload(
        endpoint: String,
        data: ByteArray,
        mimeType: String,
    ): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return rawClient.post("$endpoint/xrpc/com.atproto.repo.uploadBlob") {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.parse(mimeType))
            setBody(data)
        }
    }

    /**
     * Get a service auth token for video.bsky.app uploads.
     * Returns the JWT token string, or null on failure.
     */
    suspend fun getServiceAuth(aud: String, lxm: String, expSecs: Int = 1800): String? {
        return try {
            val exp = (System.currentTimeMillis() / 1000 + expSecs).toString()
            val response = get(
                nsid = "com.atproto.server.getServiceAuth",
                params = mapOf("aud" to aud, "lxm" to lxm, "exp" to exp),
            )
            if (response.status.isSuccess()) {
                val body = response.body<ServiceAuthResponse>()
                body.token
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Upload video to video.bsky.app using a service auth token.
     */
    suspend fun uploadVideo(
        data: ByteArray,
        mimeType: String,
        did: String,
        fileName: String,
        serviceToken: String,
    ): HttpResponse {
        return rawClient.post("$VIDEO_SERVICE/xrpc/app.bsky.video.uploadVideo") {
            parameter("did", did)
            parameter("name", fileName)
            header("Authorization", "Bearer $serviceToken")
            contentType(ContentType.parse(mimeType))
            setBody(data)
        }
    }

    /**
     * Poll video processing job status from video.bsky.app.
     */
    suspend fun getVideoJobStatus(jobId: String): HttpResponse {
        val token = sessionStore.load()?.accessJwt
        return client.get("$VIDEO_SERVICE/xrpc/app.bsky.video.getJobStatus") {
            token?.let { header("Authorization", "Bearer $it") }
            parameter("jobId", jobId)
        }
    }

    /** Derive the did:web for the PDS from the endpoint URL. */
    fun pdsDid(): String {
        val host = pdsEndpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
        return "did:web:$host"
    }

    /** Delete a specific account's session. */
    fun deleteSession(did: String) {
        sessionStore.delete(did)
    }

    fun clearSession() {
        sessionStore.clear()
        pdsEndpoint = DEFAULT_PDS
    }

    companion object {
        const val DEFAULT_PDS = "https://bsky.social"
        const val PUBLIC_API = "https://public.api.bsky.app"
        const val CHAT_PROXY = "did:web:api.bsky.chat#bsky_chat"
        const val VIDEO_SERVICE = "https://video.bsky.app"
    }
}

/** Parse AT Protocol error response into a readable message */
suspend fun HttpResponse.atprotoError(): String {
    return try {
        val body = bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val error = json.decodeFromString<AtprotoErrorResponse>(body)
        if (error.message != null) {
            "${error.error ?: "Error"}: ${error.message}"
        } else {
            error.error ?: "HTTP ${status.value}"
        }
    } catch (_: Exception) {
        "HTTP ${status.value}"
    }
}

@kotlinx.serialization.Serializable
private data class AtprotoErrorResponse(
    val error: String? = null,
    val message: String? = null,
)

@kotlinx.serialization.Serializable
data class ServiceAuthResponse(
    val token: String,
)
