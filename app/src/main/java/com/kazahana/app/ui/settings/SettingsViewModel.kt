package com.kazahana.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.kazahana.app.data.WatermarkSettings
import com.kazahana.app.data.bsaf.BsafService
import com.kazahana.app.data.evacuation.EvacuationAlertManager
import com.kazahana.app.data.evacuation.EvacuationConstants
import com.kazahana.app.data.model.AlertLevel
import com.kazahana.app.data.local.AppLocale
import com.kazahana.app.data.local.ModerationPref
import com.kazahana.app.data.local.SessionStore
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.local.ThemeMode
import com.kazahana.app.data.model.BsafRegisteredBot
import com.kazahana.app.data.model.BsafRegisteredFilter
import com.kazahana.app.data.remote.PushTokenManager
import com.kazahana.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    val gorePref: ModerationPref = ModerationPref.WARN,
    val pollIntervalSeconds: Int = 60,
    val showVia: Boolean = false,
    val bsafEnabled: Boolean = false,
    val claudeApiKey: String = "",
    val pushNotificationsEnabled: Boolean = false,
)

/** 避難誘導機能の bsaf-kikikuru-bot 自動登録フローの状態。 */
data class EvacuationBotState(
    val needsBotConfirm: Boolean = false,
    val registering: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val pushTokenManager: PushTokenManager,
    private val sessionStore: SessionStore,
    private val bsafService: BsafService,
    private val profileRepository: ProfileRepository,
    private val evacuationAlertManager: EvacuationAlertManager,
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

    val longFormServiceUrl: StateFlow<String> = settingsStore.longFormServiceUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setLongFormServiceUrl(url: String) {
        viewModelScope.launch { settingsStore.setLongFormServiceUrl(url) }
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

    // ── 避難誘導補助機能 ──

    val evacuationEnabled: StateFlow<Boolean> = settingsStore.evacuationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val evacuationPrefectureOverride: StateFlow<String> = settingsStore.evacuationPrefectureOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _evacuationBotState = MutableStateFlow(EvacuationBotState())
    val evacuationBotState: StateFlow<EvacuationBotState> = _evacuationBotState.asStateFlow()

    /**
     * トグル操作。オンにする際、bsaf-kikikuru-bot が未登録なら確認ダイアログを要求する。
     * 登録済みなら即時有効化（BSAF も有効化）。
     */
    fun onToggleEvacuation(enable: Boolean) {
        viewModelScope.launch {
            if (!enable) {
                settingsStore.setEvacuationEnabled(false)
                return@launch
            }
            val bots = settingsStore.bsafRegisteredBots.first()
            if (bots.any { it.did == EvacuationConstants.KIKIKURU_BOT_DID }) {
                settingsStore.setEvacuationEnabled(true)
                settingsStore.setBsafEnabled(true)
            } else {
                _evacuationBotState.update { it.copy(needsBotConfirm = true) }
            }
        }
    }

    /** 確認ダイアログ承認後：bsaf-kikikuru-bot を BSAF 登録＋自動フォローし、機能を有効化。 */
    fun confirmRegisterKikikuruAndEnable() {
        viewModelScope.launch {
            _evacuationBotState.update { it.copy(registering = true, error = null, needsBotConfirm = false) }
            bsafService.fetchBotDefinition(EvacuationConstants.KIKIKURU_BOT_DEFINITION_URL)
                .onSuccess { definition ->
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
                    settingsStore.setBsafEnabled(true)
                    settingsStore.setEvacuationEnabled(true)
                    // 自動フォロー（best-effort。失敗しても登録・有効化は維持）
                    profileRepository.follow(definition.bot.did)
                    _evacuationBotState.update { it.copy(registering = false) }
                }
                .onFailure { e ->
                    _evacuationBotState.update { it.copy(registering = false, error = e.message) }
                }
        }
    }

    fun dismissEvacuationBotConfirm() {
        _evacuationBotState.update { it.copy(needsBotConfirm = false) }
    }

    fun clearEvacuationBotError() {
        _evacuationBotState.update { it.copy(error = null) }
    }

    fun setEvacuationPrefectureOverride(prefecture: String) {
        viewModelScope.launch { settingsStore.setEvacuationPrefectureOverride(prefecture) }
    }

    fun setEvacuationOnboardingShown() {
        viewModelScope.launch { settingsStore.setEvacuationOnboardingShown(true) }
    }

    // ── デモモード（ストア審査対応：Release ビルドでもアラートを擬似発生） ──

    fun injectDemoAlert(level: AlertLevel) {
        val type = when (level) {
            AlertLevel.LEVEL3 -> "heavy-rain-warning"
            AlertLevel.LEVEL4 -> "flood-warning"
            AlertLevel.LEVEL5 -> "landslide-warning"
        }
        evacuationAlertManager.injectTestAlert(level, type)
    }

    fun clearDemoAlerts() {
        evacuationAlertManager.clearAll()
    }
}
