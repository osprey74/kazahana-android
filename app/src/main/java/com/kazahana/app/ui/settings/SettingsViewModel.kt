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
    val gorePref: ModerationPref = ModerationPref.WARN,
    val pollIntervalSeconds: Int = 60,
    val showVia: Boolean = false,
    val bsafEnabled: Boolean = false,
    val claudeApiKey: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsStore.themeMode,
            settingsStore.appLocaleEnum,
            settingsStore.adultContentEnabled,
            settingsStore.nudityPref,
            settingsStore.sexualPref,
        ) { theme, locale, adult, nudity, sexual ->
            SettingsUiState(
                themeMode = theme,
                appLocale = locale,
                adultContentEnabled = adult,
                nudityPref = nudity,
                sexualPref = sexual,
            )
        },
        combine(
            settingsStore.pornPref,
            settingsStore.graphicMediaPref,
            settingsStore.gorePref,
            settingsStore.pollIntervalSeconds,
            settingsStore.showVia,
            settingsStore.bsafEnabled,
            settingsStore.claudeApiKey,
        ) { values ->
            Triple(
                Triple(values[0] as ModerationPref, values[1] as ModerationPref, values[2] as ModerationPref),
                Triple(values[3] as Int, values[4] as Boolean, values[5] as Boolean),
                values[6] as String,
            )
        },
    ) { base, extra ->
        base.copy(
            pornPref = extra.first.first,
            graphicMediaPref = extra.first.second,
            gorePref = extra.first.third,
            pollIntervalSeconds = extra.second.first,
            showVia = extra.second.second,
            bsafEnabled = extra.second.third,
            claudeApiKey = extra.third,
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

    fun setClaudeApiKey(key: String) {
        viewModelScope.launch { settingsStore.setClaudeApiKey(key) }
    }

    fun clearClaudeApiKey() {
        viewModelScope.launch { settingsStore.setClaudeApiKey("") }
    }
}
