package com.kazahana.app.ui.compose

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kazahana.app.R
import com.kazahana.app.data.util.ImageHelper

enum class CropMode {
    ORIGINAL, SQUARE, FREE
}

/**
 * Which edge/corner of the crop rectangle is being dragged.
 */
private enum class DragHandle {
    NONE, TOP, BOTTOM, LEFT, RIGHT,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    MOVE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(
    imageUri: Uri,
    imageHelper: ImageHelper,
    onDone: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    // Decode bitmap once
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    LaunchedEffect(imageUri) {
        bitmap = imageHelper.decodeBitmap(imageUri)
    }

    val currentBitmap = remember(bitmap, rotationDegrees) {
        bitmap?.let { bmp ->
            if (rotationDegrees % 360 == 0) bmp
            else imageHelper.rotateBitmap(bmp, rotationDegrees % 360)
        }
    }

    var cropMode by remember { mutableStateOf(CropMode.FREE) }

    // Canvas size in pixels
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Image display rect (fitted inside canvas)
    var imgOffsetX by remember { mutableFloatStateOf(0f) }
    var imgOffsetY by remember { mutableFloatStateOf(0f) }
    var imgDisplayW by remember { mutableFloatStateOf(0f) }
    var imgDisplayH by remember { mutableFloatStateOf(0f) }

    // Crop rect in canvas coordinates (within the image display area)
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(0f) }
    var cropBottom by remember { mutableFloatStateOf(0f) }

    // Recalculate image display rect and reset crop when bitmap or canvas changes
    fun recalcLayout(bmp: Bitmap, cw: Float, ch: Float) {
        if (cw <= 0f || ch <= 0f) return
        val scaleX = cw / bmp.width
        val scaleY = ch / bmp.height
        val scale = minOf(scaleX, scaleY)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        imgOffsetX = (cw - dw) / 2f
        imgOffsetY = (ch - dh) / 2f
        imgDisplayW = dw
        imgDisplayH = dh
        // Reset crop with small inset so corner handles are grabbable
        val inset = minOf(dw, dh) * 0.04f  // 4% inset
        cropLeft = imgOffsetX + inset
        cropTop = imgOffsetY + inset
        cropRight = imgOffsetX + dw - inset
        cropBottom = imgOffsetY + dh - inset
    }

    // Apply crop mode constraints
    fun applyCropModeConstraint() {
        val bmp = currentBitmap ?: return
        when (cropMode) {
            CropMode.ORIGINAL -> {
                val aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()
                val currentW = cropRight - cropLeft
                val currentH = cropBottom - cropTop
                val centerX = (cropLeft + cropRight) / 2f
                val centerY = (cropTop + cropBottom) / 2f

                var newW: Float
                var newH: Float
                if (currentW / currentH > aspectRatio) {
                    newH = currentH
                    newW = newH * aspectRatio
                } else {
                    newW = currentW
                    newH = newW / aspectRatio
                }
                newW = newW.coerceAtMost(imgDisplayW)
                newH = newH.coerceAtMost(imgDisplayH)
                if (newW / newH > aspectRatio) {
                    newW = newH * aspectRatio
                } else {
                    newH = newW / aspectRatio
                }

                cropLeft = (centerX - newW / 2f).coerceAtLeast(imgOffsetX)
                cropTop = (centerY - newH / 2f).coerceAtLeast(imgOffsetY)
                cropRight = (cropLeft + newW).coerceAtMost(imgOffsetX + imgDisplayW)
                cropBottom = (cropTop + newH).coerceAtMost(imgOffsetY + imgDisplayH)
            }
            CropMode.SQUARE -> {
                val currentW = cropRight - cropLeft
                val currentH = cropBottom - cropTop
                val side = minOf(currentW, currentH, imgDisplayW, imgDisplayH)
                val centerX = (cropLeft + cropRight) / 2f
                val centerY = (cropTop + cropBottom) / 2f
                cropLeft = (centerX - side / 2f).coerceAtLeast(imgOffsetX)
                cropTop = (centerY - side / 2f).coerceAtLeast(imgOffsetY)
                cropRight = (cropLeft + side).coerceAtMost(imgOffsetX + imgDisplayW)
                cropBottom = (cropTop + side).coerceAtMost(imgOffsetY + imgDisplayH)
                val finalSide = minOf(cropRight - cropLeft, cropBottom - cropTop)
                cropRight = cropLeft + finalSide
                cropBottom = cropTop + finalSide
            }
            CropMode.FREE -> { /* no constraint */ }
        }
    }

