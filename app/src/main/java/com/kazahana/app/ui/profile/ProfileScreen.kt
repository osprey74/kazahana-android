package com.kazahana.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import coil3.compose.AsyncImage
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.LocalModerationSettings
import com.kazahana.app.ui.common.checkModeration
import com.kazahana.app.ui.timeline.PostCard
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    retapFlow: SharedFlow<Unit>? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    onPostClick: (postUri: String) -> Unit = {},
    onProfileClick: (did: String) -> Unit = {},
    onReply: (postUri: String, postCid: String, rootUri: String, rootCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onQuote: (postUri: String, postCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _ -> },
    onSettingsClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

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

    LaunchedEffect(retapFlow) {
        retapFlow?.collect {
            viewModel.loadProfile()
            listState.animateScrollToItem(0)
        }
    }

    when {
        uiState.isLoading && uiState.profile == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null && uiState.profile == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            val profile = uiState.profile ?: return

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Banner + Avatar + Profile info
                item {
                    ProfileHeader(
                        profile = profile,
                        isSelf = viewModel.isSelf,
                        isFollowLoading = uiState.isFollowLoading,
                        onFollowToggle = { viewModel.toggleFollow() },
                        onNavigateBack = onNavigateBack,
                        onSettingsClick = if (viewModel.isSelf) onSettingsClick else null,
                    )
                }

                // Tabs
                item {
                    ScrollableTabRow(
                        selectedTabIndex = ProfileTab.entries.indexOf(uiState.selectedTab),
                        edgePadding = 0.dp,
                    ) {
                        ProfileTab.entries.forEach { tab ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                text = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }

                // Loading posts
                if (uiState.isLoadingPosts && uiState.posts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                // Posts
                itemsIndexed(
                    items = uiState.posts,
                    key = { index, feedPost -> "${feedPost.post.uri}#$index" },
                ) { _, feedPost ->
                    val record = remember(feedPost.post.record) {
                        try {
                            Json { ignoreUnknownKeys = true }
                                .decodeFromJsonElement<PostRecord>(feedPost.post.record)
                        } catch (_: Exception) { null }
                    }
                    val modSettings = LocalModerationSettings.current
                    val modDecision = remember(feedPost.post.labels, modSettings) {
                        checkModeration(feedPost.post.labels, modSettings)
                    }
                    PostCard(
                        feedPost = feedPost,
                        onClick = { uri -> onPostClick(uri) },
                        onAuthorClick = { did -> onProfileClick(did) },
                        onReply = { uri, cid ->
                            val replyRoot = feedPost.reply?.root
                            val rootUri = replyRoot?.uri ?: uri
                            val rootCid = replyRoot?.cid ?: cid
                            onReply(
                                uri, cid, rootUri, rootCid,
                                feedPost.post.author.handle,
                                feedPost.post.author.displayName ?: "",
                                record?.text ?: "",
                            )
                        },
                        onLike = { uri, cid, likeUri -> viewModel.toggleLike(uri, cid, likeUri) },
                        onRepost = { uri, cid, repostUri -> viewModel.toggleRepost(uri, cid, repostUri) },
                        onBookmark = { uri, cid, bookmarkUri -> viewModel.toggleBookmark(uri, cid, bookmarkUri) },
                        moderationDecision = modDecision,
                    )
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

@Composable
private fun ProfileHeader(
    profile: com.kazahana.app.data.model.ProfileViewDetailed,
    isSelf: Boolean,
    isFollowLoading: Boolean,
    onFollowToggle: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    onSettingsClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (profile.banner != null) {
                AsyncImage(
                    model = profile.banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White,
                    )
                }
            }
        }

        // Avatar + Follow/Settings button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            AvatarImage(
                url = profile.avatar,
                size = 72.dp,
                modifier = Modifier
                    .offset(y = (-36).dp)
                    .clip(CircleShape),
            )

            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.common_settings),
                    )
                }
            }

            if (!isSelf) {
                val isFollowing = profile.viewer?.following != null
                if (isFollowing) {
                    OutlinedButton(
                        onClick = onFollowToggle,
                        enabled = !isFollowLoading,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.profile_unfollow))
                    }
                } else {
                    Button(
                        onClick = onFollowToggle,
                        enabled = !isFollowLoading,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.profile_follow))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-20).dp),
        ) {
            // Display name
            Text(
                text = profile.displayName ?: profile.handle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Handle
            Text(
                text = "@${profile.handle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            // Bio
            if (!profile.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Stats
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem(count = profile.followsCount ?: 0, label = stringResource(R.string.profile_following))
                StatItem(count = profile.followersCount ?: 0, label = stringResource(R.string.profile_followers))
                StatItem(count = profile.postsCount ?: 0, label = stringResource(R.string.profile_posts))
            }

            // Followed by indicator
            if (profile.viewer?.followedBy != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profile_follows_you),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
