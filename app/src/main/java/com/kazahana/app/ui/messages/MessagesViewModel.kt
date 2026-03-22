package com.kazahana.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.ConvoView
import com.kazahana.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    init {
        loadConversations()
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
