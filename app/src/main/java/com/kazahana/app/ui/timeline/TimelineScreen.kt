package com.kazahana.app.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.ui.common.LocalModerationSettings
import com.kazahana.app.ui.common.checkModeration
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    retapFlow: SharedFlow<Unit>? = null,
    onPostClick: (postUri: String) -> Unit = {},
    onProfileClick: (did: String) -> Unit = {},
    onReply: (postUri: String, postCid: String, rootUri: String, rootCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onQuote: (postUri: String, postCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Tab re-tap: reload feeds + refresh + scroll to top
    LaunchedEffect(retapFlow) {
        retapFlow?.collect {
            viewModel.loadSavedFeeds()
            viewModel.refresh()
            listState.animateScrollToItem(0)
        }
    }

    // Trigger load more when near the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5 && !uiState.isLoadingMore && uiState.hasMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: Feed selector dropdown + tab bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Feed dropdown selector (left side)
            if (uiState.showAllInSelector || uiState.feeds.size <= 1) {
                FeedDropdown(
                    feeds = if (uiState.showAllInSelector) uiState.allFeeds else uiState.feeds,
                    selectedFeed = uiState.selectedFeed,
                    onSelect = { viewModel.selectFeed(it) },
                )
            }

            // Feed tab bar (fills remaining space)
            if (uiState.feeds.size > 1) {
                val rawIndex = uiState.feeds.indexOf(uiState.selectedFeed)
                val isInTabs = rawIndex >= 0
                ScrollableTabRow(
                    selectedTabIndex = if (isInTabs) rawIndex else 0,
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f),
                    // Hide indicator when selected feed is not in the visible tabs
                    indicator = @Composable { tabPositions ->
                        if (isInTabs) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[rawIndex]),
                            )
                        }
                    },
                ) {
                    uiState.feeds.forEach { feed ->
                        Tab(
                            selected = isInTabs && feed == uiState.selectedFeed,
                            onClick = { viewModel.selectFeed(feed) },
                            text = {
                                Text(
                                    text = feed.labelRes?.let { stringResource(it) } ?: feed.displayName,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isLoading && uiState.posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null && uiState.posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                else -> {
                    val modSettings = LocalModerationSettings.current
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = uiState.posts,
                            key = { index, feedPost -> "${feedPost.post.uri}#$index" },
                        ) { _, feedPost ->
                            val record = remember(feedPost.post.record) {
                                try {
                                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                        .decodeFromJsonElement<com.kazahana.app.data.model.PostRecord>(feedPost.post.record)
                                } catch (_: Exception) { null }
                            }
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

                        // Loading more indicator
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
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
}

@Composable
private fun FeedDropdown(
    feeds: List<FeedInfo>,
    selectedFeed: FeedInfo?,
    onSelect: (FeedInfo) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Group by type
            val following = feeds.filter { it.type == "timeline" }
            val customFeeds = feeds.filter { it.type == "feed" }
            val lists = feeds.filter { it.type == "list" }

            following.forEach { feed ->
                FeedDropdownItem(
                    feed = feed,
                    isSelected = feed == selectedFeed,
                    onClick = {
                        onSelect(feed)
                        expanded = false
                    },
                )
            }

            if (customFeeds.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                customFeeds.forEach { feed ->
                    FeedDropdownItem(
                        feed = feed,
                        isSelected = feed == selectedFeed,
                        onClick = {
                            onSelect(feed)
                            expanded = false
                        },
                    )
                }
            }

            if (lists.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                lists.forEach { feed ->
                    FeedDropdownItem(
                        feed = feed,
                        isSelected = feed == selectedFeed,
                        onClick = {
                            onSelect(feed)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedDropdownItem(
    feed: FeedInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = feed.labelRes?.let { stringResource(it) } ?: feed.displayName,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        onClick = onClick,
    )
}
