package com.kazahana.app.evacuation

import com.kazahana.app.data.AppJson
import com.kazahana.app.data.model.CompactShelter
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * 端末同梱データ（shelters.zlib）が ShelterRepository と同じ手順（raw deflate 解凍 →
 * CompactShelter デコード）で正しく読めることを検証する。データ形式は最重要の前提のため、
 * 実ファイルを直接読んで件数・都道府県・座標の妥当性を確認する。
 */
class ShelterDataDecodeTest {

    private fun assetFile(): File? {
        val candidates = listOf(
            File("src/main/assets/shelters.zlib"),
            File("app/src/main/assets/shelters.zlib"),
        )
        return candidates.firstOrNull { it.exists() }
    }

    @Test
    fun shelters_zlib_decodes_to_many_shelters() {
        val file = assetFile()
        assumeTrue("shelters.zlib not found in working dir", file != null)

        val json = file!!.inputStream().use { input ->
            InflaterInputStream(input, Inflater(true)).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val shelters = AppJson.decodeFromString<List<CompactShelter>>(json)

        // 全国版なので 5 万件以上を期待
        assertTrue("count was ${shelters.size}", shelters.size > 50_000)

        // 都道府県は jp-xxx 形式、座標は日本の範囲内
        val sample = shelters.first()
        assertTrue(sample.prefecture.startsWith("jp-"))
        assertTrue(shelters.all { it.lat in 20.0..46.0 })
        assertTrue(shelters.all { it.lng in 122.0..154.0 })

        // 47 都道府県すべてが含まれる
        val prefs = shelters.map { it.prefecture }.toSet()
        assertTrue("prefecture count ${prefs.size}", prefs.size == 47)
    }
}
