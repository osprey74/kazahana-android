package com.kazahana.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object WatermarkService {

    fun hexToColor(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.WHITE
        }
    }

    /**
     * Build the preset label map from localized strings.
     * @param copyrightLabel localized "無断転載禁止" equivalent
     * @param aiJaLabel localized "AI学習・転載禁止" equivalent
     * @param photoLabel localized "撮影・編集" equivalent
     */
    fun buildLabelMap(
        copyrightLabel: String,
        aiJaLabel: String,
        photoLabel: String,
    ): Map<WatermarkPreset, String> = mapOf(
        WatermarkPreset.COPYRIGHT to copyrightLabel,
        WatermarkPreset.AI_JA to aiJaLabel,
        WatermarkPreset.AI_EN to "No AI Training",
        WatermarkPreset.AI_BOTH to "No AI Training / $copyrightLabel",
        WatermarkPreset.PHOTO to photoLabel,
    )

    fun resolveLines(
        settings: WatermarkSettings,
        handle: String,
        maxWidth: Float,
        paint: Paint,
        labels: Map<WatermarkPreset, String>,
    ): List<String> {
        val h = "© @$handle"
        val preset = settings.presetEnum

        if (preset == WatermarkPreset.CUSTOM) {
            val lines = settings.customText.split("\n").filter { it.isNotEmpty() }
            return if (lines.isEmpty()) listOf(h) else lines
        }

        val label = labels[preset] ?: return listOf(h)

        val single = "$h\u3000$label"
        return if (paint.measureText(single) <= maxWidth) listOf(single) else listOf(h, label)
    }

    fun apply(source: Bitmap, settings: WatermarkSettings, handle: String, labels: Map<WatermarkPreset, String>): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        drawWatermark(canvas, source.width.toFloat(), source.height.toFloat(), settings, handle, labels)
        return result
    }

    fun drawWatermark(
        canvas: Canvas,
        imgWidth: Float,
        imgHeight: Float,
        settings: WatermarkSettings,
        handle: String,
        labels: Map<WatermarkPreset, String>,
    ) {
        val baseFontSize = max(settings.fontSize, imgWidth * 0.022f)
        val textAlpha = (settings.opacity / 100f * 255).roundToInt()
        val bgAlpha = (settings.opacity / 100f * 0.6f * 255).roundToInt()
        val lineGap = baseFontSize * 0.3f
        val margin = imgWidth * 0.015f

        val textColor = hexToColor(settings.textColor)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            alpha = textAlpha
            textSize = baseFontSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val padX = baseFontSize * 1.0f
        val padY = baseFontSize * 0.7f
        val maxAvailableWidth = imgWidth - margin * 2 - padX * 2
        val lines = resolveLines(settings, handle, maxAvailableWidth, textPaint, labels)

        val maxLineWidth = lines.maxOf { textPaint.measureText(it) }
        val boxW = maxLineWidth + padX * 2
        val boxH = baseFontSize * lines.size + lineGap * (lines.size - 1) + padY * 2

        val pos = settings.positionEnum

        if (pos == WatermarkPosition.TILE) {
            drawTiled(canvas, imgWidth, imgHeight, lines, textPaint, baseFontSize,
                lineGap, padX, padY, boxW, boxH, bgAlpha, textAlpha, textColor)
            return
        }

        val (boxX, boxY) = if (pos == WatermarkPosition.RANDOM) {
            val maxX = (imgWidth - boxW - margin * 2).coerceAtLeast(0f)
            val maxY = (imgHeight - boxH - margin * 2).coerceAtLeast(0f)
            Pair(
                margin + (Math.random() * maxX).toFloat(),
                margin + (Math.random() * maxY).toFloat(),
            )
        } else {
            calcOrigin(pos, imgWidth, imgHeight, boxW, boxH, margin)
        }

        drawBox(canvas, boxX, boxY, boxW, boxH, lines, textPaint, baseFontSize,
            lineGap, padX, padY, bgAlpha)
    }

    private fun drawBox(
        canvas: Canvas,
        boxX: Float,
        boxY: Float,
        boxW: Float,
        boxH: Float,
        lines: List<String>,
        textPaint: Paint,
        baseFontSize: Float,
        lineGap: Float,
        padX: Float,
        padY: Float,
        bgAlpha: Int,
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = bgAlpha
        }
        canvas.drawRoundRect(RectF(boxX, boxY, boxX + boxW, boxY + boxH), 4f, 4f, bgPaint)
        for ((i, line) in lines.withIndex()) {
            val ly = boxY + padY + baseFontSize + i * (baseFontSize + lineGap)
            canvas.drawText(line, boxX + padX, ly, textPaint)
        }
    }

    private fun drawTiled(
        canvas: Canvas,
        imgWidth: Float,
        imgHeight: Float,
        lines: List<String>,
        textPaint: Paint,
        baseFontSize: Float,
        lineGap: Float,
        padX: Float,
        padY: Float,
        boxW: Float,
        boxH: Float,
        bgAlpha: Int,
        textAlpha: Int,
        textColor: Int,
    ) {
        val spacing = sqrt(boxW * boxH / 0.2f)
        val centerX = imgWidth / 2
        val centerY = imgHeight / 2

        val startCol = -((centerX / spacing).toInt() + 2)
        val endCol = ((imgWidth - centerX) / spacing).toInt() + 2
        val startRow = -((centerY / spacing).toInt() + 2)
        val endRow = ((imgHeight - centerY) / spacing).toInt() + 2

        for (row in startRow..endRow) {
            val offsetX = if (row % 2 != 0) spacing / 2 else 0f
            for (col in startCol..endCol) {
                val tileX = centerX - boxW / 2 + col * spacing + offsetX
                val tileY = centerY - boxH / 2 + row * spacing

                canvas.save()
                canvas.rotate(-30f, tileX + boxW / 2, tileY + boxH / 2)

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    alpha = bgAlpha
                }
                canvas.drawRoundRect(
                    RectF(tileX, tileY, tileX + boxW, tileY + boxH), 4f, 4f, bgPaint
                )

                val tilePaint = Paint(textPaint).apply {
                    alpha = textAlpha
                    color = textColor
                    this.alpha = textAlpha
                }
                for ((i, line) in lines.withIndex()) {
                    val ly = tileY + padY + baseFontSize + i * (baseFontSize + lineGap)
                    canvas.drawText(line, tileX + padX, ly, tilePaint)
                }

                canvas.restore()
            }
        }
    }

    private fun calcOrigin(
        pos: WatermarkPosition,
        imgW: Float,
        imgH: Float,
        boxW: Float,
        boxH: Float,
        margin: Float,
    ): Pair<Float, Float> {
        val x = when (pos) {
            WatermarkPosition.TL, WatermarkPosition.BL -> margin
            WatermarkPosition.TC, WatermarkPosition.BC -> (imgW - boxW) / 2
            else -> imgW - boxW - margin
        }
        val y = when (pos) {
            WatermarkPosition.TL, WatermarkPosition.TC, WatermarkPosition.TR -> margin
            else -> imgH - boxH - margin
        }
        return Pair(x, y)
    }
}
