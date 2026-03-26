package com.kazahana.app.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import com.kazahana.app.data.model.AspectRatio
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 950_000 // 950 KB (Bluesky limit ~976 KB)
        private const val MAX_WIDTH = 2048
    }

    /**
     * Read image bytes from URI, compressing to JPEG if the file exceeds
     * Bluesky's upload limit. Returns compressed bytes and "image/jpeg" mime type,
     * or original bytes if already small enough.
     */
    fun readAndCompressImage(uri: Uri, mimeType: String): Pair<ByteArray, String>? {
        val originalBytes = readBytes(uri) ?: return null
        if (originalBytes.size <= MAX_FILE_SIZE) return Pair(originalBytes, mimeType)

        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
            ?: return Pair(originalBytes, mimeType)

        // Resize if wider than MAX_WIDTH
        val scaled = if (bitmap.width > MAX_WIDTH) {
            val ratio = MAX_WIDTH.toFloat() / bitmap.width
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, newHeight, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        // Compress with decreasing quality until under limit
        var quality = 85
        while (quality >= 30) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= MAX_FILE_SIZE) {
                if (scaled !== bitmap) scaled.recycle()
                return Pair(bytes, "image/jpeg")
            }
            quality -= 10
        }

        // Fallback: lowest quality
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 20, out)
        if (scaled !== bitmap) scaled.recycle()
        return Pair(out.toByteArray(), "image/jpeg")
    }

    /**
     * Rotate a bitmap by the given degrees (should be a multiple of 90).
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Crop a bitmap to the given rectangle region.
     */
    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val x = rect.left.coerceIn(0, bitmap.width - 1)
        val y = rect.top.coerceIn(0, bitmap.height - 1)
        val w = rect.width().coerceIn(1, bitmap.width - x)
        val h = rect.height().coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    /**
     * Save a bitmap to the app's cache directory and return a content URI.
     */
    fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val file = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    /**
     * Decode a URI into a Bitmap, returning null on failure.
     */
    fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getAspectRatio(uri: Uri): AspectRatio? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                AspectRatio(width = options.outWidth, height = options.outHeight)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
