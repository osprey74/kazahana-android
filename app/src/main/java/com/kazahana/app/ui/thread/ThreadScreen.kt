package com.kazahana.app.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.model.FeedViewPost
import com.kazahana.app.data.model.PostRecord
import com.kazahana.app.data.model.PostView
import com.kazahana.app.ui.timeline.PostCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

private val json = Json { ignoreUnknownKeys = true }

private fun parsePostText(post: PostView): String {
    return try {
        json.decodeFromJsonElement<PostRecord>(post.record).text
    } catch (_: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    onNavigateBack: () -> Unit,
    onReply: (postUri: String, postCid: String, rootUri: String, rootCid: String, authorHandle: String, authorDisplayName: String, postText: String) -> Unit = { _, _, _, _, _, _, _ -> },
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Thread root URI for reply references
    val rootUri = uiState.parentPosts.firstOrNull()?.uri ?: uiState.mainPost?.uri ?: ""
    val rootCid = uiState.parentPosts.firstOrNull()?.cid ?: uiState.mainPost?.cid ?: ""

    val listState = rememberLazyListState()

    // Scroll to main post (after parent posts) when data loads
    val mainPostIndex = uiState.parentPosts.size // main post is right after parents
    LaunchedEffect(uiState.mainPost) {
        if (uiState.mainPost != null && uiState.parentPosts.isNotEmpty()) {
            listState.scrollToItem(mainPostIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.thread_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            else -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    val bottomPadding = maxHeight * 0.5f

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Parent posts (thread context)
                        val parentCount = uiState.parentPosts.size
                        items(parentCount) { index ->
                            val post = uiState.parentPosts[index]
                            ThreadPostItem(
                                post = post,
                                isMainPost = false,
                                showThreadLine = index > 0, // No line above the first (root) post
                                rootUri = rootUri,
                                rootCid = rootCid,
                                onReply = onReply,
                                onLike = { uri, cid, likeUri -> viewModel.toggleLike(uri, cid, likeUri) },
                                onRepost = { uri, cid, repostUri -> viewModel.toggleRepost(uri, cid, repostUri) },
                                onBookmark = { uri, cid, bookmarkUri -> viewModel.toggleBookmark(uri, cid, bookmarkUri) },
                            )
                        }

                        // Main post (highlighted)
                        uiState.mainPost?.let { mainPost ->
                            item {
                                ThreadPostItem(
                                    post = mainPost,
                                    isMainPost = true,
                                    showThreadLine = uiState.parentPosts.isNotEmpty(), // Line above main post if parents exist
                                    rootUri = rootUri,
                                    rootCid = rootCid,
                                    onReply = onReply,
                                    onLike = { uri, cid, likeUri -> viewModel.toggleLike(uri, cid, likeUri) },
                                    onRepost = { uri, cid, repostUri -> viewModel.toggleRepost(uri, cid, repostUri) },
                                    onBookmark = { uri, cid, bookmarkUri -> viewModel.toggleBookmark(uri, cid, bookmarkUri) },
                                )
                            }
                        }

                        // Replies
                        if (uiState.replies.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                )
                            }

                            items(uiState.replies) { reply ->
                                PostCard(
                                    feedPost = FeedViewPost(post = reply),
                                    onReply = { uri, cid ->
                                        onReply(
                                            uri, cid, rootUri, rootCid,
                                            reply.author.handle,
                                            reply.author.displayName ?: "",
                                            parsePostText(reply),
                                        )
                                    },
                                    onLike = { uri, cid, likeUri -> viewModel.toggleLike(uri, cid, likeUri) },
                                    onRepost = { uri, cid, repostUri -> viewModel.toggleRepost(uri, cid, repostUri) },
                                    onBookmark = { uri, cid, bookmarkUri -> viewModel.toggleBookmark(uri, cid, bookmarkUri) },
                                )
                            }
                        }

                        // Bottom padding (50% of screen height)
                        item {
                            Spacer(modifier = Modifier.height(bottomPadding))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadPostItem(
    post: PostView,
    isMainPost: Boolean,
    showThreadLine: Boolean,
    rootUri: String,
    rootCid: String,
    onReply: (String, String, String, String, String, String, String) -> Unit,
    onLike: (String, String, String?) -> Unit,
    onRepost: (String, String, String?) -> Unit,
    onBookmark: (String, String, String?) -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Column(
        modifier = if (isMainPost) {
            Modifier
                .fillMaxWidth()
                .background(highlightColor)
        } else Modifier.fillMaxWidth(),
    ) {
        if (showThreadLine) {
            Box(
                modifier = Modifier
                    .padding(start = 34.dp)
                    .width(2.dp)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            )
        }
        PostCard(
            feedPost = FeedViewPost(post = post),
            onReply = { uri, cid ->
                onReply(
                    uri, cid, rootUri, rootCid,
                    post.author.handle,
                    post.author.displayName ?: "",
                    parsePostText(post),
                )
            },
            onLike = onLike,
            onRepost = onRepost,
            onBookmark = onBookmark,
        )
    }
}
