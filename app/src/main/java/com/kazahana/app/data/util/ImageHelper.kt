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
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

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
        // atproto `#4823` raised `app.bsky.embed.images` blob limit from 1 MB to 2 MB
        // (merged 2026-04-08). Legacy PDSes may still reject at 1 MB — callers should
        // fall back by recompressing to LEGACY_MAX_FILE_SIZE on HTTP 413/400.
        const val MAX_FILE_SIZE = 2_000_000
        const val LEGACY_MAX_FILE_SIZE = 1_000_000

        // social-app `#10117` downsizing: start at 4000px on the long edge, iterate
        // ×0.8 up to 5 stages (4000 → 3200 → 2560 → 2048 → 1638), JPEG quality 85.
        private const val INITIAL_MAX_DIMENSION = 4000
        private const val DOWNSIZE_FACTOR = 0.8
        private const val DOWNSIZE_STEPS = 5
        private const val JPEG_QUALITY = 85
        private const val FALLBACK_QUALITY_MIN = 30
        private const val FALLBACK_QUALITY_LAST = 20
    }

    /**
     * Read image bytes from URI, compressing to JPEG if the file exceeds [maxBytes].
     * Returns compressed bytes and "image/jpeg" mime type, or original bytes if already
     * small enough and the source dimensions fit within [INITIAL_MAX_DIMENSION].
     */
    fun readAndCompressImage(
        uri: Uri,
        mimeType: String,
        maxBytes: Int = MAX_FILE_SIZE,
    ): Pair<ByteArray, String>? {
        val originalBytes = readBytes(uri) ?: return null
        val bounds = decodeBounds(uri) ?: return Pair(originalBytes, mimeType)
        val sourceMaxDim = max(bounds.first, bounds.second)

        if (originalBytes.size <= maxBytes && sourceMaxDim <= INITIAL_MAX_DIMENSION) {
            return Pair(originalBytes, mimeType)
        }

        val decoded = decodeDownsampled(uri, INITIAL_MAX_DIMENSION)
            ?: return Pair(originalBytes, mimeType)
        return try {
            Pair(compressWithDownsizing(decoded, maxBytes), "image/jpeg")
        } finally {
            decoded.recycle()
        }
    }

    /**
     * Recompress JPEG [source] bytes to fit under [maxBytes] using the official-compatible
     * 5-step downsizing strategy. Used for watermarked images where the raw output already
     * has a bitmap in memory semantically, and for legacy-PDS fallback recompression.
     */
    fun compressBytes(source: ByteArray, maxBytes: Int = MAX_FILE_SIZE): ByteArray {
        if (source.size <= maxBytes) return source
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return source
        return try {
            compressWithDownsizing(bitmap, maxBytes)
        } finally {
            bitmap.recycle()
        }
    }

    private fun compressWithDownsizing(source: Bitmap, maxBytes: Int): ByteArray {
        var targetDim = INITIAL_MAX_DIMENSION.toDouble()
        var lastBytes: ByteArray? = null
        repeat(DOWNSIZE_STEPS) {
            val scaled = scaleToMaxDim(source, targetDim.roundToInt())
            val bytes = scaled.toJpegBytes(JPEG_QUALITY)
            if (scaled !== source) scaled.recycle()
            if (bytes.size <= maxBytes) return bytes
            lastBytes = bytes
            targetDim *= DOWNSIZE_FACTOR
        }

        // Still too large at the smallest dimension — keep that size and drop quality.
        val finalDim = (INITIAL_MAX_DIMENSION * DOWNSIZE_FACTOR.pow(DOWNSIZE_STEPS - 1)).roundToInt()
        val scaled = scaleToMaxDim(source, finalDim)
        try {
            var quality = JPEG_QUALITY - 15
            while (quality >= FALLBACK_QUALITY_MIN) {
                val bytes = scaled.toJpegBytes(quality)
                if (bytes.size <= maxBytes) return bytes
                quality -= 10
            }
            val last = scaled.toJpegBytes(FALLBACK_QUALITY_LAST)
            return if (lastBytes != null && last.size > lastBytes!!.size) lastBytes!! else last
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun scaleToMaxDim(bitmap: Bitmap, maxDim: Int): Bitmap {
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / currentMax
        val newW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun decodeBounds(uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeDownsampled(uri: Uri, targetMaxDim: Int): Bitmap? {
        val bounds = decodeBounds(uri) ?: return null
        val sourceMaxDim = max(bounds.first, bounds.second)
        // Leave headroom (~2x target) so subsequent createScaledBitmap produces smooth output.
        var sampleSize = 1
        while (sourceMaxDim / (sampleSize * 2) > targetMaxDim) sampleSize *= 2
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
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
        val bounds = decodeBounds(uri) ?: return null
        return AspectRatio(width = bounds.first, height = bounds.second)
    }

    /**
     * Decode aspect ratio from raw image bytes — used as a fallback when the
     * source URI isn't accessible (e.g. after watermark application produced
     * in-memory bytes).
     */
    fun getAspectRatio(bytes: ByteArray): AspectRatio? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                AspectRatio(width = options.outWidth, height = options.outHeight)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
