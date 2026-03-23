package com.kazahana.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.bsaf.BsafService
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.model.BsafRegisteredBot
import com.kazahana.app.data.model.BsafRegisteredFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BsafBotsUiState(
    val bots: List<BsafRegisteredBot> = emptyList(),
    val isLoading: Boolean = false,
    val isRegistering: Boolean = false,
    val error: String? = null,
    val registrationSuccess: Boolean = false,
)

@HiltViewModel
class BsafBotsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val bsafService: BsafService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BsafBotsUiState())
    val uiState: StateFlow<BsafBotsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.bsafRegisteredBots.collect { bots ->
                _uiState.update { it.copy(bots = bots) }
            }
        }
    }

    fun registerBot(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, error = null, registrationSuccess = false) }
            bsafService.fetchBotDefinition(url)
                .onSuccess { definition ->
                    if (_uiState.value.bots.any { it.did == definition.bot.did }) {
                        _uiState.update { it.copy(isRegistering = false, error = "Bot already registered") }
                        return@launch
                    }
                    val bot = BsafRegisteredBot(
                        did = definition.bot.did,
                        handle = definition.bot.handle,
                        name = definition.bot.name,
                        description = definition.bot.description,
                        source = definition.bot.source,
                        sourceUrl = definition.bot.sourceUrl,
                        selfUrl = definition.selfUrl,
                        updatedAt = definition.updatedAt,
                        lastCheckedAt = java.time.Instant.now().toString(),
                        filters = definition.filters.map { f ->
                            BsafRegisteredFilter(
                                tag = f.tag,
                                label = f.label,
                                options = f.options,
                                enabledValues = f.options.map { it.value },
                            )
                        },
                    )
                    settingsStore.registerBsafBot(bot)
                    _uiState.update { it.copy(isRegistering = false, registrationSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isRegistering = false, error = e.message) }
                }
        }
    }

    fun unregisterBot(did: String) {
        viewModelScope.launch {
            settingsStore.unregisterBsafBot(did)
        }
    }

    fun setFilterOptions(did: String, tag: String, enabledValues: List<String>) {
        viewModelScope.launch {
            settingsStore.updateBsafBotFilters(did, tag, enabledValues)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearRegistrationSuccess() {
        _uiState.update { it.copy(registrationSuccess = false) }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val bots = _uiState.value.bots
            for (bot in bots) {
                val updated = bsafService.checkBotUpdate(bot)
                if (updated != null) {
                    val currentBots: List<BsafRegisteredBot> = _uiState.value.bots
                    settingsStore.setBsafRegisteredBots(
                        currentBots.map { if (it.did == updated.did) updated else it }
                    )
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