    // When bitmap changes, recalc layout
    LaunchedEffect(currentBitmap, canvasWidth, canvasHeight) {
        currentBitmap?.let { bmp ->
            recalcLayout(bmp, canvasWidth, canvasHeight)
            applyCropModeConstraint()
        }
    }

    // When crop mode changes, apply constraint
    LaunchedEffect(cropMode) {
        applyCropModeConstraint()
    }

    val handleRadius = 24f
    val minCropSize = 48f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.image_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val bmp = currentBitmap ?: return@TextButton
                            // Convert canvas crop coordinates to bitmap coordinates
                            val scaleX = bmp.width.toFloat() / imgDisplayW
                            val scaleY = bmp.height.toFloat() / imgDisplayH
                            val bmpRect = Rect(
                                ((cropLeft - imgOffsetX) * scaleX).toInt(),
                                ((cropTop - imgOffsetY) * scaleY).toInt(),
                                ((cropRight - imgOffsetX) * scaleX).toInt(),
                                ((cropBottom - imgOffsetY) * scaleY).toInt(),
                            )
                            val cropped = imageHelper.cropBitmap(bmp, bmpRect)
                            val resultUri = imageHelper.saveBitmapToCache(cropped)
                            onDone(resultUri)
                        },
                    ) {
                        Text(stringResource(R.string.image_edit_done))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            // Image + crop overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(25.dp)
                    .onSizeChanged { size ->
                        canvasWidth = size.width.toFloat()
                        canvasHeight = size.height.toFloat()
                        currentBitmap?.let { bmp ->
                            recalcLayout(bmp, canvasWidth, canvasHeight)
                            applyCropModeConstraint()
                        }
                    },
            ) {
                val bmpSnapshot = currentBitmap
                if (bmpSnapshot != null) {
                    val imageBitmap = remember(bmpSnapshot) { bmpSnapshot.asImageBitmap() }
                    // Capture original aspect ratio for constraint use in drag
                    val originalAspect = bmpSnapshot.width.toFloat() / bmpSnapshot.height.toFloat()

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cropMode) {
                                var handle = DragHandle.NONE
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        handle = detectHandle(
                                            offset, cropLeft, cropTop, cropRight, cropBottom,
                                            handleRadius,
                                        )
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x
                                        val dy = dragAmount.y

                                        when (handle) {
                                            DragHandle.MOVE -> {
                                                val w = cropRight - cropLeft
                                                val h = cropBottom - cropTop
                                                var newLeft = cropLeft + dx
                                                var newTop = cropTop + dy
                                                newLeft = newLeft.coerceIn(imgOffsetX, imgOffsetX + imgDisplayW - w)
                                                newTop = newTop.coerceIn(imgOffsetY, imgOffsetY + imgDisplayH - h)
                                                cropLeft = newLeft
                                                cropTop = newTop
                                                cropRight = newLeft + w
                                                cropBottom = newTop + h
                                            }
                                            DragHandle.LEFT -> {
                                                cropLeft = (cropLeft + dx).coerceIn(imgOffsetX, cropRight - minCropSize)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = cropRight - cropLeft
                                                    val center = (cropTop + cropBottom) / 2f
                                                    cropTop = (center - side / 2f).coerceAtLeast(imgOffsetY)
                                                    cropBottom = (cropTop + side).coerceAtMost(imgOffsetY + imgDisplayH)
                                                    cropTop = cropBottom - side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val w = cropRight - cropLeft
                                                    val newH = w / originalAspect
                                                    val center = (cropTop + cropBottom) / 2f
                                                    cropTop = (center - newH / 2f).coerceAtLeast(imgOffsetY)
                                                    cropBottom = (cropTop + newH).coerceAtMost(imgOffsetY + imgDisplayH)
                                                }
                                            }
                                            DragHandle.RIGHT -> {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minCropSize, imgOffsetX + imgDisplayW)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = cropRight - cropLeft
                                                    val center = (cropTop + cropBottom) / 2f
                                                    cropTop = (center - side / 2f).coerceAtLeast(imgOffsetY)
                                                    cropBottom = (cropTop + side).coerceAtMost(imgOffsetY + imgDisplayH)
                                                    cropTop = cropBottom - side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val w = cropRight - cropLeft
                                                    val newH = w / originalAspect
                                                    val center = (cropTop + cropBottom) / 2f
                                                    cropTop = (center - newH / 2f).coerceAtLeast(imgOffsetY)
                                                    cropBottom = (cropTop + newH).coerceAtMost(imgOffsetY + imgDisplayH)
                                                }
                                            }
                                            DragHandle.TOP -> {
                                                cropTop = (cropTop + dy).coerceIn(imgOffsetY, cropBottom - minCropSize)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = cropBottom - cropTop
                                                    val center = (cropLeft + cropRight) / 2f
                                                    cropLeft = (center - side / 2f).coerceAtLeast(imgOffsetX)
                                                    cropRight = (cropLeft + side).coerceAtMost(imgOffsetX + imgDisplayW)
                                                    cropLeft = cropRight - side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val h = cropBottom - cropTop
                                                    val newW = h * originalAspect
                                                    val center = (cropLeft + cropRight) / 2f
                                                    cropLeft = (center - newW / 2f).coerceAtLeast(imgOffsetX)
                                                    cropRight = (cropLeft + newW).coerceAtMost(imgOffsetX + imgDisplayW)
                                                }
                                            }
                                            DragHandle.BOTTOM -> {
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minCropSize, imgOffsetY + imgDisplayH)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = cropBottom - cropTop
                                                    val center = (cropLeft + cropRight) / 2f
                                                    cropLeft = (center - side / 2f).coerceAtLeast(imgOffsetX)
                                                    cropRight = (cropLeft + side).coerceAtMost(imgOffsetX + imgDisplayW)
                                                    cropLeft = cropRight - side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val h = cropBottom - cropTop
                                                    val newW = h * originalAspect
                                                    val center = (cropLeft + cropRight) / 2f
                                                    cropLeft = (center - newW / 2f).coerceAtLeast(imgOffsetX)
                                                    cropRight = (cropLeft + newW).coerceAtMost(imgOffsetX + imgDisplayW)
                                                }
                                            }
                                            DragHandle.TOP_LEFT -> {
                                                cropLeft = (cropLeft + dx).coerceIn(imgOffsetX, cropRight - minCropSize)
                                                cropTop = (cropTop + dy).coerceIn(imgOffsetY, cropBottom - minCropSize)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = minOf(cropRight - cropLeft, cropBottom - cropTop)
                                                    cropLeft = cropRight - side
                                                    cropTop = cropBottom - side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val w = cropRight - cropLeft
                                                    cropTop = (cropBottom - w / originalAspect).coerceAtLeast(imgOffsetY)
                                                }
                                            }
                                            DragHandle.TOP_RIGHT -> {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minCropSize, imgOffsetX + imgDisplayW)
                                                cropTop = (cropTop + dy).coerceIn(imgOffsetY, cropBottom - minCropSize)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = minOf(cropRight - cropLeft, cropBottom - cropTop)
                                                    cropRight = cropLeft + side
                                                    cropTop = cropBottom - side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val w = cropRight - cropLeft
                                                    cropTop = (cropBottom - w / originalAspect).coerceAtLeast(imgOffsetY)
                                                }
                                            }
                                            DragHandle.BOTTOM_LEFT -> {
                                                cropLeft = (cropLeft + dx).coerceIn(imgOffsetX, cropRight - minCropSize)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minCropSize, imgOffsetY + imgDisplayH)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = minOf(cropRight - cropLeft, cropBottom - cropTop)
                                                    cropLeft = cropRight - side
                                                    cropBottom = cropTop + side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val w = cropRight - cropLeft
                                                    cropBottom = (cropTop + w / originalAspect).coerceAtMost(imgOffsetY + imgDisplayH)
                                                }
                                            }
                                            DragHandle.BOTTOM_RIGHT -> {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minCropSize, imgOffsetX + imgDisplayW)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minCropSize, imgOffsetY + imgDisplayH)
                                                if (cropMode == CropMode.SQUARE) {
                                                    val side = minOf(cropRight - cropLeft, cropBottom - cropTop)
                                                    cropRight = cropLeft + side
                                                    cropBottom = cropTop + side
                                                } else if (cropMode == CropMode.ORIGINAL) {
                                                    val w = cropRight - cropLeft
                                                    cropBottom = (cropTop + w / originalAspect).coerceAtMost(imgOffsetY + imgDisplayH)
                                                }
                                            }
                                            DragHandle.NONE -> {}
                                        }
                                    },
                                )
                            },
                    ) {
                        // Draw the image
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(imgOffsetX.toInt(), imgOffsetY.toInt()),
                            dstSize = IntSize(imgDisplayW.toInt(), imgDisplayH.toInt()),
                        )

                        // Draw dimmed overlay outside crop area
                        val dimColor = Color.Black.copy(alpha = 0.5f)

                        // Top strip
                        if (cropTop > imgOffsetY) {
                            drawRect(dimColor, Offset(imgOffsetX, imgOffsetY), Size(imgDisplayW, cropTop - imgOffsetY))
                        }
                        // Bottom strip
                        if (cropBottom < imgOffsetY + imgDisplayH) {
                            drawRect(dimColor, Offset(imgOffsetX, cropBottom), Size(imgDisplayW, imgOffsetY + imgDisplayH - cropBottom))
                        }
                        // Left strip (between top and bottom crop lines)
                        if (cropLeft > imgOffsetX) {
                            drawRect(dimColor, Offset(imgOffsetX, cropTop), Size(cropLeft - imgOffsetX, cropBottom - cropTop))
                        }
                        // Right strip
                        if (cropRight < imgOffsetX + imgDisplayW) {
                            drawRect(dimColor, Offset(cropRight, cropTop), Size(imgOffsetX + imgDisplayW - cropRight, cropBottom - cropTop))
                        }

                        // Draw crop border
                        val borderColor = Color.White
                        drawRect(
                            color = borderColor,
                            topLeft = Offset(cropLeft, cropTop),
                            size = Size(cropRight - cropLeft, cropBottom - cropTop),
                            style = Stroke(width = 2f),
                        )

                        // Draw rule-of-thirds grid lines
                        val gridColor = Color.White.copy(alpha = 0.3f)
                        val cw = cropRight - cropLeft
                        val ch = cropBottom - cropTop
                        for (i in 1..2) {
                            val x = cropLeft + cw * i / 3f
                            drawLine(gridColor, Offset(x, cropTop), Offset(x, cropBottom), strokeWidth = 1f)
                            val y = cropTop + ch * i / 3f
                            drawLine(gridColor, Offset(cropLeft, y), Offset(cropRight, y), strokeWidth = 1f)
                        }

                        // Draw corner handles
                        val cornerLen = 20f
                        val cornerStroke = 4f
                        drawCornerHandle(cropLeft, cropTop, cornerLen, cornerStroke, borderColor, topLeft = true)
                        drawCornerHandle(cropRight, cropTop, cornerLen, cornerStroke, borderColor, topRight = true)
                        drawCornerHandle(cropLeft, cropBottom, cornerLen, cornerStroke, borderColor, bottomLeft = true)
                        drawCornerHandle(cropRight, cropBottom, cornerLen, cornerStroke, borderColor, bottomRight = true)
                    }
                }
            }

            // Bottom bar: crop mode chips + rotate button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = cropMode == CropMode.ORIGINAL,
                        onClick = { cropMode = CropMode.ORIGINAL },
                        label = { Text(stringResource(R.string.image_edit_crop_original)) },
                    )
                    FilterChip(
                        selected = cropMode == CropMode.SQUARE,
                        onClick = { cropMode = CropMode.SQUARE },
                        label = { Text(stringResource(R.string.image_edit_crop_square)) },
                    )
                    FilterChip(
                        selected = cropMode == CropMode.FREE,
                        onClick = { cropMode = CropMode.FREE },
                        label = { Text(stringResource(R.string.image_edit_crop_free)) },
                    )
                }

                IconButton(onClick = {
                    rotationDegrees = (rotationDegrees + 90) % 360
                }) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = stringResource(R.string.image_edit_rotate),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun detectHandle(
    offset: Offset,
    cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
    radius: Float,
): DragHandle {
    val corners = listOf(
        DragHandle.TOP_LEFT to Offset(cropLeft, cropTop),
        DragHandle.TOP_RIGHT to Offset(cropRight, cropTop),
        DragHandle.BOTTOM_LEFT to Offset(cropLeft, cropBottom),
        DragHandle.BOTTOM_RIGHT to Offset(cropRight, cropBottom),
    )
    for ((handle, pos) in corners) {
        if (offset.distanceTo(pos) < radius * 1.5f) return handle
    }
    val edgeThreshold = radius
    if (offset.y in cropTop..cropBottom) {
        if (kotlin.math.abs(offset.x - cropLeft) < edgeThreshold) return DragHandle.LEFT
        if (kotlin.math.abs(offset.x - cropRight) < edgeThreshold) return DragHandle.RIGHT
    }
    if (offset.x in cropLeft..cropRight) {
        if (kotlin.math.abs(offset.y - cropTop) < edgeThreshold) return DragHandle.TOP
        if (kotlin.math.abs(offset.y - cropBottom) < edgeThreshold) return DragHandle.BOTTOM
    }
    if (offset.x in cropLeft..cropRight && offset.y in cropTop..cropBottom) {
        return DragHandle.MOVE
    }
    return DragHandle.NONE
}

private fun DrawScope.drawCornerHandle(
    x: Float, y: Float, len: Float, strokeWidth: Float, color: Color,
    topLeft: Boolean = false, topRight: Boolean = false,
    bottomLeft: Boolean = false, bottomRight: Boolean = false,
) {
    when {
        topLeft -> {
            drawLine(color, Offset(x, y), Offset(x + len, y), strokeWidth)
            drawLine(color, Offset(x, y), Offset(x, y + len), strokeWidth)
        }
        topRight -> {
            drawLine(color, Offset(x, y), Offset(x - len, y), strokeWidth)
            drawLine(color, Offset(x, y), Offset(x, y + len), strokeWidth)
        }
        bottomLeft -> {
            drawLine(color, Offset(x, y), Offset(x + len, y), strokeWidth)
            drawLine(color, Offset(x, y), Offset(x, y - len), strokeWidth)
        }
        bottomRight -> {
            drawLine(color, Offset(x, y), Offset(x - len, y), strokeWidth)
            drawLine(color, Offset(x, y), Offset(x, y - len), strokeWidth)
        }
    }
}
