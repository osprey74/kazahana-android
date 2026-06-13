package com.kazahana.app.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.ConvoView
import com.kazahana.app.data.model.JoinRequestView
import com.kazahana.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupSettingsUiState(
    val convo: ConvoView? = null,
    val isLoading: Boolean = true,
    val joinRequests: List<JoinRequestView> = emptyList(),
    val isWorking: Boolean = false,
    val error: String? = null,
    val left: Boolean = false,
)

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val convoId: String = savedStateHandle["convoId"] ?: ""

    private val _uiState = MutableStateFlow(GroupSettingsUiState())
    val uiState: StateFlow<GroupSettingsUiState> = _uiState.asStateFlow()

    init {
        if (convoId.isNotEmpty()) load()
    }

    fun load() {
        viewModelScope.launch {
            chatRepository.getConvo(convoId)
                .onSuccess { convo ->
                    _uiState.update { it.copy(convo = convo, isLoading = false) }
                    maybeLoadJoinRequests(convo)
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    private fun maybeLoadJoinRequests(convo: ConvoView) {
        val group = convo.groupInfo
        // joinRequestCount is owner-only; non-null & >0 means there are requests to list.
        // Otherwise clear any stale list — leaving an approved user in both the requests
        // and members lists would produce duplicate LazyColumn keys and crash.
        if (group == null || (group.joinRequestCount ?: 0) <= 0) {
            _uiState.update { it.copy(joinRequests = emptyList()) }
            return
        }
        viewModelScope.launch {
            chatRepository.listJoinRequests(convoId)
                .onSuccess { resp -> _uiState.update { it.copy(joinRequests = resp.requests) } }
            chatRepository.updateJoinRequestsRead(convoId)
        }
    }

    private fun runAction(block: suspend () -> Result<*>) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            block()
                .onSuccess {
                    _uiState.update { it.copy(isWorking = false) }
                    load()
                }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = e.message) } }
        }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        runAction { chatRepository.editGroup(convoId, name.trim()) }
    }

    fun kick(did: String) = runAction { chatRepository.removeMembers(convoId, listOf(did)) }

    fun toggleLock(currentlyLocked: Boolean) = runAction {
        if (currentlyLocked) chatRepository.unlockConvo(convoId) else chatRepository.lockConvo(convoId)
    }

    fun toggleMute(currentlyMuted: Boolean) = runAction {
        if (currentlyMuted) chatRepository.unmuteConvo(convoId) else chatRepository.muteConvo(convoId)
    }

    /** Create the link if absent, otherwise toggle its enabled state. */
    fun toggleJoinLink() {
        val link = _uiState.value.convo?.groupInfo?.joinLink
        runAction {
            when {
                link == null -> chatRepository.createJoinLink(convoId)
                link.isEnabled -> chatRepository.disableJoinLink(convoId)
                else -> chatRepository.enableJoinLink(convoId)
            }
        }
    }

    fun approve(did: String) = runAction { chatRepository.approveJoinRequest(convoId, did) }

    fun reject(did: String) = runAction { chatRepository.rejectJoinRequest(convoId, did) }

    fun leave() {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            chatRepository.leaveConvo(convoId)
                .onSuccess { _uiState.update { it.copy(isWorking = false, left = true) } }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = e.message) } }
        }
    }
}
