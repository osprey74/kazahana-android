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

    /** Plain SharedPreferences for account metadata (DID list, active DID) */
    private val metaPrefs: SharedPreferences =
        context.getSharedPreferences("kazahana_accounts", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateIfNeeded()
    }

    /** Save a session (keyed by DID). Also adds DID to saved list and sets it as active. */
    fun save(session: Session) {
        prefs.edit()
            .putString("$KEY_SESSION_PREFIX${session.did}", json.encodeToString(session))
            .apply()
        addDid(session.did)
        activeAccountDID = session.did
    }

    /** Load the active session. */
    fun load(): Session? {
        val activeDid = activeAccountDID ?: return loadAll().firstOrNull()
        return loadByDid(activeDid)
    }

    /** Load a session for a specific DID. */
    fun loadByDid(did: String): Session? {
        val raw = prefs.getString("$KEY_SESSION_PREFIX$did", null) ?: return null
        return try {
            json.decodeFromString<Session>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Load all saved sessions in order. */
    fun loadAll(): List<Session> {
        return savedAccountDIDs.mapNotNull { did -> loadByDid(did) }
    }

    /** Delete a specific account's session. Returns true if it was the active account. */
    fun delete(did: String): Boolean {
        prefs.edit().remove("$KEY_SESSION_PREFIX$did").apply()
        removeDid(did)
        val wasActive = activeAccountDID == did
        if (wasActive) {
            val remaining = savedAccountDIDs
            activeAccountDID = remaining.firstOrNull()
        }
        return wasActive
    }

    /** Clear all sessions (full reset). */
    fun clear() {
        val dids = savedAccountDIDs
        val editor = prefs.edit()
        dids.forEach { did -> editor.remove("$KEY_SESSION_PREFIX$did") }
        editor.apply()
        metaPrefs.edit()
            .remove(KEY_SAVED_DIDS)
            .remove(KEY_ACTIVE_DID)
            .apply()
    }

    /** The currently active account DID. */
    var activeAccountDID: String?
        get() = metaPrefs.getString(KEY_ACTIVE_DID, null)
        set(value) {
            metaPrefs.edit().putString(KEY_ACTIVE_DID, value).apply()
        }

    /** Ordered list of saved account DIDs. */
    val savedAccountDIDs: List<String>
        get() {
            val raw = metaPrefs.getString(KEY_SAVED_DIDS, null) ?: return emptyList()
            return raw.split(DID_SEPARATOR).filter { it.isNotEmpty() }
        }

    private fun addDid(did: String) {
        val current = savedAccountDIDs.toMutableList()
        if (did !in current) {
            current.add(did)
        }
        metaPrefs.edit().putString(KEY_SAVED_DIDS, current.joinToString(DID_SEPARATOR)).apply()
    }

    private fun removeDid(did: String) {
        val current = savedAccountDIDs.toMutableList()
        current.remove(did)
        metaPrefs.edit().putString(KEY_SAVED_DIDS, current.joinToString(DID_SEPARATOR)).apply()
    }

    /**
     * One-time migration from v1.0 single-session format ("session" key)
     * to v1.1 multi-session format ("session:{did}" key).
     */
    private fun migrateIfNeeded() {
        if (savedAccountDIDs.isNotEmpty()) return
        val raw = prefs.getString(KEY_SESSION_LEGACY, null) ?: return
        try {
            val session = json.decodeFromString<Session>(raw)
            prefs.edit()
                .putString("$KEY_SESSION_PREFIX${session.did}", raw)
                .remove(KEY_SESSION_LEGACY)
                .apply()
            addDid(session.did)
            activeAccountDID = session.did
        } catch (_: Exception) {
            // Corrupted legacy data — just remove it
            prefs.edit().remove(KEY_SESSION_LEGACY).apply()
        }
    }

    companion object {
        private const val KEY_SESSION_LEGACY = "session"
        private const val KEY_SESSION_PREFIX = "session:"
        private const val KEY_ACTIVE_DID = "activeAccountDID"
        private const val KEY_SAVED_DIDS = "savedAccountDIDs"
        private const val DID_SEPARATOR = "\n"
    }
}
