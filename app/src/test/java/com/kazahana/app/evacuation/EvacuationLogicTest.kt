package com.kazahana.app.evacuation

import com.kazahana.app.data.evacuation.EvacuationConstants
import com.kazahana.app.data.evacuation.ShelterRepository
import com.kazahana.app.data.model.AlertLevel
import com.kazahana.app.data.model.ActiveAlert
import com.kazahana.app.data.model.Hazard
import com.kazahana.app.data.model.Prefecture
import com.kazahana.app.data.model.ShelterHazards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvacuationLogicTest {

    // 東京駅 / 大阪駅
    private val tokyoLat = 35.681236
    private val tokyoLng = 139.767125
    private val osakaLat = 34.702485
    private val osakaLng = 135.495951

    @Test
    fun haversine_tokyo_to_osaka_is_about_401km() {
        val d = ShelterRepository.haversineDistance(tokyoLat, tokyoLng, osakaLat, osakaLng)
        // 約 401km。±5km の許容。
        assertTrue("distance was $d", d in 396_000.0..406_000.0)
    }

    @Test
    fun haversine_same_point_is_zero() {
        val d = ShelterRepository.haversineDistance(tokyoLat, tokyoLng, tokyoLat, tokyoLng)
        assertEquals(0.0, d, 0.001)
    }

    @Test
    fun bearing_tokyo_to_osaka_is_west_southwest() {
        val b = ShelterRepository.bearing(tokyoLat, tokyoLng, osakaLat, osakaLng)
        // 初期方位角（大圏）は西南西 ≒ 255.6°。西南西帯（240–260°）で検証。
        assertTrue("bearing was $b", b in 240.0..260.0)
    }

    @Test
    fun bearing_is_normalised_0_to_360() {
        val b = ShelterRepository.bearing(osakaLat, osakaLng, tokyoLat, tokyoLng)
        assertTrue("bearing was $b", b in 0.0..360.0)
    }

    @Test
    fun hazard_bitmask_decodes_each_bit() {
        assertTrue(ShelterHazards.from(1).flood)
        assertTrue(ShelterHazards.from(2).landslide)
        assertTrue(ShelterHazards.from(4).stormSurge)
        assertTrue(ShelterHazards.from(8).earthquake)
        assertTrue(ShelterHazards.from(16).tsunami)
        assertTrue(ShelterHazards.from(32).fire)
        assertTrue(ShelterHazards.from(64).inlandFlood)
        assertTrue(ShelterHazards.from(128).volcano)
    }

    @Test
    fun hazard_bitmask_decodes_combination() {
        // flood(1) + tsunami(16) + earthquake(8) = 25
        val h = ShelterHazards.from(25)
        assertTrue(h.flood)
        assertTrue(h.earthquake)
        assertTrue(h.tsunami)
        assertTrue(!h.landslide)
        assertTrue(!h.volcano)
    }

    @Test
    fun prefecture_has_47_entries() {
        assertEquals(47, Prefecture.entries.size)
    }

    @Test
    fun prefecture_resolves_from_raw_value() {
        assertEquals(Prefecture.TOKYO, Prefecture.fromRawValue("jp-tokyo"))
        assertEquals(Prefecture.HOKKAIDO, Prefecture.fromRawValue("jp-hokkaido"))
        assertEquals(Prefecture.OKINAWA, Prefecture.fromRawValue("jp-okinawa"))
        assertNull(Prefecture.fromRawValue("jp-unknown"))
    }

    @Test
    fun prefecture_resolves_from_japanese_admin_area() {
        assertEquals(Prefecture.TOKYO, Prefecture.fromJapaneseName("東京都"))
        assertEquals(Prefecture.HOKKAIDO, Prefecture.fromJapaneseName("北海道"))
        assertEquals(Prefecture.KYOTO, Prefecture.fromJapaneseName("京都府"))
        assertEquals(Prefecture.OSAKA, Prefecture.fromJapaneseName("大阪府"))
        assertNull(Prefecture.fromJapaneseName(null))
        assertNull(Prefecture.fromJapaneseName("California"))
    }

    @Test
    fun all_prefecture_raw_values_are_jp_prefixed_and_unique() {
        val raws = Prefecture.entries.map { it.rawValue }
        assertTrue(raws.all { it.startsWith("jp-") })
        assertEquals(raws.size, raws.toSet().size)
    }

    @Test
    fun hazard_filters_for_heavy_rain() {
        val filters = EvacuationConstants.hazardFilters("heavy-rain-warning")
        assertTrue(filters.contains(Hazard.FLOOD))
        assertTrue(filters.contains(Hazard.LANDSLIDE))
        assertTrue(filters.contains(Hazard.INLAND_FLOOD))
    }

    @Test
    fun hazard_filters_unknown_type_returns_all() {
        val filters = EvacuationConstants.hazardFilters("snow-warning")
        assertEquals(Hazard.entries.size, filters.size)
    }

    @Test
    fun alert_level_ordering() {
        assertTrue(AlertLevel.LEVEL3.ordinal < AlertLevel.LEVEL4.ordinal)
        assertTrue(AlertLevel.LEVEL4.ordinal < AlertLevel.LEVEL5.ordinal)
        assertEquals(AlertLevel.LEVEL4, AlertLevel.fromValue("level4"))
        assertNull(AlertLevel.fromValue("cancelled"))
        assertNull(AlertLevel.fromValue("level2"))
    }

    @Test
    fun active_alert_dedupe_id_format() {
        val alert = ActiveAlert(
            type = "heavy-rain-warning",
            value = AlertLevel.LEVEL4,
            time = "2026-06-04T00:00:00Z",
            target = "jp-tokyo",
            receivedAt = 0L,
        )
        assertEquals("heavy-rain-warning|level4|2026-06-04T00:00:00Z|jp-tokyo", alert.id)
    }
}
