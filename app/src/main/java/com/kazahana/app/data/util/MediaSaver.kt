package com.kazahana.app.data.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads and saves images/videos from URLs to the device's media library.
 */
object MediaSaver {

    private val httpClient = HttpClient(OkHttp)

    /**
     * Detect image format from magic bytes.
     * Returns MIME type and file extension.
     */
    private fun detectFormat(bytes: ByteArray): Pair<String, String> {
        if (bytes.size < 4) return "image/jpeg" to "jpg"
        return when {
            // PNG: 89 50 4E 47
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ->
                "image/png" to "png"
            // GIF: 47 49 46
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() ->
                "image/gif" to "gif"
            // WebP: RIFF....WEBP
            bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() ->
                "image/webp" to "webp"
            else -> "image/jpeg" to "jpg"
        }
    }

    /**
     * Save a single image from URL to device gallery.
     */
    suspend fun saveImage(context: Context, imageUrl: String, index: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get(imageUrl)
                if (!response.status.isSuccess()) return@withContext false
                val bytes = response.readBytes()
                val (mimeType, ext) = detectFormat(bytes)
                val fileName = "kazahana_${System.currentTimeMillis()}_$index.$ext"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/kazahana")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: return@withContext false

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }

                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Save a video from HLS URL or thumbnail URL.
     * For HLS streams, we save the thumbnail image instead (HLS cannot be downloaded as a single file).
     * For direct video URLs, we download and save.
     */
    suspend fun saveVideo(context: Context, videoUrl: String, thumbnailUrl: String?): Boolean {
        // HLS playlists (.m3u8) cannot be saved as a single file.
        // Save the thumbnail instead if available.
        val urlToSave = if (videoUrl.contains(".m3u8") && thumbnailUrl != null) {
            thumbnailUrl
        } else {
            videoUrl
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get(urlToSave)
                if (!response.status.isSuccess()) return@withContext false
                val bytes = response.readBytes()

                val isVideo = !urlToSave.contains(".m3u8") && !urlToSave.endsWith(".jpg") && !urlToSave.endsWith(".png")
                val fileName = "kazahana_${System.currentTimeMillis()}"

                if (isVideo) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/kazahana")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }

                    val uri = context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        contentValues,
                    ) ?: return@withContext false

                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)
                    }
                    true
                } else {
                    // Save thumbnail as image
                    val (mimeType, ext) = detectFormat(bytes)
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.$ext")
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/kazahana")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues,
                    ) ?: return@withContext false

                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)
                    }
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
