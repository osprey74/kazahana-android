package com.kazahana.app.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.ChatMessageOrDeleted
import com.kazahana.app.data.model.ChatReaction
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** The message the composer is currently replying to (Bluesky v1.125 DM replies). */
data class ChatReplyTarget(
    val messageId: String,
    val text: String,
    val senderDid: String,
    val isDeleted: Boolean = false,
)

data class ChatUiState(
    val convo: ConvoView? = null,
    val messages: List<ChatMessageOrDeleted> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val messageText: String = "",
    val replyTo: ChatReplyTarget? = null,
    /** One-shot send error code/message, consumed by the UI via [ChatViewModel.consumeSendError]. */
    val sendError: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val convoId: String = savedStateHandle["convoId"] ?: ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        if (convoId.isNotEmpty()) {
            loadConvo()
            loadMessages()
            startPolling()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(15_000L) // 15 seconds
                pollNewMessages()
            }
        }
    }

    private suspend fun pollNewMessages() {
        chatRepository.getMessages(convoId)
            .onSuccess { response ->
                val existingIds = _uiState.value.messages.map { it.id }.toSet()
                val newMessages = response.messages.filter { it.id !in existingIds }
                if (newMessages.isNotEmpty()) {
                    _uiState.update {
                        it.copy(messages = newMessages + it.messages)
                    }
                    chatRepository.updateRead(convoId)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
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

    /** Start replying to [message] (no-op for deleted/system messages without an id). */
    fun startReply(messageId: String, text: String?, senderDid: String?) {
        if (senderDid == null) return
        _uiState.update {
            it.copy(
                replyTo = ChatReplyTarget(
                    messageId = messageId,
                    text = text ?: "",
                    senderDid = senderDid,
                    isDeleted = text == null,
                ),
            )
        }
    }

    fun cancelReply() {
        _uiState.update { it.copy(replyTo = null) }
    }

    fun consumeSendError() {
        _uiState.update { it.copy(sendError = null) }
    }

    fun sendMessage(directText: String? = null) {
        val text = directText ?: _uiState.value.messageText.trim()
        if (text.isEmpty() || _uiState.value.isSending) return
        val replyTarget = _uiState.value.replyTo

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendError = null) }
            chatRepository.sendMessage(convoId, text, replyTarget?.messageId)
                .onSuccess { response ->
                    // Prefer the server-echoed replyTo; fall back to a locally built one
                    // so the sender's own bubble shows the reply context immediately.
                    val replyToJson = response.replyTo ?: replyTarget?.let { buildReplyToJson(it) }
                    val newMessage = ChatMessageOrDeleted(
                        type = "chat.bsky.convo.defs#messageView",
                        id = response.id,
                        rev = response.rev,
                        text = response.text,
                        sender = response.sender,
                        sentAt = response.sentAt,
                        replyTo = replyToJson,
                    )
                    _uiState.update {
                        it.copy(
                            messages = listOf(newMessage) + it.messages,
                            messageText = if (directText != null) it.messageText else "",
                            isSending = false,
                            replyTo = null,
                        )
                    }
                }
                .onFailure { e ->
                    val code = e.message
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            sendError = code,
                            // The reply target no longer exists — drop it so the user can retry plainly.
                            replyTo = if (code == "ReplyTargetNotFound") null else it.replyTo,
                        )
                    }
                }
        }
    }

    private fun buildReplyToJson(target: ChatReplyTarget): JsonElement = buildJsonObject {
        put(
            "\$type",
            if (target.isDeleted) "chat.bsky.convo.defs#deletedMessageView"
            else "chat.bsky.convo.defs#messageView",
        )
        put("id", target.messageId)
        if (!target.isDeleted) put("text", target.text)
        put("sender", buildJsonObject { put("did", target.senderDid) })
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

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessageForSelf(convoId, messageId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == messageId) {
                                    msg.copy(
                                        type = "chat.bsky.convo.defs#deletedMessageView",
                                        text = null,
                                    )
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
