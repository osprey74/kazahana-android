package com.kazahana.app.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    onPostClick: (postUri: String) -> Unit = {},
    onReply: (postUri: String, postCid: String, rootUri: String, rootCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onQuote: (postUri: String, postCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = uiState.posts,
                        key = { it.post.uri },
                    ) { feedPost ->
                        val record = remember(feedPost.post.record) {
                            try {
                                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                    .decodeFromJsonElement<com.kazahana.app.data.model.PostRecord>(feedPost.post.record)
                            } catch (_: Exception) { null }
                        }
                        PostCard(
                            feedPost = feedPost,
                            onClick = { uri -> onPostClick(uri) },
                            onReply = { uri, cid ->
                                // For replies, root = original thread root or the post itself
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
