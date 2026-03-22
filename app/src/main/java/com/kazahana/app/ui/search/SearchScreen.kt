package com.kazahana.app.ui.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.data.model.ProfileViewDetailed
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.LocalModerationSettings
import com.kazahana.app.ui.common.checkModeration
import com.kazahana.app.ui.timeline.PostCard
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Composable
fun SearchScreen(
    retapFlow: SharedFlow<Unit>? = null,
    viewModel: SearchViewModel = hiltViewModel(),
    onPostClick: (postUri: String) -> Unit = {},
    onProfileClick: (did: String) -> Unit = {},
    onReply: (postUri: String, postCid: String, rootUri: String, rootCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onQuote: (postUri: String, postCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(retapFlow) {
        retapFlow?.collect {
            listState.animateScrollToItem(0)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5 && !uiState.isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.updateQuery(it) },
            placeholder = { Text("Search posts or users") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.search()
                    keyboardController?.hide()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Tabs
        if (uiState.hasSearched) {
            TabRow(selectedTabIndex = SearchTab.entries.indexOf(uiState.selectedTab)) {
                SearchTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }
        }

        // Results
        when {
            uiState.isSearching && uiState.posts.isEmpty() && uiState.users.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !uiState.hasSearched -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Search Bluesky",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (uiState.selectedTab) {
                        SearchTab.POSTS -> {
                            if (uiState.posts.isEmpty() && !uiState.isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("No posts found")
                                    }
                                }
                            }
                            items(
                                items = uiState.posts,
                                key = { it.uri },
                            ) { postView ->
                                val feedPost = remember(postView) {
                                    FeedViewPost(post = postView)
                                }
                                val record = remember(postView.record) {
                                    try {
                                        Json { ignoreUnknownKeys = true }
                                            .decodeFromJsonElement<PostRecord>(postView.record)
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
                                        onReply(
                                            uri, cid, uri, cid,
                                            postView.author.handle,
                                            postView.author.displayName ?: "",
                                            record?.text ?: "",
                                        )
                                    },
                                    onLike = { uri, cid, likeUri -> viewModel.toggleLike(uri, cid, likeUri) },
                                    onRepost = { uri, cid, repostUri -> viewModel.toggleRepost(uri, cid, repostUri) },
                                    onBookmark = { uri, cid, bookmarkUri -> viewModel.toggleBookmark(uri, cid, bookmarkUri) },
                                    moderationDecision = modDecision,
                                )
                            }
                        }
                        SearchTab.USERS -> {
                            if (uiState.users.isEmpty() && !uiState.isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("No users found")
                                    }
                                }
                            }
                            items(
                                items = uiState.users,
                                key = { it.did },
                            ) { user ->
                                UserRow(
                                    user = user,
                                    onClick = { onProfileClick(user.did) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            }
                        }
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
private fun UserRow(
    user: ProfileViewDetailed,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(url = user.avatar, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName ?: user.handle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${user.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!user.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = user.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
