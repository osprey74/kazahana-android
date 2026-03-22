package com.kazahana.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kazahana_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class ModerationPref { HIDE, WARN, SHOW }

enum class AppLocale(val tag: String, val labelKey: String) {
    SYSTEM("", "lang_system"),
    JA("ja", "lang_ja"),
    EN("en", "lang_en"),
    PT("pt", "lang_pt"),
    DE("de", "lang_de"),
    ZH_TW("zh-TW", "lang_zh_tw"),
    ZH_CN("zh-CN", "lang_zh_cn"),
    FR("fr", "lang_fr"),
    KO("ko", "lang_ko"),
    ES("es", "lang_es"),
    RU("ru", "lang_ru"),
    ID("id", "lang_id"),
    ;

    companion object {
        fun fromTag(tag: String): AppLocale {
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LOCALE = stringPreferencesKey("app_locale")
        val ADULT_CONTENT_ENABLED = booleanPreferencesKey("adult_content_enabled")
        val NUDITY_PREF = stringPreferencesKey("moderation_nudity")
        val SEXUAL_PREF = stringPreferencesKey("moderation_sexual")
        val PORN_PREF = stringPreferencesKey("moderation_porn")
        val GRAPHIC_MEDIA_PREF = stringPreferencesKey("moderation_graphic_media")
        val POLL_INTERVAL = intPreferencesKey("poll_interval_seconds")
        val PINNED_FEED_URIS = stringPreferencesKey("pinned_feed_uris")
        val HIDDEN_FEED_URIS = stringPreferencesKey("hidden_feed_uris")
        val SHOW_ALL_FEEDS_IN_SELECTOR = booleanPreferencesKey("show_all_feeds_in_selector")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    /** Raw BCP-47 tag, empty string means "system default" */
    val appLocale: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCALE] ?: ""
    }

    val appLocaleEnum: Flow<AppLocale> = context.dataStore.data.map { prefs ->
        AppLocale.fromTag(prefs[Keys.LOCALE] ?: "")
    }

    val adultContentEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ADULT_CONTENT_ENABLED] ?: false
    }

    val nudityPref: Flow<ModerationPref> = context.dataStore.data.map { prefs ->
        parseModerationPref(prefs[Keys.NUDITY_PREF])
    }

    val sexualPref: Flow<ModerationPref> = context.dataStore.data.map { prefs ->
        parseModerationPref(prefs[Keys.SEXUAL_PREF])
    }

    val pornPref: Flow<ModerationPref> = context.dataStore.data.map { prefs ->
        parseModerationPref(prefs[Keys.PORN_PREF])
    }

    val graphicMediaPref: Flow<ModerationPref> = context.dataStore.data.map { prefs ->
        parseModerationPref(prefs[Keys.GRAPHIC_MEDIA_PREF])
    }

    val pollIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.POLL_INTERVAL] ?: 60
    }

    /** JSON-encoded list of feed URIs in display order */
    val pinnedFeedURIs: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseStringList(prefs[Keys.PINNED_FEED_URIS])
    }

    /** JSON-encoded list of hidden feed URIs */
    val hiddenFeedURIs: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseStringList(prefs[Keys.HIDDEN_FEED_URIS])
    }

    val showAllFeedsInSelector: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_ALL_FEEDS_IN_SELECTOR] ?: true
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }

    suspend fun setAppLocale(locale: AppLocale) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCALE] = locale.tag
        }
    }

    suspend fun setAdultContentEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADULT_CONTENT_ENABLED] = enabled
        }
    }

    suspend fun setModerationPref(label: String, pref: ModerationPref) {
        val key = when (label) {
            "nudity" -> Keys.NUDITY_PREF
            "sexual" -> Keys.SEXUAL_PREF
            "porn" -> Keys.PORN_PREF
            "graphic-media" -> Keys.GRAPHIC_MEDIA_PREF
            else -> return
        }
        context.dataStore.edit { prefs ->
            prefs[key] = when (pref) {
                ModerationPref.HIDE -> "hide"
                ModerationPref.WARN -> "warn"
                ModerationPref.SHOW -> "show"
            }
        }
    }

    suspend fun setPollInterval(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.POLL_INTERVAL] = seconds
        }
    }

    suspend fun setPinnedFeedURIs(uris: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PINNED_FEED_URIS] = encodeStringList(uris)
        }
    }

    suspend fun setShowAllFeedsInSelector(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_ALL_FEEDS_IN_SELECTOR] = enabled
        }
    }

    companion object {
        private const val MAX_SEARCH_HISTORY = 20
    }

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseStringList(prefs[Keys.SEARCH_HISTORY])
    }

    suspend fun addSearchHistory(query: String) {
        context.dataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.SEARCH_HISTORY]).toMutableList()
            current.remove(query) // remove duplicate
            current.add(0, query) // add to front
            if (current.size > MAX_SEARCH_HISTORY) {
                current.subList(MAX_SEARCH_HISTORY, current.size).clear()
            }
            prefs[Keys.SEARCH_HISTORY] = encodeStringList(current)
        }
    }

    suspend fun removeSearchHistory(query: String) {
        context.dataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.SEARCH_HISTORY]).toMutableList()
            current.remove(query)
            prefs[Keys.SEARCH_HISTORY] = encodeStringList(current)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SEARCH_HISTORY)
        }
    }

    suspend fun setHiddenFeedURIs(uris: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDDEN_FEED_URIS] = encodeStringList(uris)
        }
    }

    private fun parseStringList(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeStringList(list: List<String>): String {
        val json = kotlinx.serialization.json.Json
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
            list,
        )
    }

    private fun parseModerationPref(value: String?): ModerationPref {
        return when (value) {
            "hide" -> ModerationPref.HIDE
            "show" -> ModerationPref.SHOW
            else -> ModerationPref.WARN
        }
    }
}
