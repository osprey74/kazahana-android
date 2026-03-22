package com.kazahana.app.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.ChatMessageOrDeleted
import com.kazahana.app.data.model.ChatReaction
import com.kazahana.app.data.model.ConvoView
import com.kazahana.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val convo: ConvoView? = null,
    val messages: List<ChatMessageOrDeleted> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val messageText: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val convoId: String = savedStateHandle["convoId"] ?: ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        if (convoId.isNotEmpty()) {
            loadConvo()
            loadMessages()
        }
    }

    private fun loadConvo() {
        viewModelScope.launch {
            chatRepository.getConvo(convoId).onSuccess { convo ->
                _uiState.update { it.copy(convo = convo) }
            }
            chatRepository.updateRead(convoId)
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            chatRepository.getMessages(convoId)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            messages = response.messages,
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore || state.cursor == null) return

        viewModelScope.launch {
            chatRepository.getMessages(convoId, cursor = state.cursor)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + response.messages,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                        )
                    }
                }
        }
    }

    fun updateMessageText(text: String) {
        _uiState.update { it.copy(messageText = text) }
    }

    fun sendMessage(directText: String? = null) {
        val text = directText ?: _uiState.value.messageText.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            chatRepository.sendMessage(convoId, text)
                .onSuccess { response ->
                    val newMessage = ChatMessageOrDeleted(
                        type = "chat.bsky.convo.defs#messageView",
                        id = response.id,
                        rev = response.rev,
                        text = response.text,
                        sender = response.sender,
                        sentAt = response.sentAt,
                    )
                    _uiState.update {
                        it.copy(
                            messages = listOf(newMessage) + it.messages,
                            messageText = if (directText != null) it.messageText else "",
                            isSending = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isSending = false) }
                }
        }
    }

    fun toggleReaction(messageId: String, emoji: String, myDid: String) {
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val hasReaction = message.reactions?.any { it.value == emoji && it.sender.did == myDid } == true

        viewModelScope.launch {
            val result = if (hasReaction) {
                chatRepository.removeReaction(convoId, messageId, emoji)
            } else {
                chatRepository.addReaction(convoId, messageId, emoji)
            }
            result.onSuccess { response ->
                // Update the message in the list with new reactions from API response
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == messageId) {
                                msg.copy(reactions = response.reactions)
                            } else msg
                        }
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            chatRepository.getMessages(convoId)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            messages = response.messages,
                            cursor = response.cursor,
                            hasMore = response.cursor != null,
                        )
                    }
                }
            chatRepository.updateRead(convoId)
        }
    }
}
