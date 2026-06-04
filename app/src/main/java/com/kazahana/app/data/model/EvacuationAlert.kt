package com.kazahana.app.data.model

/** 避難情報のレベル。level3 < level4 < level5。 */
enum class AlertLevel(val rawValue: String) {
    LEVEL3("level3"),
    LEVEL4("level4"),
    LEVEL5("level5"),
    ;

    companion object {
        fun fromValue(value: String): AlertLevel? =
            entries.firstOrNull { it.rawValue == value }
    }
}

/**
 * 現在有効なアラート。
 * 重複排除キーは `type|value|time|target` の完全一致（BsafService.duplicateKey と同形式）。
 */
data class ActiveAlert(
    val type: String,         // BSAF type（heavy-rain-warning など）
    val value: AlertLevel,    // BSAF value
    val time: String,         // BSAF time（ISO8601）
    val target: String,       // BSAF target（jp-xxxx）
    val receivedAt: Long,     // 受信時刻（epoch ms、タイムアウト判定用）
) {
    val id: String get() = "$type|${value.rawValue}|$time|$target"
}

/** バナー表示状態。 */
data class EvacuationBannerState(
    val visible: Boolean = false,
    val highestLevel: AlertLevel? = null,
    val alerts: List<ActiveAlert> = emptyList(),
)
