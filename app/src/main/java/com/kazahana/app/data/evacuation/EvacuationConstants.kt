package com.kazahana.app.data.evacuation

import com.kazahana.app.data.model.Hazard

/**
 * 避難誘導補助機能の定数・マッピング。
 *
 * 本機能は bsaf-kikikuru-bot（気象庁の警報級・危険度情報）の投稿をトリガーとする。
 * 扱う情報は気象庁の危険度情報であって自治体の避難指示そのものではない。
 */
object EvacuationConstants {

    /**
     * bsaf-kikikuru-bot の BSAF Bot Definition JSON URL。
     * GitHub の blob URL ではなく raw URL を使用すること。
     */
    const val KIKIKURU_BOT_DEFINITION_URL =
        "https://raw.githubusercontent.com/osprey74/bsaf-kikikuru-bot/main/bot-definition.json"

    /** bsaf-kikikuru-bot の DID（自動登録の重複チェック・デモ用モック Bot）。 */
    const val KIKIKURU_BOT_DID = "did:plc:kxwz5cz7o6g4jlmxh6doyfsm"

    /** アラートのタイムアウト（解除見逃し対策）。iOS と一致させ 6 時間。 */
    const val ALERT_TIMEOUT_MS = 6L * 60L * 60L * 1000L

    /** タイムアウトチェックの間隔（10分）。 */
    const val EXPIRY_CHECK_INTERVAL_MS = 10L * 60L * 1000L

    /** 避難レベルとして扱う BSAF value（警戒レベル体系）。 */
    val ALERT_VALUES: Set<String> = setOf("level3", "level4", "level5")

    /** BSAF value="cancelled"（解除）。 */
    const val CANCELLED_VALUE = "cancelled"

    /**
     * BSAF type → 対応する災害種別（OR 条件）。
     * 1 つの type が複数の災害種別に対応しうる。未知の type は全種別を候補にする安全側倒し。
     *
     * bsaf-kikikuru-bot の実 type 値（bot-definition.json）に基づく。
     */
    fun hazardFilters(bsafType: String): List<Hazard> = when (bsafType) {
        "heavy-rain-warning", "heavy-rain" ->
            listOf(Hazard.FLOOD, Hazard.LANDSLIDE, Hazard.INLAND_FLOOD)
        "flood-warning", "flood" ->
            listOf(Hazard.FLOOD, Hazard.INLAND_FLOOD)
        "landslide-warning", "landslide" ->
            listOf(Hazard.LANDSLIDE)
        "storm-surge-warning", "storm-surge" ->
            listOf(Hazard.STORM_SURGE)
        "melting-snow-warning" ->
            listOf(Hazard.FLOOD, Hazard.LANDSLIDE)
        "avalanche-warning" ->
            listOf(Hazard.LANDSLIDE)
        "dry-air-warning" ->
            listOf(Hazard.FIRE)
        "tsunami-warning", "tsunami" ->
            listOf(Hazard.TSUNAMI)
        "earthquake-warning", "earthquake" ->
            listOf(Hazard.EARTHQUAKE)
        "volcanic-warning", "volcanic" ->
            listOf(Hazard.VOLCANO)
        // 未知 type（暴風・波浪・大雪・雷・その他等）は全種別を候補に（安全側倒し）
        else -> Hazard.entries.toList()
    }
}
