package com.kazahana.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.ProfileViewDetailed
import com.kazahana.app.data.repository.ChatRepository
import com.kazahana.app.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** chat.bsky.group.createGroup allows up to 49 invitees. */
private const val MAX_GROUP_MEMBERS = 49

data class CreateGroupUiState(
    val name: String = "",
    val query: String = "",
    val results: List<ProfileViewDetailed> = emptyList(),
    val selected: List<ProfileViewDetailed> = emptyList(),
    val isSearching: Boolean = false,
    val isCreating: Boolean = false,
    val createdConvoId: String? = null,
    val error: String? = null,
) {
    val canCreate: Boolean get() = name.isNotBlank() && selected.isNotEmpty() && !isCreating
    val memberLimitReached: Boolean get() = selected.size >= MAX_GROUP_MEMBERS
}

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            search()
        }
    }

    private suspend fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        _uiState.update { it.copy(isSearching = true) }
        searchRepository.searchActors(query, limit = 20)
            .onSuccess { resp -> _uiState.update { it.copy(results = resp.actors, isSearching = false) } }
            .onFailure { _uiState.update { it.copy(isSearching = false) } }
    }

    fun toggleMember(user: ProfileViewDetailed) {
        _uiState.update { state ->
            if (state.selected.any { it.did == user.did }) {
                state.copy(selected = state.selected.filterNot { it.did == user.did })
            } else if (state.selected.size >= MAX_GROUP_MEMBERS) {
                state
            } else {
                state.copy(selected = state.selected + user)
            }
        }
    }

    fun removeMember(did: String) {
        _uiState.update { it.copy(selected = it.selected.filterNot { u -> u.did == did }) }
    }

    fun create() {
        val state = _uiState.value
        if (!state.canCreate) return
        _uiState.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            chatRepository.createGroup(state.name.trim(), state.selected.map { it.did })
                .onSuccess { convo -> _uiState.update { it.copy(isCreating = false, createdConvoId = convo.id) } }
                .onFailure { e -> _uiState.update { it.copy(isCreating = false, error = e.message) } }
        }
    }
}
