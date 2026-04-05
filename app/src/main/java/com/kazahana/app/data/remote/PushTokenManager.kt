package com.kazahana.app.data.remote

import android.util.Log
import com.kazahana.app.BuildConfig
import com.kazahana.app.data.local.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenManager @Inject constructor(
    private val sessionStore: SessionStore,
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "PushTokenManager"
        private const val BASE_URL = "https://kazahana-push-backend.fly.dev"
    }

    /**
     * Register the FCM token for all saved accounts.
     */
    suspend fun registerTokenForAllAccounts(token: String) {
        val dids = sessionStore.savedAccountDIDs
        for (did in dids) {
            registerToken(did, token)
        }
    }

    /**
     * Register the FCM token for a specific account.
     */
    suspend fun registerToken(did: String, token: String) {
        val body = JSONObject().apply {
            put("did", did)
            put("token", token)
            put("platform", "android")
        }
        try {
            val response = postRequest("/api/device-token", body)
            if (response) {
                Log.d(TAG, "Token registered for $did")
            } else {
                Log.w(TAG, "Failed to register token for $did")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering token for $did", e)
        }
    }

    /**
     * Unregister the FCM token for a specific account.
     */
    suspend fun unregisterToken(did: String) {
        val body = JSONObject().apply {
            put("did", did)
            put("platform", "android")
        }
        try {
            val response = deleteRequest("/api/device-token", body)
            if (response) {
                Log.d(TAG, "Token unregistered for $did")
            } else {
                Log.w(TAG, "Failed to unregister token for $did")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering token for $did", e)
        }
    }

    /**
     * Unregister tokens for all saved accounts.
     */
    suspend fun unregisterTokenForAllAccounts() {
        val dids = sessionStore.savedAccountDIDs
        for (did in dids) {
            unregisterToken(did)
        }
    }

    private suspend fun postRequest(path: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .addHeader("Authorization", "Bearer ${BuildConfig.PUSH_API_SECRET}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        }

    private suspend fun deleteRequest(path: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .addHeader("Authorization", "Bearer ${BuildConfig.PUSH_API_SECRET}")
                .addHeader("Content-Type", "application/json")
                .delete(body.toString().toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        }
}
