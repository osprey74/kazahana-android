package com.kazahana.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.DIDDocument
import com.kazahana.app.data.model.ResolveHandleResponse
import com.kazahana.app.data.model.Session
import com.kazahana.app.data.remote.ATProtoClient
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val client: ATProtoClient,
) : ViewModel() {

    // null = still resolving PDS, true = logged in, false = not logged in
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Restore PDS endpoint from saved session, then signal ready
        val session = client.session
        if (session != null) {
            viewModelScope.launch {
                resolvePds(session.did)
                _isLoggedIn.value = true
            }
        } else {
            _isLoggedIn.value = false
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            try {
                // 1. Resolve DID if handle is given
                val did = if (identifier.startsWith("did:")) {
                    identifier
                } else {
                    resolveDid(identifier)
                }

                // 2. Resolve PDS endpoint
                if (did != null) {
                    resolvePds(did)
                }

                // 3. Create session
                val response = client.postUnauthenticated(
                    baseUrl = client.pdsEndpoint,
                    nsid = "com.atproto.server.createSession",
                    body = buildJsonObject {
                        put("identifier", identifier)
                        put("password", password)
                    },
                )

                if (response.status.isSuccess()) {
                    val session = response.body<Session>()
                    client.saveSession(session)
                    // Re-resolve PDS with actual DID
                    resolvePds(session.did)
                    _isLoggedIn.value = true
                    _uiState.value = LoginUiState()
                } else {
                    val statusCode = response.status.value
                    val errorMsg = when {
                        statusCode == 401 -> "Invalid handle or password"
                        statusCode == 429 -> "Rate limited. Please wait."
                        else -> "Login failed (HTTP $statusCode)"
                    }
                    _uiState.value = LoginUiState(error = errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState(error = "Network error: ${e.message}")
            }
        }
    }

    val currentDid: String
        get() = client.session?.did ?: ""

    fun logout() {
        client.clearSession()
        _isLoggedIn.value = false
    }

    private suspend fun resolveDid(handle: String): String? {
        return try {
            val response = client.getUnauthenticated(
                baseUrl = ATProtoClient.DEFAULT_PDS,
                nsid = "com.atproto.identity.resolveHandle",
                params = mapOf("handle" to handle),
            )
            if (response.status.isSuccess()) {
                response.body<ResolveHandleResponse>().did
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolvePds(did: String) {
        try {
            val plcUrl = if (did.startsWith("did:plc:")) {
                "https://plc.directory/$did"
            } else {
                return // did:web uses the handle domain
            }
            val response = client.getUnauthenticated(
                baseUrl = plcUrl,
                nsid = "",
                params = emptyMap(),
            )
            if (response.status.isSuccess()) {
                val doc = response.body<DIDDocument>()
                val pds = doc.service
                    .firstOrNull { it.id == "#atproto_pds" }
                    ?.serviceEndpoint
                if (pds != null) {
                    client.updatePds(pds)
                }
            }
        } catch (_: Exception) {
            // Fall back to default PDS
        }
    }
}
