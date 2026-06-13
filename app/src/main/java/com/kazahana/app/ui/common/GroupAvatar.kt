package com.kazahana.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aggregated avatar for a group conversation: up to 3 member avatars stacked
 * diagonally within [size]. Falls back to a generic group icon when no avatars
 * are available.
 */
@Composable
fun GroupAvatar(
    avatarUrls: List<String?>,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val urls = avatarUrls.filterNotNull().take(3)

    if (urls.isEmpty()) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
        return
    }

    val childSize = size * 0.62f
    Box(modifier = modifier.size(size)) {
        // Bottom-right first so the top-left avatar overlaps on top.
        if (urls.size >= 2) {
            AvatarImage(
                url = urls[1],
                size = childSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
        if (urls.size >= 3) {
            AvatarImage(
                url = urls[2],
                size = childSize * 0.85f,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
        AvatarImage(
            url = urls[0],
            size = childSize,
            modifier = Modifier
                .align(Alignment.TopStart)
                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}
