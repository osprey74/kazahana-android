package com.kazahana.app.data.evacuation

import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.model.ActiveAlert
import com.kazahana.app.data.model.AlertLevel
import com.kazahana.app.data.model.BsafParsedTags
import com.kazahana.app.data.model.EvacuationBannerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 避難バナーの状態を単一所有する @Singleton。
 *
 * アカウント切替で UI（MainScreen）が再構築されても、状態はこの Singleton が保持するため
 * 失われない。投入側（TimelineViewModel）と購読側（バナー・各画面）の双方がここに依存する。
 *
 * 設計思想: 自動避難判定・強制遷移は行わない。レベル3で先回りバナー、レベル4/5で強調するのみ。
 */
@Singleton
class EvacuationAlertManager @Inject constructor(
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _bannerState = MutableStateFlow(EvacuationBannerState())
    val bannerState: StateFlow<EvacuationBannerState> = _bannerState.asStateFlow()

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var prefectureOverride: String = ""

    @Volatile
    private var locationDerivedPrefecture: String? = null

    init {
        scope.launch {
            settingsStore.evacuationEnabled.collect { value ->
                enabled = value
                if (!value) clearAll()
            }
        }
        scope.launch {
            settingsStore.evacuationPrefectureOverride.collect { value ->
                prefectureOverride = value
                onPrefectureChanged()
            }
        }
    }

    /** 測位由来の都道府県を外部（LocationService 経由）から設定する。 */
    fun setLocationDerivedPrefecture(pref: String?) {
        locationDerivedPrefecture = pref
        onPrefectureChanged()
    }

    /** 手動設定優先で現在の都道府県を解決。 */
    fun resolvedPrefecture(): String? =
        prefectureOverride.ifEmpty { null } ?: locationDerivedPrefecture

    /**
     * BSAF 投稿を処理しバナー状態を更新する。
     * 機能オフ・都道府県不一致・レベル3未満は無視。重複排除は id（type|value|time|target）。
     */
    fun processPost(tags: BsafParsedTags) {
        if (!enabled) return
        val pref = resolvedPrefecture() ?: return
        if (tags.target != pref) return

        if (tags.value == EvacuationConstants.CANCELLED_VALUE) {
            removeCancelledAlerts(tags.type, tags.target)
            return
        }

        val level = AlertLevel.fromValue(tags.value) ?: return // level3/4/5 のみ
        val alert = ActiveAlert(
            type = tags.type,
            value = level,
            time = tags.time,
            target = tags.target,
            receivedAt = System.currentTimeMillis(),
        )
        _bannerState.update { state ->
            if (state.alerts.any { it.id == alert.id }) state
            else recompute(state.alerts + alert)
        }
    }

    /** 解除（cancelled）受信時、同一 type+target のアラートを除去。 */
    fun removeCancelledAlerts(type: String, target: String) {
        _bannerState.update { state ->
            recompute(state.alerts.filterNot { it.type == type && it.target == target })
        }
    }

    /** タイムアウトした古いアラートを除去（解除見逃し対策）。 */
    fun expireStaleAlerts() {
        val cutoff = System.currentTimeMillis() - EvacuationConstants.ALERT_TIMEOUT_MS
        _bannerState.update { state ->
            recompute(state.alerts.filter { it.receivedAt >= cutoff })
        }
    }

    /** 全アラートを消去（機能オフ・デモのリセット）。 */
    fun clearAll() {
        _bannerState.value = EvacuationBannerState()
    }

    /** デモモード用：テストアラートを注入する。 */
    fun injectTestAlert(level: AlertLevel, type: String) {
        val target = resolvedPrefecture() ?: ""
        val alert = ActiveAlert(
            type = type,
            value = level,
            time = System.currentTimeMillis().toString(),
            target = target,
            receivedAt = System.currentTimeMillis(),
        )
        _bannerState.update { recompute(it.alerts + alert) }
    }

    private fun onPrefectureChanged() {
        val pref = resolvedPrefecture()
        // 都道府県が変わったら、現在地と一致しないアラートは除去する
        _bannerState.update { state ->
            if (pref == null) EvacuationBannerState()
            else recompute(state.alerts.filter { it.target == pref })
        }
    }

    private fun recompute(alerts: List<ActiveAlert>): EvacuationBannerState {
        return if (alerts.isEmpty()) {
            EvacuationBannerState()
        } else {
            EvacuationBannerState(
                visible = true,
                highestLevel = alerts.maxByOrNull { it.value.ordinal }?.value,
                alerts = alerts,
            )
        }
    }
}
