package com.kazahana.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.kazahana.app.R
import com.kazahana.app.data.model.NotificationItem
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.data.model.PostView
import com.kazahana.app.data.model.ProfileViewBasic
import androidx.compose.ui.unit.sp
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.BotBadge
import com.kazahana.app.ui.common.isBotAccount
import com.kazahana.app.ui.common.relativeTime
import com.kazahana.app.ui.common.RichTextContent
import kotlinx.coroutines.flow.SharedFlow
import com.kazahana.app.data.AppJson
import kotlinx.serialization.json.decodeFromJsonElement
private val RepostGreen = Color(0xFF00BA7C)
private val LikeRed = Color(0xFFE0245E)
private val FollowBlue = Color(0xFF1DA1F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    retapFlow: SharedFlow<Unit>? = null,
    viewModel: NotificationViewModel = hiltViewModel(),
    onPostClick: (postUri: String) -> Unit = {},
    onProfileClick: (did: String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.ensureLoaded()
    }

    LaunchedEffect(retapFlow) {
        retapFlow?.collect {
            viewModel.refresh()
            listState.animateScrollToItem(0)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5 && !uiState.isLoadingMore && uiState.hasMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            uiState.isLoading && uiState.notifications.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.notifications.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.groupedNotifications.isEmpty() && uiState.notifications.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notification_empty), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = uiState.groupedNotifications,
                        key = { "${it.uri}_${it.reason}" },
                    ) { group ->
                        val subjectPostUri = when (group.reason) {
                            "like", "repost" -> group.reasonSubject
                            "like-via-repost", "repost-via-repost" ->
                                group.reasonSubject?.let { uiState.resolvedRepostURIs[it] }
                            "reply", "mention", "quote" -> group.uri
                            else -> null
                        }
                        val subjectPost = subjectPostUri?.let { uiState.subjectPosts[it] }

                        GroupedNotificationRow(
                            group = group,
                            subjectPost = subjectPost,
                            onClick = {
                                when (group.reason) {
                                    "follow" -> onProfileClick(group.authors.first().did)
                                    "like", "repost" -> group.reasonSubject?.let { onPostClick(it) }
                                    "like-via-repost", "repost-via-repost" ->
                                        subjectPostUri?.let { onPostClick(it) }
                                    else -> onPostClick(group.uri)
                                }
                            },
                            onAvatarClick = { did -> onProfileClick(did) },
                            onPostClick = { uri -> onPostClick(uri) },
                            onPostAuthorClick = { did -> onProfileClick(did) },
                            onLike = { uri, cid, likeUri -> viewModel.toggleLike(uri, cid, likeUri) },
                            onRepost = { uri, cid, repostUri -> viewModel.toggleRepost(uri, cid, repostUri) },
                            onBookmark = { uri, cid, bookmarkUri -> viewModel.toggleBookmark(uri, cid, bookmarkUri) },
                            onHashtagClick = onHashtagClick,
                            onMentionClick = onMentionClick,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedNotificationRow(
    group: NotificationGroup,
    subjectPost: PostView?,
    onClick: () -> Unit,
    onAvatarClick: (did: String) -> Unit = {},
    onPostClick: (postUri: String) -> Unit = {},
    onPostAuthorClick: (did: String) -> Unit = {},
    onLike: (postUri: String, postCid: String, currentLikeUri: String?) -> Unit = { _, _, _ -> },
    onRepost: (postUri: String, postCid: String, currentRepostUri: String?) -> Unit = { _, _, _ -> },
    onBookmark: (postUri: String, postCid: String, currentBookmarkUri: String?) -> Unit = { _, _, _ -> },
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
) {
    val (icon, iconColor) = remember(group.reason) {
        when (group.reason) {
            "like", "like-via-repost" -> Pair(Icons.Filled.Favorite, LikeRed)
            "repost", "repost-via-repost" -> Pair(Icons.Filled.Repeat, RepostGreen)
            "follow" -> Pair(Icons.Filled.PersonAdd, FollowBlue)
            "mention" -> Pair(Icons.Outlined.AlternateEmail, FollowBlue)
            "reply" -> Pair(Icons.Filled.Reply, Color.Gray)
            "quote" -> Pair(Icons.Filled.FormatQuote, Color.Gray)
            else -> Pair(Icons.Filled.Favorite, Color.Gray)
        }
    }
    val firstAuthor = group.authors.first()
    val othersCount = group.authors.size - 1

    val label = when (group.reason) {
        "like" -> stringResource(R.string.notification_liked)
        "like-via-repost" -> stringResource(R.string.notification_liked_repost)
        "repost" -> stringResource(R.string.notification_reposted)
        "repost-via-repost" -> stringResource(R.string.notification_reposted_repost)
        "follow" -> stringResource(R.string.notification_followed)
        "mention" -> stringResource(R.string.notification_mentioned)
        "reply" -> stringResource(R.string.notification_replied)
        "quote" -> stringResource(R.string.notification_quoted)
        else -> group.reason
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Notification header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Multiple avatars row for grouped notifications
                if (group.authors.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-4).dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        group.authors.take(5).forEach { author ->
                            AvatarImage(
                                url = author.avatar,
                                size = 28.dp,
                                onClick = { onAvatarClick(author.did) },
                            )
                        }
                        if (group.authors.size > 5) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        androidx.compose.foundation.shape.CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+${group.authors.size - 5}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (group.authors.size == 1) {
                        AvatarImage(url = firstAuthor.avatar, size = 32.dp, onClick = { onAvatarClick(firstAuthor.did) })
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (othersCount > 0) {
                                    stringResource(
                                        R.string.notification_group_label,
                                        firstAuthor.displayName ?: firstAuthor.handle,
                                        othersCount,
                                    )
                                } else {
                                    firstAuthor.displayName ?: firstAuthor.handle
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (!group.isRead) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (group.authors.size == 1 && isBotAccount(firstAuthor.did, firstAuthor.labels)) {
                                Spacer(modifier = Modifier.width(3.dp))
                                BotBadge(size = 13.sp)
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        text = relativeTime(group.indexedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                // Inline post content (text + media thumbnails)
                if (group.reason != "follow") {
                    NotificationPostContent(
                        subjectPost = subjectPost,
                        fallbackRecord = group.record,
                        reason = group.reason,
                        onHashtagClick = onHashtagClick,
                        onMentionClick = onMentionClick,
                    )
                }
            }
        }
    }
}

/** Compact inline post content for notifications: full text + square thumbnails + video capture. */
@Composable
private fun NotificationPostContent(
    subjectPost: PostView?,
    fallbackRecord: kotlinx.serialization.json.JsonElement,
    reason: String,
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
) {
    // Determine text source: fetched post record, or fallback from notification record
    val postRecord = remember(subjectPost?.record) {
        subjectPost?.record?.let {
            try { AppJson.decodeFromJsonElement<PostRecord>(it) } catch (_: Exception) { null }
        }
    }
    val fallbackPostRecord = remember(fallbackRecord) {
        if (reason in listOf("reply", "mention", "quote")) {
            try { AppJson.decodeFromJsonElement<PostRecord>(fallbackRecord) } catch (_: Exception) { null }
        } else null
    }
    val record = postRecord ?: fallbackPostRecord

    val postText = record?.text
    val images = subjectPost?.embed?.images ?: subjectPost?.embed?.media?.images
    val videoThumbnail = subjectPost?.embed?.thumbnail

    if (postText.isNullOrEmpty() && images.isNullOrEmpty() && videoThumbnail == null) return

    Spacer(modifier = Modifier.height(6.dp))

    // Post text (full)
    if (!postText.isNullOrEmpty()) {
        val facets = record?.facets?.ifEmpty { null }
        if (facets != null) {
            RichTextContent(
                text = postText,
                facets = facets,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                onHashtagClick = onHashtagClick,
                onMentionClick = onMentionClick,
            )
        } else {
            Text(
                text = postText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }

    // Square image thumbnails
    if (!images.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            images.forEach { image ->
                AsyncImage(
                    model = image.thumb,
                    contentDescription = image.alt.ifEmpty { null },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }

    // Video thumbnail (square with play indicator)
    if (videoThumbnail != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = videoThumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Play icon overlay
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(2.dp),
            )
        }
    }
}
