package com.kazahana.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.kazahana.app.data.WatermarkSettings
import com.kazahana.app.data.local.AppLocale
import com.kazahana.app.data.local.ModerationPref
import com.kazahana.app.data.local.SessionStore
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.local.ThemeMode
import com.kazahana.app.data.remote.PushTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    val pushNotificationsEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val pushTokenManager: PushTokenManager,
    private val sessionStore: SessionStore,
) : ViewModel() {

    val watermarkSettings: StateFlow<WatermarkSettings> = settingsStore.watermarkSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatermarkSettings())

    val currentHandle: StateFlow<String> = MutableStateFlow(
        sessionStore.load()?.handle ?: "example.bsky.social"
    )

    fun updateWatermark(settings: WatermarkSettings) {
        viewModelScope.launch { settingsStore.setWatermarkSettings(settings) }
    }

    val confirmDraftImageQuality: StateFlow<Boolean> = settingsStore.confirmDraftImageQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setConfirmDraftImageQuality(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setConfirmDraftImageQuality(enabled) }
    }

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
            settingsStore.pushNotificationsEnabled,
        ) { values ->
            Triple(
                Triple(values[0] as ModerationPref, values[1] as ModerationPref, values[2] as ModerationPref),
                Triple(values[3] as Int, values[4] as Boolean, values[5] as Boolean),
                Pair(values[6] as String, values[7] as Boolean),
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
            claudeApiKey = extra.third.first,
            pushNotificationsEnabled = extra.third.second,
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

    fun setPushNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setPushNotificationsEnabled(enabled)
            if (enabled) {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    viewModelScope.launch {
                        pushTokenManager.registerTokenForAllAccounts(token)
                    }
                }
            } else {
                pushTokenManager.unregisterTokenForAllAccounts()
            }
        }
    }
}
