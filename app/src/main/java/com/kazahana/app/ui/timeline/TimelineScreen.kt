package com.kazahana.app.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    // Tab re-tap: refresh + scroll to top
    LaunchedEffect(retapFlow) {
        retapFlow?.collect {
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
        // Feed selector tabs
        if (uiState.feeds.size > 1) {
            val selectedIndex = uiState.feeds.indexOf(uiState.selectedFeed).coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
            ) {
                uiState.feeds.forEach { feed ->
                    Tab(
                        selected = feed == uiState.selectedFeed,
                        onClick = { viewModel.selectFeed(feed) },
                        text = { Text(feed.displayName, maxLines = 1) },
                    )
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
