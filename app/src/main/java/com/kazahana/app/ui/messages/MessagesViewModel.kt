package com.kazahana.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.ConvoView
import com.kazahana.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesUiState(
    val conversations: List<ConvoView> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        loadConversations()
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(30_000L) // 30 seconds
                silentRefresh()
            }
        }
    }

    private suspend fun silentRefresh() {
        chatRepository.listConvos()
            .onSuccess { response ->
                _uiState.update {
                    it.copy(
                        conversations = response.convos,
                        cursor = response.cursor,
                        hasMore = response.cursor != null,
                    )
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            chatRepository.listConvos()
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            conversations = response.convos,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            chatRepository.listConvos()
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            conversations = response.convos,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRefreshing = false, error = e.message) }
                }
        }
    }

    fun acceptConvo(convoId: String) {
        viewModelScope.launch {
            chatRepository.acceptConvo(convoId)
                .onSuccess {
                    refresh()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore || state.cursor == null) return

        viewModelScope.launch {
            chatRepository.listConvos(cursor = state.cursor)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            conversations = it.conversations + response.convos,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                        )
                    }
                }
        }
    }
}
