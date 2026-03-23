package com.kazahana.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.kazahana.app.R
import com.kazahana.app.data.model.ImageView

@Composable
fun FullscreenImageViewer(
    images: List<ImageView>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    showHideButton: Boolean = false,
    onHide: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler { onDismiss() }

        // Get system bar heights from Android resources (Compose WindowInsets
        // are unavailable inside Dialog)
        val context = LocalContext.current
        val density = LocalDensity.current
        val statusBarHeight = remember(density) {
            val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) with(density) { context.resources.getDimensionPixelSize(id).toDp() } else 24.dp
        }
        val navBarHeight = remember(density) {
            val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) with(density) { context.resources.getDimensionPixelSize(id).toDp() } else 48.dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(top = statusBarHeight, bottom = navBarHeight),
        ) {
            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { images.size },
            )
            val currentAlt = images.getOrNull(pagerState.currentPage)?.alt

            // Column splits dialog into: top spacer (5%) / image (80%) / ALT (15%)
            Column(modifier = Modifier.fillMaxSize()) {
                // Top spacer (5%)
                Spacer(modifier = Modifier.weight(0.05f))

                // Image area (80%)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.80f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    val imgW = maxWidth
                    val imgH = maxHeight
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ZoomableImage(
                                imageUrl = images[page].fullsize,
                                contentDescription = images[page].alt.ifEmpty { null },
                                onTap = onDismiss,
                                imageWidth = imgW,
                                imageHeight = imgH,
                            )
                        }
                    }
                }

                // ALT text + page indicator (15%)
                Column(
                    modifier = Modifier
                        .weight(0.15f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!currentAlt.isNullOrBlank()) {
                        Text(
                            text = currentAlt,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }

                    if (images.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${images.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            // Close button (overlaid at top-right)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            // Hide button (overlaid at top-left)
            if (showHideButton && onHide != null) {
                TextButton(
                    onClick = onHide,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp),
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.moderation_hide),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    onTap: () -> Unit,
    imageWidth: Dp,
    imageHeight: Dp,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun clampOffsets(viewWidth: Int, viewHeight: Int) {
        val maxX = (viewWidth * (scale - 1f)) / 2f
        val maxY = (viewHeight * (scale - 1f)) / 2f
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    Box(
        modifier = Modifier
            .size(imageWidth, imageHeight)
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (event.changes.size >= 2) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                                clampOffsets(size.width, size.height)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                            clampOffsets(size.width, size.height)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = { onTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(imageWidth, imageHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}
