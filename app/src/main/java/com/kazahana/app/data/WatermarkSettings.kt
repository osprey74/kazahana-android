package com.kazahana.app.data

import kotlinx.serialization.Serializable

enum class WatermarkPreset {
    COPYRIGHT, AI_JA, AI_EN, AI_BOTH, PHOTO, CUSTOM
}

enum class WatermarkPosition {
    TL, TC, TR, BL, BC, BR, RANDOM, TILE
}

@Serializable
data class WatermarkSettings(
    val enabled: Boolean = false,
    val preset: String = "COPYRIGHT",
    val customText: String = "",
    val position: String = "BR",
    val opacity: Float = 70f,
    val fontSize: Float = 12f,
    val textColor: String = "#FFFFFF",
    val skipVideo: Boolean = true,
    val confirmBeforePost: Boolean = true,
) {
    val presetEnum: WatermarkPreset
        get() = try { WatermarkPreset.valueOf(preset) } catch (_: Exception) { WatermarkPreset.COPYRIGHT }

    val positionEnum: WatermarkPosition
        get() = try { WatermarkPosition.valueOf(position) } catch (_: Exception) { WatermarkPosition.BR }
}
