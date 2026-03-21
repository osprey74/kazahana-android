package com.kazahana.app.ui.timeline

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
import androidx.compose.material3.IconButton
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
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.relativeTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

private val json = Json { ignoreUnknownKeys = true }
private val RepostGreen = androidx.compose.ui.graphics.Color(0xFF00BA7C)

@Composable
fun PostCard(
    feedPost: FeedViewPost,
    onClick: (postUri: String) -> Unit = {},
    onReply: (postUri: String, postCid: String) -> Unit = { _, _ -> },
    onLike: (postUri: String, postCid: String, currentLikeUri: String?) -> Unit = { _, _, _ -> },
    onRepost: (postUri: String, postCid: String, currentRepostUri: String?) -> Unit = { _, _, _ -> },
    onBookmark: (postUri: String, postCid: String, currentBookmarkUri: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val post = feedPost.post
    val record = remember(post.record) {
        try {
            json.decodeFromJsonElement<PostRecord>(post.record)
        } catch (_: Exception) {
            null
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Repost indicator
        if (feedPost.reason?.type?.contains("reasonRepost") == true) {
            Text(
                text = "⟳ ${feedPost.reason.by?.displayName ?: feedPost.reason.by?.handle ?: ""} reposted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 58.dp, top = 8.dp),
            )
        }

        // Clickable content area (navigates to thread) — excludes action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(post.uri) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            AvatarImage(url = post.author.avatar)

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

                // Post text
                if (record != null && record.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                // Image grid (thumbnails)
                val images = post.embed?.images
                if (!images.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ImageGrid(images = images)
                }

                // Video player
                val videoUrl = post.embed?.playlist
                if (videoUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.kazahana.app.ui.common.VideoPlayer(
                        hlsUrl = videoUrl,
                        thumbnailUrl = post.embed.thumbnail,
                        aspectRatio = post.embed.aspectRatio,
                    )
                }

                // Link card
                val external = post.embed?.external
                if (external != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinkCard(external = external)
                }
            }
        }

        // Action bar — outside the clickable area so taps work independently
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
