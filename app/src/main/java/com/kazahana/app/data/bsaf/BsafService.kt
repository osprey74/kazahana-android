package com.kazahana.app.data.bsaf

import androidx.compose.ui.graphics.Color
import com.kazahana.app.data.AppJson
import com.kazahana.app.data.model.BsafBotDefinition
import com.kazahana.app.data.model.BsafParsedTags
import com.kazahana.app.data.model.BsafRegisteredBot
import com.kazahana.app.data.model.BsafRegisteredFilter
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BsafService @Inject constructor() {

    // ── Tag parsing ──

    fun parseBsafTags(tags: List<String>): BsafParsedTags? {
        if (!tags.any { it == "bsaf:v1" }) return null
        var type = ""
        var value = ""
        var time = ""
        var target = ""
        var source = ""
        for (tag in tags) {
            val colonIdx = tag.indexOf(':')
            if (colonIdx < 0) continue
            val key = tag.substring(0, colonIdx)
            val v = tag.substring(colonIdx + 1)
            when (key) {
                "type" -> type = v
                "value" -> value = v
                "time" -> time = v
                "target" -> target = v
                "source" -> source = v
            }
        }
        return BsafParsedTags(
            version = "v1",
            type = type,
            value = value,
            time = time,
            target = target,
            source = source,
        )
    }

    // ── Filter logic ──

    fun shouldShowBsafPost(parsed: BsafParsedTags, bot: BsafRegisteredBot): Boolean {
        for (filter in bot.filters) {
            if (filter.enabledValues.isEmpty()) continue
            val postValue = when (filter.tag) {
                "type" -> parsed.type
                "value" -> parsed.value
                "target" -> parsed.target
                else -> continue
            }
            if (postValue.isNotEmpty() && postValue !in filter.enabledValues) return false
        }
        return true
    }

    // ── Duplicate detection ──

    fun duplicateKey(parsed: BsafParsedTags): String {
        return "${parsed.type}|${parsed.value}|${parsed.time}|${parsed.target}"
    }

    // ── Severity color ──

    fun severityBorderColor(value: String): Color {
        return when (value) {
            // Earthquake intensities
            "7", "6+", "6-" -> Color(0xFFBE185D)  // pink
            "5+", "5-" -> Color(0xFFDC2626)        // red
            "4" -> Color(0xFFD97706)                // orange
            // Weather
            "special-warning" -> Color(0xFFBE185D)
            "severe-warning", "warning" -> Color(0xFFD97706)
            "advisory" -> Color(0xFFCA8A04)          // yellow
            // Default
            else -> Color(0xFF16A34A)                // green
        }
    }

    // ── URL helpers ──

    private fun toRawUrl(url: String): String {
        // Convert GitHub blob URLs to raw content URLs
        if (url.contains("github.com") && url.contains("/blob/")) {
            return url
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        }
        return url
    }

    // ── Bot definition fetching ──

    suspend fun fetchBotDefinition(url: String): Result<BsafBotDefinition> {
        return try {
            val rawUrl = toRawUrl(url.trim())
            val client = HttpClient()
            val response = client.get(rawUrl)
            client.close()
            if (response.status.value != 200) {
                return Result.failure(Exception("HTTP ${response.status.value}"))
            }
            val body = response.bodyAsText()
            val definition = AppJson.decodeFromString<BsafBotDefinition>(body)
            Result.success(definition)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Auto-update ──

    suspend fun checkBotUpdate(bot: BsafRegisteredBot): BsafRegisteredBot? {
        return try {
            val definition = fetchBotDefinition(bot.selfUrl).getOrNull() ?: return null
            if (definition.updatedAt == bot.updatedAt) return null

            // Merge filters: keep existing enabled values, add new options as enabled
            val mergedFilters = definition.filters.map { newFilter ->
                val existing = bot.filters.find { it.tag == newFilter.tag }
                val existingEnabled = existing?.enabledValues?.toSet() ?: emptySet()
                val allNewValues = newFilter.options.map { it.value }.toSet()
                val enabled = if (existing != null) {
                    // Keep previously enabled values that still exist + add new values
                    val keptEnabled = existingEnabled.intersect(allNewValues)
                    val brandNew = allNewValues - existing.options.map { it.value }.toSet()
                    (keptEnabled + brandNew).toList()
                } else {
                    newFilter.options.map { it.value }
                }
                BsafRegisteredFilter(
                    tag = newFilter.tag,
                    label = newFilter.label,
                    options = newFilter.options,
                    enabledValues = enabled,
                )
            }

            bot.copy(
                updatedAt = definition.updatedAt,
                filters = mergedFilters,
                lastCheckedAt = java.time.Instant.now().toString(),
            )
        } catch (_: Exception) {
            null
        }
    }
}
