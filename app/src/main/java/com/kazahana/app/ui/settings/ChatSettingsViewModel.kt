package com.kazahana.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** all | following | none */
private const val DEFAULT_POLICY = "following"

data class ChatSettingsUiState(
    val allowIncoming: String = DEFAULT_POLICY,
    val allowGroupInvites: String = DEFAULT_POLICY,
    val loaded: Boolean = false,
)

@HiltViewModel
class ChatSettingsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatSettingsUiState())
    val uiState: StateFlow<ChatSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getDeclaration().onSuccess { decl ->
                _uiState.update {
                    it.copy(
                        allowIncoming = decl.allowIncoming ?: DEFAULT_POLICY,
                        allowGroupInvites = decl.allowGroupInvites ?: DEFAULT_POLICY,
                        loaded = true,
                    )
                }
            }
        }
    }

    fun setAllowIncoming(value: String) {
        _uiState.update { it.copy(allowIncoming = value) }
        save()
    }

    fun setAllowGroupInvites(value: String) {
        _uiState.update { it.copy(allowGroupInvites = value) }
        save()
    }

    private fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            chatRepository.putDeclaration(state.allowIncoming, state.allowGroupInvites)
        }
    }
}
