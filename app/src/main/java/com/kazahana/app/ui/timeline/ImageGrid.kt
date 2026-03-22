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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kazahana.app.R
import com.kazahana.app.data.model.ImageView
import com.kazahana.app.ui.common.FullscreenImageViewer
import com.kazahana.app.ui.common.ModerationDecision

@Composable
fun ImageGrid(
    images: List<ImageView>,
    moderationDecision: ModerationDecision = ModerationDecision(),
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var mediaRevealed by remember { mutableStateOf(false) }

    val shouldBlur = moderationDecision.shouldWarn && !mediaRevealed

    // Fullscreen viewer with hide button
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

    Box(modifier = modifier.fillMaxWidth()) {
        // Image content (blurred when moderated)
        val blurModifier = if (shouldBlur) Modifier.blur(24.dp) else Modifier

        Box(modifier = blurModifier) {
            when (images.size) {
                1 -> {
                    val img = images[0]
                    val ratio = img.aspectRatio?.let { it.width.toFloat() / it.height }
                        ?: (16f / 9f)
                    AsyncImage(
                        model = img.thumb,
                        contentDescription = img.alt.ifEmpty { null },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio.coerceIn(0.5f, 3f))
                            .clip(shape)
                            .then(
                                if (!shouldBlur) Modifier.clickable { viewerIndex = 0 }
                                else Modifier
                            ),
                    )
                }

                2 -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        images.forEachIndexed { index, img ->
                            AsyncImage(
                                model = img.thumb,
                                contentDescription = img.alt.ifEmpty { null },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(200.dp)
                                    .clip(shape)
                                    .then(
                                        if (!shouldBlur) Modifier.clickable { viewerIndex = index }
                                        else Modifier
                                    ),
                            )
                        }
                    }
                }

                else -> {
                    val rows = images.chunked(2)
                    var globalIndex = 0
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                row.forEach { img ->
                                    val idx = globalIndex++
                                    AsyncImage(
                                        model = img.thumb,
                                        contentDescription = img.alt.ifEmpty { null },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(140.dp)
                                            .clip(shape)
                                            .then(
                                                if (!shouldBlur) Modifier.clickable { viewerIndex = idx }
                                                else Modifier
                                            ),
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
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

        // "Hide again" button when revealed
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
}
