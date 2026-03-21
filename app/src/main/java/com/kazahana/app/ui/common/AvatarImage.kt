package com.kazahana.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun AvatarImage(
    url: String?,
    size: Dp = 42.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = url,
        contentDescription = "Avatar",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.LightGray, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}
