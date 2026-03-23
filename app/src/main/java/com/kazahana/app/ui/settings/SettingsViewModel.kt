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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val showVia: Boolean = false,
    val bsafEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.themeMode,
        settingsStore.appLocaleEnum,
        settingsStore.adultContentEnabled,
        settingsStore.nudityPref,
        settingsStore.sexualPref,
        settingsStore.pornPref,
        settingsStore.graphicMediaPref,
        settingsStore.pollIntervalSeconds,
        settingsStore.showVia,
        settingsStore.bsafEnabled,
    ) { values ->
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            appLocale = values[1] as AppLocale,
            adultContentEnabled = values[2] as Boolean,
            nudityPref = values[3] as ModerationPref,
            sexualPref = values[4] as ModerationPref,
            pornPref = values[5] as ModerationPref,
            graphicMediaPref = values[6] as ModerationPref,
            pollIntervalSeconds = values[7] as Int,
            showVia = values[8] as Boolean,
            bsafEnabled = values[9] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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

    fun setShowVia(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowVia(enabled) }
    }

    fun setBsafEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBsafEnabled(enabled) }
    }
}
