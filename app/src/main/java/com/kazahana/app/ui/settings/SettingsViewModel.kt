package com.kazahana.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazahana.app.data.local.AppLocale
import com.kazahana.app.data.local.ModerationPref
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.local.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLocale: AppLocale = AppLocale.SYSTEM,
    val adultContentEnabled: Boolean = false,
    val nudityPref: ModerationPref = ModerationPref.WARN,
    val sexualPref: ModerationPref = ModerationPref.WARN,
    val pornPref: ModerationPref = ModerationPref.WARN,
    val graphicMediaPref: ModerationPref = ModerationPref.WARN,
    val pollIntervalSeconds: Int = 60,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsStore.appLocaleEnum.collect { locale ->
                _uiState.update { it.copy(appLocale = locale) }
            }
        }
        viewModelScope.launch {
            settingsStore.adultContentEnabled.collect { enabled ->
                _uiState.update { it.copy(adultContentEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsStore.nudityPref.collect { pref ->
                _uiState.update { it.copy(nudityPref = pref) }
            }
        }
        viewModelScope.launch {
            settingsStore.sexualPref.collect { pref ->
                _uiState.update { it.copy(sexualPref = pref) }
            }
        }
        viewModelScope.launch {
            settingsStore.pornPref.collect { pref ->
                _uiState.update { it.copy(pornPref = pref) }
            }
        }
        viewModelScope.launch {
            settingsStore.graphicMediaPref.collect { pref ->
                _uiState.update { it.copy(graphicMediaPref = pref) }
            }
        }
        viewModelScope.launch {
            settingsStore.pollIntervalSeconds.collect { seconds ->
                _uiState.update { it.copy(pollIntervalSeconds = seconds) }
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setTheme(mode) }
    }

    fun setAppLocale(locale: AppLocale) {
        viewModelScope.launch {
            settingsStore.setAppLocale(locale)
            // Apply locale immediately via AppCompat
            if (locale.tag.isEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(locale.tag)
                )
            }
        }
    }

    fun setAdultContentEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAdultContentEnabled(enabled) }
    }

    fun setModerationPref(label: String, pref: ModerationPref) {
        viewModelScope.launch { settingsStore.setModerationPref(label, pref) }
    }

    fun setPollInterval(seconds: Int) {
        viewModelScope.launch { settingsStore.setPollInterval(seconds) }
    }
}
