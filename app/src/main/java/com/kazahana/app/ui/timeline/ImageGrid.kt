package com.kazahana.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kazahana.app.R
import com.kazahana.app.data.model.ImageView
import com.kazahana.app.ui.common.FullscreenImageViewer
import com.kazahana.app.ui.common.ModerationDecision

private const val ALT_MAX_CHARS = 128

private fun truncateAlt(alt: String): String {
    return if (alt.length > ALT_MAX_CHARS) alt.take(ALT_MAX_CHARS) + "…" else alt
}

@Composable
fun ImageGrid(
    images: List<ImageView>,
    moderationDecision: ModerationDecision = ModerationDecision(),
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var mediaRevealed by remember { mutableStateOf(false) }
    val shouldBlur = moderationDecision.shouldWarn && !mediaRevealed

    // Single shared pager state — carousel path uses it; single-image path ignores it.
    val pagerState = rememberPagerState(pageCount = { images.size })

    viewerIndex?.let { index ->
        FullscreenImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { viewerIndex = null },
            showHideButton = moderationDecision.shouldWarn,
            onHide = {
                viewerIndex = null
                mediaRevealed = false
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val blurModifier = if (shouldBlur) Modifier.blur(24.dp) else Modifier
            Box(modifier = blurModifier) {
                if (images.size == 1) {
                    SingleImage(
                        image = images[0],
                        shape = shape,
                        enabled = !shouldBlur,
                        onClick = { viewerIndex = 0 },
                    )
                } else {
                    ImageCarousel(
                        images = images,
                        pagerState = pagerState,
                        shape = shape,
                        enabled = !shouldBlur,
                        onImageClick = { viewerIndex = it },
                    )
                }
            }

            // Warning overlay on top of blurred images
            if (shouldBlur) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .clickable { mediaRevealed = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.moderation_content_warning),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            if (moderationDecision.shouldWarn && mediaRevealed) {
                TextButton(
                    onClick = { mediaRevealed = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.moderation_hide),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // Page indicator — below the (possibly blurred) image area, always crisp.
        if (images.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            PageIndicator(pageCount = images.size, currentPage = pagerState.currentPage)
        }

        // ALT text below the image grid
        val altsWithIndex = images.mapIndexedNotNull { i, img ->
            if (img.alt.isNotBlank()) Pair(i + 1, truncateAlt(img.alt)) else null
        }
        if (altsWithIndex.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            if (images.size == 1) {
                Text(
                    text = altsWithIndex[0].second,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    altsWithIndex.forEach { (index, alt) ->
                        Text(
                            text = "[${stringResource(R.string.alt_image_prefix)}$index] $alt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleImage(
    image: ImageView,
    shape: Shape,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ratio = image.aspectRatio?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    AsyncImage(
        model = image.thumb,
        contentDescription = image.alt.ifEmpty { null },
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.5f, 3f))
            .clip(shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun ImageCarousel(
    images: List<ImageView>,
    pagerState: PagerState,
    shape: Shape,
    enabled: Boolean,
    onImageClick: (Int) -> Unit,
) {
    // Container aspect ratio derived from the first image, narrower clamp than single-image
    // so mixed orientations don't produce extreme layouts. Images below use Fit scaling so
    // nothing gets cropped; letterboxed area uses a neutral tint.
    val firstRatio = images[0].aspectRatio?.let { it.width.toFloat() / it.height } ?: 1f
    val containerRatio = firstRatio.coerceIn(0.75f, 2f)

    HorizontalPager(
        state = pagerState,
        pageSpacing = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(containerRatio),
    ) { page ->
        val img = images[page]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        ) {
            AsyncImage(
                model = img.thumb,
                contentDescription = img.alt.ifEmpty { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (enabled) Modifier.clickable { onImageClick(page) } else Modifier),
            )
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (selected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) activeColor else inactiveColor),
            )
        }
    }
}
