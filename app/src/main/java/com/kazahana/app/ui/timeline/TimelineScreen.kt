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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
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
    onViewQuotes: (postUri: String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    activeHandle: String? = null,
    onAccountSwitcherClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Report dialog state
    var reportTarget by remember { mutableStateOf<Any?>(null) } // Pair<uri,cid> for post, String for user DID
    var isReportSubmitting by remember { mutableStateOf(false) }

    // Mute confirmation dialog state
    var muteConfirmTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // did, handle

    // Block confirmation dialog state
    var blockConfirmTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // did, handle

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
            // Feed dropdown selector (left side) — shows hidden feeds only when "show all" is on
            if (uiState.showAllInSelector && uiState.hiddenFeeds.isNotEmpty()) {
                FeedDropdown(
                    feeds = uiState.hiddenFeeds,
                    selectedFeed = uiState.selectedFeed,
                    onSelect = {
                        val wasRetap = viewModel.selectFeed(it)
                        if (wasRetap) scope.launch { listState.animateScrollToItem(0) }
                    },
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
                            onClick = {
                                val wasRetap = viewModel.selectFeed(feed)
                                if (wasRetap) scope.launch { listState.animateScrollToItem(0) }
                            },
                            text = {
                                Text(
                                    text = feed.labelRes?.let { stringResource(it) } ?: feed.displayName,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Account handle button (right side)
            if (activeHandle != null && onAccountSwitcherClick != null) {
                Text(
                    text = abbreviatedHandle(activeHandle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clickable(onClick = onAccountSwitcherClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1,
                )
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
                                    com.kazahana.app.data.AppJson
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
                                onQuote = { uri, cid, handle, displayName, text ->
                                    onQuote(uri, cid, handle, displayName, text)
                                },
                                onViewQuotes = { uri -> onViewQuotes(uri) },
                                onHidePost = { uri -> viewModel.hidePost(uri) },
                                onMuteThread = { uri, mute -> viewModel.muteThread(uri, mute) },
                                onReportPost = { uri, cid -> reportTarget = Pair(uri, cid) },
                                onReportUser = { did -> reportTarget = did },
                                onMuteUser = { did, handle -> muteConfirmTarget = Pair(did, handle) },
                                onBlockUser = { did, handle -> blockConfirmTarget = Pair(did, handle) },
                                isOwnPost = feedPost.post.author.did == viewModel.currentDid,
                                moderationDecision = modDecision,
                                bsafTags = uiState.bsafTags[feedPost.post.uri],
                                bsafDuplicate = uiState.bsafDuplicates[feedPost.post.uri],
                                onHashtagClick = onHashtagClick,
                                onMentionClick = onMentionClick,
                                onSaveMedia = { imageUrls, videoUrl, videoThumbnail ->
                                    viewModel.saveMedia(context, imageUrls, videoUrl, videoThumbnail)
                                },
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

    // Report dialog
    val currentReportTarget = reportTarget
    if (currentReportTarget != null) {
        com.kazahana.app.ui.common.ReportDialog(
            isPost = currentReportTarget is Pair<*, *>,
            isSubmitting = isReportSubmitting,
            onSubmit = { reasonType, details ->
                isReportSubmitting = true
                when (currentReportTarget) {
                    is Pair<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val pair = currentReportTarget as Pair<String, String>
                        viewModel.reportPostAsync(pair.first, pair.second, reasonType, details) { result ->
                            isReportSubmitting = false
                            reportTarget = null
                            result.onSuccess {
                                Toast.makeText(context, context.getString(R.string.report_success), Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, context.getString(R.string.report_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    is String -> {
                        viewModel.reportUserAsync(currentReportTarget, reasonType, details) { result ->
                            isReportSubmitting = false
                            reportTarget = null
                            result.onSuccess {
                                Toast.makeText(context, context.getString(R.string.report_success), Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, context.getString(R.string.report_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onDismiss = { reportTarget = null },
        )
    }

    // Mute confirmation dialog
    muteConfirmTarget?.let { (did, handle) ->
        AlertDialog(
            onDismissRequest = { muteConfirmTarget = null },
            title = { Text(stringResource(R.string.mute_user_confirm_title)) },
            text = { Text(stringResource(R.string.mute_user_confirm_message, handle)) },
            confirmButton = {
                Button(onClick = {
                    muteConfirmTarget = null
                    viewModel.muteUser(did)
                    Toast.makeText(context, context.getString(R.string.mute_user_success), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.profile_mute)) }
            },
            dismissButton = {
                TextButton(onClick = { muteConfirmTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // Block confirmation dialog
    blockConfirmTarget?.let { (did, handle) ->
        AlertDialog(
            onDismissRequest = { blockConfirmTarget = null },
            title = { Text(stringResource(R.string.block_user_confirm_title)) },
            text = { Text(stringResource(R.string.block_user_confirm_message, handle)) },
            confirmButton = {
                Button(onClick = {
                    blockConfirmTarget = null
                    viewModel.blockUser(did)
                    Toast.makeText(context, context.getString(R.string.block_user_success), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.profile_block)) }
            },
            dismissButton = {
                TextButton(onClick = { blockConfirmTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedDropdown(
    feeds: List<FeedInfo>,
    selectedFeed: FeedInfo?,
    onSelect: (FeedInfo) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }

    // Trigger button
    Row(
        modifier = Modifier
            .clickable { showSheet = true }
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

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(feeds) { feed ->
                    val isSelected = feed == selectedFeed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(feed)
                                showSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
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
                }
            }
        }
    }
}

/** Abbreviate handle: "@handle" truncated to 22 chars max. */
private fun abbreviatedHandle(handle: String): String {
    val full = "@$handle"
    return if (full.length > 22) full.take(21) + "\u2026" else full
}
