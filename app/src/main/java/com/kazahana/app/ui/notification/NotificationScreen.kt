package com.kazahana.app.ui.notification

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.NotificationItem
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.data.model.PostView
import androidx.compose.ui.unit.sp
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.BotBadge
import com.kazahana.app.ui.common.isBotAccount
import com.kazahana.app.ui.common.LocalModerationSettings
import com.kazahana.app.ui.common.checkModeration
import com.kazahana.app.ui.common.relativeTime
import com.kazahana.app.ui.timeline.PostCard
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
            uiState.notifications.isEmpty() -> {
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
                        items = uiState.notifications,
                        key = { "${it.uri}_${it.reason}" },
                    ) { notification ->
                        val subjectPostUri = when (notification.reason) {
                            "like", "repost" -> notification.reasonSubject
                            "like-via-repost", "repost-via-repost" ->
                                notification.reasonSubject?.let { uiState.resolvedRepostURIs[it] }
                            "reply", "mention", "quote" -> notification.uri
                            else -> null
                        }
                        val subjectPost = subjectPostUri?.let { uiState.subjectPosts[it] }

                        NotificationRow(
                            notification = notification,
                            subjectPost = subjectPost,
                            onClick = {
                                when (notification.reason) {
                                    "follow" -> onProfileClick(notification.author.did)
                                    "like", "repost" -> notification.reasonSubject?.let { onPostClick(it) }
                                    "like-via-repost", "repost-via-repost" ->
                                        subjectPostUri?.let { onPostClick(it) }
                                    else -> onPostClick(notification.uri)
                                }
                            },
                            onAvatarClick = { onProfileClick(notification.author.did) },
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
private fun NotificationRow(
    notification: NotificationItem,
    subjectPost: PostView?,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onPostClick: (postUri: String) -> Unit = {},
    onPostAuthorClick: (did: String) -> Unit = {},
    onLike: (postUri: String, postCid: String, currentLikeUri: String?) -> Unit = { _, _, _ -> },
    onRepost: (postUri: String, postCid: String, currentRepostUri: String?) -> Unit = { _, _, _ -> },
    onBookmark: (postUri: String, postCid: String, currentBookmarkUri: String?) -> Unit = { _, _, _ -> },
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
) {
    val (icon, iconColor) = remember(notification.reason) {
        when (notification.reason) {
            "like", "like-via-repost" -> Pair(Icons.Filled.Favorite, LikeRed)
            "repost", "repost-via-repost" -> Pair(Icons.Filled.Repeat, RepostGreen)
            "follow" -> Pair(Icons.Filled.PersonAdd, FollowBlue)
            "mention" -> Pair(Icons.Outlined.AlternateEmail, FollowBlue)
            "reply" -> Pair(Icons.Filled.Reply, Color.Gray)
            "quote" -> Pair(Icons.Filled.FormatQuote, Color.Gray)
            else -> Pair(Icons.Filled.Favorite, Color.Gray)
        }
    }
    val label = when (notification.reason) {
        "like" -> stringResource(R.string.notification_liked)
        "like-via-repost" -> stringResource(R.string.notification_liked_repost)
        "repost" -> stringResource(R.string.notification_reposted)
        "repost-via-repost" -> stringResource(R.string.notification_reposted_repost)
        "follow" -> stringResource(R.string.notification_followed)
        "mention" -> stringResource(R.string.notification_mentioned)
        "reply" -> stringResource(R.string.notification_replied)
        "quote" -> stringResource(R.string.notification_quoted)
        else -> notification.reason
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AvatarImage(url = notification.author.avatar, size = 32.dp, onClick = onAvatarClick)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = notification.author.displayName ?: notification.author.handle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isBotAccount(notification.author.did, notification.author.labels)) {
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
                        text = relativeTime(notification.indexedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                // For reply/mention/quote without a fetched subject post, show inline record text
                if (subjectPost == null && notification.reason in listOf("reply", "mention", "quote")) {
                    val recordText = remember(notification.record) {
                        try {
                            AppJson.decodeFromJsonElement<PostRecord>(notification.record).text
                        } catch (_: Exception) { null }
                    }
                    if (recordText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recordText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        // Subject post card (for like/repost: the liked/reposted post; for reply/mention/quote: the notification post itself)
        if (subjectPost != null && notification.reason != "follow") {
            val feedPost = remember(subjectPost) { FeedViewPost(post = subjectPost) }
            val modSettings = LocalModerationSettings.current
            val modDecision = remember(subjectPost.labels, modSettings) {
                checkModeration(subjectPost.labels, modSettings)
            }
            PostCard(
                feedPost = feedPost,
                onClick = { uri -> onPostClick(uri) },
                onAuthorClick = { did -> onPostAuthorClick(did) },
                onReply = { _, _ -> },
                onLike = onLike,
                onRepost = onRepost,
                onBookmark = onBookmark,
                modifier = Modifier.padding(start = 32.dp),
                moderationDecision = modDecision,
                onHashtagClick = onHashtagClick,
                onMentionClick = onMentionClick,
            )
        }
    }
}
