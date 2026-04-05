package com.kazahana.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.kazahana.app.data.local.SessionStore
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.model.DIDDocument
import com.kazahana.app.data.model.ResolveHandleResponse
import com.kazahana.app.data.model.Session
import com.kazahana.app.data.remote.ATProtoClient
import com.kazahana.app.data.remote.PushTokenManager
import com.kazahana.app.data.repository.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val client: ATProtoClient,
    private val sessionStore: SessionStore,
    private val feedRepository: FeedRepository,
    private val settingsStore: SettingsStore,
    private val pushTokenManager: PushTokenManager,
) : ViewModel() {

    // null = still resolving PDS, true = logged in, false = not logged in
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** All saved accounts (for account picker / settings) */
    private val _savedAccounts = MutableStateFlow<List<Session>>(emptyList())
    val savedAccounts: StateFlow<List<Session>> = _savedAccounts.asStateFlow()

    /** Currently active account DID — used as key to force recomposition */
    private val _activeAccountDID = MutableStateFlow<String?>(null)
    val activeAccountDID: StateFlow<String?> = _activeAccountDID.asStateFlow()

    /** Whether the login screen is shown as an "add account" sheet */
    private val _showAddAccountLogin = MutableStateFlow(false)
    val showAddAccountLogin: StateFlow<Boolean> = _showAddAccountLogin.asStateFlow()

    init {
        refreshAccountList()
        val accounts = _savedAccounts.value
        val session = client.session
        if (session != null && accounts.size <= 1) {
            // Single account: auto-login (no change from v1.0 experience)
            _activeAccountDID.value = session.did
            viewModelScope.launch {
                resolvePds(session.did)
                _isLoggedIn.value = true
                loadPostLanguages()
            }
        } else if (accounts.size >= 2) {
            // Multiple accounts saved: show account picker
            _isLoggedIn.value = false
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
                    _activeAccountDID.value = session.did
                    refreshAccountList()
                    _isLoggedIn.value = true
                    _showAddAccountLogin.value = false
                    _uiState.value = LoginUiState()
                    loadPostLanguages()
                    registerPushTokenForDid(session.did)
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

    /** Switch to a different saved account. */
    fun switchAccount(session: Session) {
        viewModelScope.launch {
            // 1. Persist active DID first (race condition prevention — see iOS handoff doc)
            sessionStore.activeAccountDID = session.did
            // 2. Update client session
            client.updateSession(session)
            // 3. Resolve PDS for the new account
            resolvePds(session.did)
            // 4. Update UI state
            _activeAccountDID.value = session.did
            refreshAccountList()
            _isLoggedIn.value = true
            // 5. Silent token refresh
            viewModelScope.launch {
                client.refreshToken()
                refreshAccountList()
            }
            loadPostLanguages()
        }
    }

    /** Remove an account from this device. */
    fun removeAccount(did: String) {
        viewModelScope.launch {
            // Best-effort server-side session deletion
            val session = sessionStore.loadByDid(did)
            if (session != null) {
                try {
                    client.postUnauthenticated(
                        baseUrl = client.pdsEndpoint,
                        nsid = "com.atproto.server.deleteSession",
                        body = buildJsonObject {
                            put("refreshJwt", session.refreshJwt)
                        },
                    )
                } catch (_: Exception) {
                    // Best-effort — ignore failures
                }
            }

            // Unregister push token for this account
            unregisterPushTokenForDid(did)

            val wasActive = sessionStore.delete(did)
            refreshAccountList()

            if (wasActive) {
                val remaining = sessionStore.loadAll()
                if (remaining.isNotEmpty()) {
                    // Switch to the first remaining account
                    val next = remaining.first()
                    switchAccount(next)
                } else {
                    // No accounts left → login screen
                    _activeAccountDID.value = null
                    _isLoggedIn.value = false
                    client.updatePds(ATProtoClient.DEFAULT_PDS)
                }
            }
        }
    }

    /** Legacy logout: removes the active account. */
    fun logout() {
        val did = _activeAccountDID.value ?: return
        removeAccount(did)
    }

    fun showAddAccountLogin() {
        _showAddAccountLogin.value = true
        _uiState.value = LoginUiState()
    }

    fun dismissAddAccountLogin() {
        _showAddAccountLogin.value = false
        _uiState.value = LoginUiState()
    }

    private fun refreshAccountList() {
        _savedAccounts.value = sessionStore.loadAll()
    }

    /** Fetch Bluesky account post language preferences and cache them locally. */
    private fun loadPostLanguages() {
        viewModelScope.launch {
            try {
                feedRepository.getPostLanguages()
                    .onSuccess { langs ->
                        settingsStore.setBlueskyPostLanguages(langs)
                    }
            } catch (_: Exception) {
                // Silently ignore — non-critical, will retry on next login
            }
        }
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

    private fun registerPushTokenForDid(did: String) {
        viewModelScope.launch {
            try {
                if (!settingsStore.pushNotificationsEnabled.first()) return@launch
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    viewModelScope.launch {
                        pushTokenManager.registerToken(did, token)
                    }
                }
            } catch (_: Exception) { /* silent */ }
        }
    }

    private fun unregisterPushTokenForDid(did: String) {
        viewModelScope.launch {
            try {
                pushTokenManager.unregisterToken(did)
            } catch (_: Exception) { /* silent */ }
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
