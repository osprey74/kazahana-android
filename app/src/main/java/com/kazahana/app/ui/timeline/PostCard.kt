package com.kazahana.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.kazahana.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kazahana.app.data.model.EmbedViewRecord
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.BotBadge
import com.kazahana.app.ui.common.isBotAccount
import com.kazahana.app.data.bsaf.BsafService
import com.kazahana.app.data.model.BsafDuplicateInfo
import com.kazahana.app.data.model.BsafParsedTags
import com.kazahana.app.ui.common.ModerationDecision
import com.kazahana.app.ui.common.relativeTime
import com.kazahana.app.data.AppJson
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.decodeFromJsonElement
private val RepostGreen = androidx.compose.ui.graphics.Color(0xFF00BA7C)
private val bsafService = BsafService()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCard(
    feedPost: FeedViewPost,
    onClick: (postUri: String) -> Unit = {},
    onAuthorClick: (did: String) -> Unit = {},
    onReply: (postUri: String, postCid: String) -> Unit = { _, _ -> },
    onLike: (postUri: String, postCid: String, currentLikeUri: String?) -> Unit = { _, _, _ -> },
    onRepost: (postUri: String, postCid: String, currentRepostUri: String?) -> Unit = { _, _, _ -> },
    onBookmark: (postUri: String, postCid: String, currentBookmarkUri: String?) -> Unit = { _, _, _ -> },
    moderationDecision: ModerationDecision = ModerationDecision(),
    bsafTags: BsafParsedTags? = null,
    bsafDuplicate: BsafDuplicateInfo? = null,
    modifier: Modifier = Modifier,
) {
    val post = feedPost.post
    val record = remember(post.record) {
        try {
            AppJson.decodeFromJsonElement<PostRecord>(post.record)
        } catch (_: Exception) {
            null
        }
    }

    val severityColor = remember(bsafTags) {
        bsafTags?.let { bsafService.severityBorderColor(it.value) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (severityColor != null) {
                    Modifier.drawBehind {
                        drawRect(
                            color = severityColor,
                            topLeft = Offset.Zero,
                            size = Size(4.dp.toPx(), size.height),
                        )
                    }
                } else Modifier
            ),
    ) {
        // Repost indicator
        if (feedPost.reason?.type?.contains("reasonRepost") == true) {
            Text(
                text = "⟳ ${feedPost.reason.by?.displayName ?: feedPost.reason.by?.handle ?: ""} reposted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 58.dp, top = 8.dp),
            )
        }

        // Clickable content area — text is always visible, moderation applies to media only
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(post.uri) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            AvatarImage(url = post.author.avatar, onClick = { onAuthorClick(post.author.did) })

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Author line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = post.author.displayName ?: post.author.handle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isBotAccount(post.author.did, post.author.labels)) {
                        Spacer(modifier = Modifier.width(3.dp))
                        BotBadge(size = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "@${post.author.handle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = relativeTime(post.indexedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                // Reply indicator
                if (feedPost.reply != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            text = stringResource(R.string.post_reply),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }

                // Langs & via row
                if (record != null && (record.langs.isNotEmpty() || record.via != null)) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (record.langs.isNotEmpty()) {
                            Text(
                                text = record.langs.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                        if (record.via != null) {
                            Text(
                                text = "via ${record.via}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                // Post text (always visible)
                if (record != null && record.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                // Image grid — moderation applied here
                // Handle both top-level images and recordWithMedia images
                val images = post.embed?.images ?: post.embed?.media?.images
                if (!images.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!moderationDecision.shouldHide) {
                        ImageGrid(
                            images = images,
                            moderationDecision = moderationDecision,
                        )
                    }
                    // shouldHide → images completely hidden
                }

                // Video player — moderation applied here too
                val videoUrl = post.embed?.playlist
                if (videoUrl != null && !moderationDecision.shouldHide) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!moderationDecision.shouldWarn) {
                        com.kazahana.app.ui.common.VideoPlayer(
                            hlsUrl = videoUrl,
                            thumbnailUrl = post.embed.thumbnail,
                            aspectRatio = post.embed.aspectRatio,
                        )
                    } else {
                        com.kazahana.app.ui.common.ModerationWarnOverlay(
                            decision = moderationDecision,
                        ) {
                            com.kazahana.app.ui.common.VideoPlayer(
                                hlsUrl = videoUrl,
                                thumbnailUrl = post.embed.thumbnail,
                                aspectRatio = post.embed.aspectRatio,
                            )
                        }
                    }
                    val videoAlt = post.embed?.alt
                    if (!videoAlt.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (videoAlt.length > 128) videoAlt.take(128) + "…" else videoAlt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Link card (no moderation needed)
                val external = post.embed?.external ?: post.embed?.media?.external
                if (external != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinkCard(external = external)
                }

                // Quote post (embedded record)
                val embeddedRecord = post.embed?.record
                if (embeddedRecord?.record != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    QuoteCard(
                        recordJson = embeddedRecord.record,
                        onClick = onClick,
                    )
                }

                // BSAF tag badges (iOS: monospaced 11pt, secondary, with Divider)
                if (bsafTags != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        buildList {
                            add("bsaf:${bsafTags.version}")
                            if (bsafTags.type.isNotEmpty()) add("type:${bsafTags.type}")
                            if (bsafTags.value.isNotEmpty()) add("value:${bsafTags.value}")
                            if (bsafTags.time.isNotEmpty()) add("time:${bsafTags.time}")
                            if (bsafTags.target.isNotEmpty()) add("target:${bsafTags.target}")
                            if (bsafTags.source.isNotEmpty()) add("source:${bsafTags.source}")
                        }.forEach { tag ->
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // BSAF duplicate indicator (iOS: 11pt, doc.on.doc icon + text)
                if (bsafDuplicate != null && bsafDuplicate.duplicateHandles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            text = stringResource(
                                R.string.bsaf_duplicate_indicator,
                                bsafDuplicate.duplicateHandles.size,
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        // Action bar
        ActionBar(
            replyCount = post.replyCount ?: 0,
            repostCount = post.repostCount ?: 0,
            likeCount = post.likeCount ?: 0,
            isLiked = post.viewer?.like != null,
            isReposted = post.viewer?.repost != null,
            isBookmarked = post.viewer?.bookmark != null,
            onReply = { onReply(post.uri, post.cid) },
            onLike = { onLike(post.uri, post.cid, post.viewer?.like) },
            onRepost = { onRepost(post.uri, post.cid, post.viewer?.repost) },
            onBookmark = { onBookmark(post.uri, post.cid, post.viewer?.bookmark) },
            modifier = Modifier.padding(start = 66.dp, end = 16.dp, bottom = 8.dp),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

@Composable
private fun QuoteCard(
    recordJson: kotlinx.serialization.json.JsonElement,
    onClick: (postUri: String) -> Unit,
) {
    val viewRecord = remember(recordJson) {
        try {
            AppJson.decodeFromJsonElement<EmbedViewRecord>(recordJson)
        } catch (_: Exception) {
            null
        }
    }
    val quotedRecord = remember(viewRecord?.value) {
        viewRecord?.value?.let {
            try {
                AppJson.decodeFromJsonElement<PostRecord>(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    if (viewRecord == null || viewRecord.author == null) return

    val quotedImages = viewRecord.embeds.firstNotNullOfOrNull { it.images }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick(viewRecord.uri) }
            .padding(10.dp),
    ) {
        // Author line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (viewRecord.author.avatar != null) {
                AvatarImage(url = viewRecord.author.avatar, size = 16.dp)
            }
            Text(
                text = viewRecord.author.displayName ?: viewRecord.author.handle,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "@${viewRecord.author.handle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Quoted text
        if (quotedRecord != null && quotedRecord.text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = quotedRecord.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Quoted images (compact thumbnails)
        if (!quotedImages.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            ImageGrid(images = quotedImages)
        }
    }
}

@Composable
private fun ActionBar(
    replyCount: Int,
    repostCount: Int,
    likeCount: Int,
    isLiked: Boolean,
    isReposted: Boolean,
    isBookmarked: Boolean,
    onReply: () -> Unit,
    onLike: () -> Unit,
    onRepost: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        ActionItem(
            icon = Icons.Outlined.ChatBubbleOutline,
            activeIcon = Icons.Outlined.ChatBubbleOutline,
            count = replyCount,
            isActive = false,
            onClick = onReply,
        )
        ActionItem(
            icon = Icons.Outlined.Repeat,
            activeIcon = Icons.Filled.Repeat,
            count = repostCount,
            isActive = isReposted,
            activeColor = RepostGreen,
            onClick = onRepost,
        )
        ActionItem(
            icon = Icons.Outlined.FavoriteBorder,
            activeIcon = Icons.Filled.Favorite,
            count = likeCount,
            isActive = isLiked,
            activeColor = MaterialTheme.colorScheme.error,
            onClick = onLike,
        )
        ActionItem(
            icon = Icons.Outlined.BookmarkBorder,
            activeIcon = Icons.Filled.Bookmark,
            count = 0,
            isActive = isBookmarked,
            activeColor = MaterialTheme.colorScheme.primary,
            onClick = onBookmark,
        )
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
    ) {
        Icon(
            imageVector = if (isActive) activeIcon else icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = tint,
            )
        }
    }
}
