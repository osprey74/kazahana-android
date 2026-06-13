package com.kazahana.app.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.model.JoinLinkPreviewState
import com.kazahana.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinGroupUiState(
    val isLoading: Boolean = true,
    val preview: JoinLinkPreviewState? = null,
    val previewError: Boolean = false,
    val isJoining: Boolean = false,
    val joinedConvoId: String? = null,
    val pending: Boolean = false,
    // Raw atproto error code (e.g. ConvoLocked) for the screen to localize.
    val joinErrorCode: String? = null,
)

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val code: String = savedStateHandle["code"] ?: ""

    private val _uiState = MutableStateFlow(JoinGroupUiState())
    val uiState: StateFlow<JoinGroupUiState> = _uiState.asStateFlow()

    init {
        if (code.isNotEmpty()) loadPreview() else _uiState.update { it.copy(isLoading = false, previewError = true) }
    }

    fun loadPreview() {
        _uiState.update { it.copy(isLoading = true, previewError = false) }
        viewModelScope.launch {
            chatRepository.getJoinLinkPreview(code)
                .onSuccess { state -> _uiState.update { it.copy(isLoading = false, preview = state) } }
                .onFailure { _uiState.update { it.copy(isLoading = false, previewError = true) } }
        }
    }

    fun join() {
        if (_uiState.value.isJoining) return
        _uiState.update { it.copy(isJoining = true, joinErrorCode = null) }
        viewModelScope.launch {
            chatRepository.requestJoin(code)
                .onSuccess { resp ->
                    _uiState.update {
                        it.copy(
                            isJoining = false,
                            joinedConvoId = if (resp.status == "joined") resp.convo?.id else null,
                            pending = resp.status == "pending",
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isJoining = false, joinErrorCode = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(joinErrorCode = null) }
    }
}
