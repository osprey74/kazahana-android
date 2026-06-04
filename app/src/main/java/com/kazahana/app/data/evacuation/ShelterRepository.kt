package com.kazahana.app.data.evacuation

import android.content.Context
import com.kazahana.app.data.AppJson
import com.kazahana.app.data.model.CompactShelter
import com.kazahana.app.data.model.Hazard
import com.kazahana.app.data.model.Shelter
import com.kazahana.app.data.model.ShelterWithDistance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.zip.InflaterInputStream
import java.util.zip.Inflater
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 端末同梱の避難所データ（assets/shelters.zlib）を読み込み、最近傍探索を提供する。
 *
 * データは raw deflate（zlib/gzip ヘッダーなし、wbits=-15）で圧縮されているため
 * `Inflater(nowrap = true)` で解凍する。オフライン動作を保証（ネットワーク不要）。
 */
@Singleton
class ShelterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val loadMutex = Mutex()

    @Volatile
    private var byPrefecture: Map<String, List<Shelter>>? = null

    @Volatile
    private var byId: Map<String, Shelter>? = null

    /** データをロード（メモリキャッシュ）。初回のみ解凍・パースを行う。 */
    suspend fun ensureLoaded() {
        if (byPrefecture != null) return
        loadMutex.withLock {
            if (byPrefecture != null) return
            withContext(Dispatchers.IO) {
                val json = context.assets.open(ASSET_NAME).use { input ->
                    InflaterInputStream(input, Inflater(true)).use { inflater ->
                        inflater.readBytes().toString(Charsets.UTF_8)
                    }
                }
                val compact = AppJson.decodeFromString<List<CompactShelter>>(json)
                val shelters = compact.map { it.expand() }
                byPrefecture = shelters.groupBy { it.prefecture }
                byId = shelters.associateBy { it.id }
            }
        }
    }

    /** 全件数（テスト・診断用）。ロード前は 0。 */
    val loadedCount: Int get() = byId?.size ?: 0

    fun shelterById(id: String): Shelter? = byId?.get(id)

    /**
     * 指定都道府県内で最近傍の避難所を探索（災害種別 OR フィルタ・必須）。
     * 1 つでも対応する hazard があれば候補に含める。
     */
    suspend fun findNearest(
        prefecture: String,
        lat: Double,
        lng: Double,
        hazards: List<Hazard>,
        limit: Int = 5,
    ): List<ShelterWithDistance> {
        ensureLoaded()
        val candidates = byPrefecture?.get(prefecture) ?: emptyList()
        return rank(candidates, lat, lng, hazards, limit)
    }

    /**
     * 都道府県を限定せず全件から最近傍を探索（測位が都道府県に解決できない場合のフォールバック）。
     */
    suspend fun findNearestAll(
        lat: Double,
        lng: Double,
        hazards: List<Hazard>,
        limit: Int = 5,
    ): List<ShelterWithDistance> {
        ensureLoaded()
        val all = byId?.values ?: emptyList()
        return rank(all, lat, lng, hazards, limit)
    }

    private fun rank(
        candidates: Iterable<Shelter>,
        lat: Double,
        lng: Double,
        hazards: List<Hazard>,
        limit: Int,
    ): List<ShelterWithDistance> {
        return candidates
            .filter { shelter -> hazards.any { shelter.hazards.supports(it) } }
            .map { ShelterWithDistance(it, haversineDistance(lat, lng, it.lat, it.lng)) }
            .sortedBy { it.distance }
            .take(limit)
    }

    companion object {
        private const val ASSET_NAME = "shelters.zlib"
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Haversine 距離（メートル）。 */
        fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_M * c
        }

        /** 2点間の方位角（度、真北基準で時計回り 0–360）。 */
        fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLng = Math.toRadians(lng2 - lng1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val y = sin(dLng) * cos(rLat2)
            val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLng)
            val deg = Math.toDegrees(atan2(y, x))
            return (deg + 360.0) % 360.0
        }
    }
}
