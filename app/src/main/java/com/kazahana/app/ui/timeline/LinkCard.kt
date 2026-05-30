package com.kazahana.app.ui.timeline

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kazahana.app.R
import com.kazahana.app.data.model.ColorRGB
import com.kazahana.app.data.model.ExternalSource
import com.kazahana.app.data.model.ExternalView
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun LinkCard(
    external: ExternalView,
    modifier: Modifier = Modifier,
    hideSubscribe: Boolean = false,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val source = external.source

    // Pattern detection (Standard Site, official social-app parity):
    //  - publication-only: a publication URL was shared (no document metadata)
    //  - document-only: createdAt/readingTime present, no source
    //  - document + publication: both present
    val isPublicationOnly = source != null &&
        external.createdAt == null && external.readingTime == null
    val authorHandle = external.associatedProfiles.firstOrNull()?.handle

    fun open(uri: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .clickable { open(external.uri) },
    ) {
        if (isPublicationOnly && source != null) {
            // Pattern 3: publication-only card
            PublicationSection(
                source = source,
                authorHandle = authorHandle,
                hideSubscribe = hideSubscribe,
                onClick = { open(source.uri) },
            )
        } else {
            // Pattern 1 / 2: document card (thumb + title + description + meta)
            external.thumb?.let { thumbUrl ->
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = external.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (external.description.isNotEmpty()) {
                    Text(
                        text = external.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Standard Site metadata row: host (left) + published date / reading time (right)
                val publishedDate = external.createdAt?.let { formatPublishedDate(it) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = external.uri.removePrefix("https://").removePrefix("http://").take(40),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (publishedDate != null) {
                        Text(
                            text = publishedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                        )
                    }
                    external.readingTime?.let { minutes ->
                        if (publishedDate != null) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${minutes}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                        )
                    }
                }
            }

            // Pattern 2: divider + publication section
            if (source != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                PublicationSection(
                    source = source,
                    authorHandle = authorHandle,
                    hideSubscribe = hideSubscribe,
                    onClick = { open(source.uri) },
                )
            }
        }
    }
}

@Composable
private fun PublicationSection(
    source: ExternalSource,
    authorHandle: String?,
    hideSubscribe: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        source.icon?.let { iconUrl ->
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val desc = source.description
            if (!desc.isNullOrEmpty()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (authorHandle != null) {
                Text(
                    text = stringResource(R.string.link_card_author, authorHandle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!hideSubscribe) {
            Spacer(modifier = Modifier.width(8.dp))
            // accent-only theming (background/foreground intentionally not applied — dark-mode safety)
            val bg = source.theme?.accentRGB.toColor() ?: MaterialTheme.colorScheme.primary
            val fg = source.theme?.accentForegroundRGB.toColor() ?: MaterialTheme.colorScheme.onPrimary
            Text(
                text = stringResource(R.string.link_card_view_publication),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun ColorRGB?.toColor(): Color? =
    this?.let { Color(red = it.r, green = it.g, blue = it.b) }

private fun formatPublishedDate(isoString: String): String? = try {
    val instant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(isoString))
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(instant.atZone(ZoneId.systemDefault()))
} catch (_: Exception) {
    null
}
