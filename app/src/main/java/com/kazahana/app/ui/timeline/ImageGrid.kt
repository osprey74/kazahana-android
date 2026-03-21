package com.kazahana.app.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kazahana.app.data.model.ImageView
import com.kazahana.app.ui.common.FullscreenImageViewer

@Composable
fun ImageGrid(
    images: List<ImageView>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    // Fullscreen viewer
    viewerIndex?.let { index ->
        FullscreenImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { viewerIndex = null },
        )
    }

    when (images.size) {
        1 -> {
            val img = images[0]
            val ratio = img.aspectRatio?.let { it.width.toFloat() / it.height }
                ?: (16f / 9f)
            AsyncImage(
                model = img.thumb,
                contentDescription = img.alt.ifEmpty { null },
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio.coerceIn(0.5f, 3f))
                    .clip(shape)
                    .clickable { viewerIndex = 0 },
            )
        }

        2 -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier.fillMaxWidth(),
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
                            .clickable { viewerIndex = index },
                    )
                }
            }
        }

        else -> {
            // 3-4 images: 2-column grid
            val rows = images.chunked(2)
            var globalIndex = 0
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier.fillMaxWidth(),
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
                                    .clickable { viewerIndex = idx },
                            )
                        }
                        // Pad empty space if odd number
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
