package com.kazahana.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.local.SettingsStore
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

data class NewConversationUiState(
    val query: String = "",
    val users: List<ProfileViewDetailed> = emptyList(),
    val isSearching: Boolean = false,
    val isCreating: Boolean = false,
    val createdConvoId: String? = null,
    val error: String? = null,
    val dmSearchHistory: List<String> = emptyList(),
)

@HiltViewModel
class NewConversationViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val chatRepository: ChatRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewConversationUiState())
    val uiState: StateFlow<NewConversationUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.dmSearchHistory.collect { history ->
                _uiState.update { it.copy(dmSearchHistory = history) }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(users = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            searchUsers()
        }
    }

    fun searchFromHistory(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = query) }
        searchJob = viewModelScope.launch {
            searchUsers()
        }
    }

    private suspend fun searchUsers() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        _uiState.update { it.copy(isSearching = true) }
        searchRepository.searchActors(query, limit = 20)
            .onSuccess { response ->
                _uiState.update { it.copy(users = response.actors, isSearching = false) }
            }
            .onFailure {
                _uiState.update { it.copy(isSearching = false) }
            }
    }

    fun selectUser(user: ProfileViewDetailed) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            // Save handle to DM search history
            settingsStore.addDmSearchHistory(user.handle)
            chatRepository.getConvoForMembers(user.did)
                .onSuccess { convo ->
                    _uiState.update { it.copy(isCreating = false, createdConvoId = convo.id) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isCreating = false, error = e.message) }
                }
        }
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { settingsStore.removeDmSearchHistory(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { settingsStore.clearDmSearchHistory() }
    }
}
