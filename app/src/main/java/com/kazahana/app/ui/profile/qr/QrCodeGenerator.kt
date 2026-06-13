package com.kazahana.app.ui.profile.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates QR code bitmaps from arbitrary text/URLs using ZXing (offline, no ML Kit).
 *
 * Used by the profile QR sheet to encode `https://bsky.app/profile/{handle}`.
 */
object QrCodeGenerator {

    /**
     * Encode [content] into a square QR code [Bitmap].
     *
     * @param content the text/URL to encode
     * @param sizePx output bitmap edge length in pixels
     * @param foregroundColor module (dot) color, defaults to black
     * @param backgroundColor background color, defaults to white
     * @return the generated [Bitmap], or null if encoding fails
     */
    fun encode(
        content: String,
        sizePx: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints,
            )
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix[x, y]) foregroundColor else backgroundColor
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }
}
