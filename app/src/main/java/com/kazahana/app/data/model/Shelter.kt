package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 避難所が対応する災害種別フラグ。
 *
 * 端末同梱データ（shelters.zlib）では 8 ビットのビットマスクとして格納されている。
 * ビット割り当ては iOS / build-shelters.py と完全一致させること。
 */
data class ShelterHazards(
    val flood: Boolean = false,        // 洪水
    val landslide: Boolean = false,    // 崖崩れ・土石流・地滑り
    val stormSurge: Boolean = false,   // 高潮
    val earthquake: Boolean = false,   // 地震
    val tsunami: Boolean = false,      // 津波
    val fire: Boolean = false,         // 大規模な火事
    val inlandFlood: Boolean = false,  // 内水氾濫
    val volcano: Boolean = false,      // 火山現象
) {
    companion object {
        const val FLOOD = 1
        const val LANDSLIDE = 2
        const val STORM_SURGE = 4
        const val EARTHQUAKE = 8
        const val TSUNAMI = 16
        const val FIRE = 32
        const val INLAND_FLOOD = 64
        const val VOLCANO = 128

        /** ビットマスクから展開する。 */
        fun from(mask: Int): ShelterHazards = ShelterHazards(
            flood = mask and FLOOD != 0,
            landslide = mask and LANDSLIDE != 0,
            stormSurge = mask and STORM_SURGE != 0,
            earthquake = mask and EARTHQUAKE != 0,
            tsunami = mask and TSUNAMI != 0,
            fire = mask and FIRE != 0,
            inlandFlood = mask and INLAND_FLOOD != 0,
            volcano = mask and VOLCANO != 0,
        )
    }

    /** KeyPath 相当の参照を Hazard enum で表現するためのアクセサ。 */
    fun supports(hazard: Hazard): Boolean = when (hazard) {
        Hazard.FLOOD -> flood
        Hazard.LANDSLIDE -> landslide
        Hazard.STORM_SURGE -> stormSurge
        Hazard.EARTHQUAKE -> earthquake
        Hazard.TSUNAMI -> tsunami
        Hazard.FIRE -> fire
        Hazard.INLAND_FLOOD -> inlandFlood
        Hazard.VOLCANO -> volcano
    }
}

/** 災害種別。フィルタ条件・i18n キー解決に使う。 */
enum class Hazard {
    FLOOD,
    LANDSLIDE,
    STORM_SURGE,
    EARTHQUAKE,
    TSUNAMI,
    FIRE,
    INLAND_FLOOD,
    VOLCANO,
}

/**
 * 端末同梱データのコンパクト表現（短縮キー）。
 * shelters.zlib は raw deflate 圧縮された、この形式の JSON 配列。
 */
@Serializable
data class CompactShelter(
    @SerialName("i") val id: String,
    @SerialName("n") val name: String,
    @SerialName("a") val lat: Double,
    @SerialName("o") val lng: Double,
    @SerialName("p") val prefecture: String,
    @SerialName("h") val hazardMask: Int,
) {
    fun expand(): Shelter = Shelter(
        id = id,
        name = name,
        lat = lat,
        lng = lng,
        prefecture = prefecture,
        hazards = ShelterHazards.from(hazardMask),
    )
}

/** 展開済みの避難所。 */
data class Shelter(
    val id: String,             // 共通ID（国土地理院）
    val name: String,           // 施設・場所名
    val lat: Double,            // 緯度
    val lng: Double,            // 経度
    val prefecture: String,     // 都道府県（jp-xxxx 形式）
    val hazards: ShelterHazards,
)

/** 距離付き避難所（最近傍探索の結果）。 */
data class ShelterWithDistance(
    val shelter: Shelter,
    val distance: Double, // メートル
)
