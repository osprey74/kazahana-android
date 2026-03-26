package com.kazahana.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PostDraft(
    val id: String,
    val createdAt: String,
    val text: String,
    val imageFileNames: List<String> = emptyList(),
    val videoFileName: String? = null,
    val threadgateIndex: Int = 0,
    val disableEmbedding: Boolean = false,
)

private val Context.draftDataStore by preferencesDataStore(name = "kazahana_drafts")

@Singleton
class DraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MAX_DRAFTS = 20
        private val KEY_DRAFTS = stringPreferencesKey("post_drafts")
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val draftsDir: File
        get() = File(context.filesDir, "drafts").also { it.mkdirs() }

    /**
     * Returns all drafts sorted by recency (newest first).
     */
    fun loadAll(): Flow<List<PostDraft>> = context.draftDataStore.data.map { prefs ->
        val raw = prefs[KEY_DRAFTS]
        if (raw.isNullOrEmpty()) emptyList()
        else try {
            json.decodeFromString<List<PostDraft>>(raw).sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Save a new draft. Images and video are provided as raw bytes.
     * Automatically deletes oldest drafts when the limit is exceeded.
     */
    suspend fun saveDraft(
        text: String,
        images: List<Pair<ByteArray, String>> = emptyList(),
        video: Pair<ByteArray, String>? = null,
        threadgateIndex: Int = 0,
        disableEmbedding: Boolean = false,
    ) {
        val draftId = UUID.randomUUID().toString()
        val dir = draftsDir

        // Save image files
        val imageFileNames = images.mapIndexed { index, (bytes, _) ->
            val fileName = "${draftId}_img_${index}.jpg"
            File(dir, fileName).writeBytes(bytes)
            fileName
        }

        // Save video file
        val videoFileName = video?.let { (bytes, _) ->
            val fileName = "${draftId}_video.mp4"
            File(dir, fileName).writeBytes(bytes)
            fileName
        }

        val draft = PostDraft(
            id = draftId,
            createdAt = Instant.now().toString(),
            text = text,
            imageFileNames = imageFileNames,
            videoFileName = videoFileName,
            threadgateIndex = threadgateIndex,
            disableEmbedding = disableEmbedding,
        )

        context.draftDataStore.edit { prefs ->
            val current = try {
                val raw = prefs[KEY_DRAFTS]
                if (raw.isNullOrEmpty()) mutableListOf()
                else json.decodeFromString<List<PostDraft>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }

            current.add(0, draft)

            // Enforce max limit — delete oldest drafts
            while (current.size > MAX_DRAFTS) {
                val removed = current.removeAt(current.size - 1)
                deleteDraftFiles(removed)
            }

            prefs[KEY_DRAFTS] = json.encodeToString(current)
        }
    }

    /**
     * Delete a single draft by ID.
     */
    suspend fun delete(id: String) {
        context.draftDataStore.edit { prefs ->
            val current = try {
                val raw = prefs[KEY_DRAFTS]
                if (raw.isNullOrEmpty()) mutableListOf()
                else json.decodeFromString<List<PostDraft>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }

            val removed = current.find { it.id == id }
            current.removeAll { it.id == id }
            removed?.let { deleteDraftFiles(it) }

            prefs[KEY_DRAFTS] = json.encodeToString(current)
        }
    }

    /**
     * Delete all drafts.
     */
    suspend fun deleteAll() {
        context.draftDataStore.edit { prefs ->
            val current = try {
                val raw = prefs[KEY_DRAFTS]
                if (raw.isNullOrEmpty()) emptyList()
                else json.decodeFromString<List<PostDraft>>(raw)
            } catch (_: Exception) {
                emptyList()
            }

            current.forEach { deleteDraftFiles(it) }
            prefs[KEY_DRAFTS] = json.encodeToString(emptyList<PostDraft>())
        }
    }

    /**
     * Load image bytes for a draft at the given index.
     */
    fun loadImageBytes(draft: PostDraft, index: Int): ByteArray? {
        val fileName = draft.imageFileNames.getOrNull(index) ?: return null
        val file = File(draftsDir, fileName)
        return if (file.exists()) file.readBytes() else null
    }

    /**
     * Load video bytes for a draft.
     */
    fun loadVideoBytes(draft: PostDraft): ByteArray? {
        val fileName = draft.videoFileName ?: return null
        val file = File(draftsDir, fileName)
        return if (file.exists()) file.readBytes() else null
    }

    private fun deleteDraftFiles(draft: PostDraft) {
        val dir = draftsDir
        draft.imageFileNames.forEach { fileName ->
            File(dir, fileName).delete()
        }
        draft.videoFileName?.let { fileName ->
            File(dir, fileName).delete()
        }
    }
}
