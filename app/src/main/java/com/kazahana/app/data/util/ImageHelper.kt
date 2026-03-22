package com.kazahana.app.data.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.kazahana.app.data.model.AspectRatio
import dagger.hilt.android.qualifiers.ApplicationContext
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
