package com.kazahana.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kazahana.app.data.model.Session
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
class SessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "kazahana_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_SESSION, json.encodeToString(session))
            .apply()
    }

    fun load(): Session? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            json.decodeFromString<Session>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        private const val KEY_SESSION = "session"
    }
}
