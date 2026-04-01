package com.kazahana.app.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.sp
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.BotBadge
import com.kazahana.app.ui.common.isBotAccount
import com.kazahana.app.ui.common.LocalModerationSettings
import com.kazahana.app.ui.common.checkModeration
import com.kazahana.app.ui.timeline.PostCard
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    onViewQuotes: (postUri: String) -> Unit = {},
    onSettingsClick: (() -> Unit)? = null,
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onCompose: ((String?) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Report dialog state
    var reportTarget by remember { mutableStateOf<Any?>(null) } // Pair<uri,cid> for post, "user" for profile user
    var isReportSubmitting by remember { mutableStateOf(false) }

    // Mute/Block confirmation dialogs
    var muteConfirmTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var blockConfirmTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            val canLoadMore = when (uiState.selectedTab) {
                ProfileTab.STARTER_PACKS -> !uiState.isLoadingStarterPacks && uiState.hasMoreStarterPacks
                else -> !uiState.isLoadingMore && uiState.hasMore
            }
            lastVisibleItem >= totalItems - 5 && canLoadMore
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

            // Show compact header when tabs have scrolled off screen
            // Item 0 = ProfileHeader, Item 1 = Tabs, so when firstVisibleItemIndex >= 2
            val showCompactHeader by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex >= 2
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
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
                            onMuteToggle = if (!viewModel.isSelf) { { viewModel.toggleMute() } } else null,
                            onBlockToggle = if (!viewModel.isSelf) { { viewModel.toggleBlock() } } else null,
                            onReport = if (!viewModel.isSelf) { { reportTarget = "user" } } else null,
                            onAddToList = if (!viewModel.isSelf) { { viewModel.showListSheet() } } else null,
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

                    // Tab content
                    when (uiState.selectedTab) {
                        ProfileTab.STARTER_PACKS -> {
                            if (uiState.isLoadingStarterPacks && uiState.actorStarterPacks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { CircularProgressIndicator() }
                                }
                            }
                            if (!uiState.isLoadingStarterPacks && uiState.actorStarterPacks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(stringResource(R.string.profile_no_starter_packs), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                                }
                            }
                            items(
                                items = uiState.actorStarterPacks,
                                key = { it.uri },
                            ) { starterPack ->
                                StarterPackCard(starterPack = starterPack)
                            }
                            if (uiState.isLoadingStarterPacks && uiState.actorStarterPacks.isNotEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                        else -> {
                            // Post-based tabs (POSTS, REPLIES, MEDIA, LIKES)
                            // Pinned post (posts tab only)
                            if (uiState.selectedTab == ProfileTab.POSTS && uiState.pinnedPost != null) {
                                item(key = "pinned_post") {
                                    val pinnedFeedPost = uiState.pinnedPost!!
                                    val pinnedRecord = remember(pinnedFeedPost.post.record) {
                                        try {
                                            com.kazahana.app.data.AppJson
                                                .decodeFromJsonElement<PostRecord>(pinnedFeedPost.post.record)
                                        } catch (_: Exception) { null }
                                    }
                                    val modSettings = LocalModerationSettings.current
                                    val modDecision = remember(pinnedFeedPost.post.labels, modSettings) {
                                        checkModeration(pinnedFeedPost.post.labels, modSettings)
                                    }
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 58.dp, top = 8.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.PushPin,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            )
                                            Text(
                                                text = stringResource(R.string.post_pinned),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            )
                                        }
                                        PostCard(
                                            feedPost = pinnedFeedPost,
                                            onClick = { uri -> onPostClick(uri) },
                                            onAuthorClick = { did -> onProfileClick(did) },
                                            onReply = { uri, cid ->
                                                onReply(
                                                    uri, cid, uri, cid,
                                                    pinnedFeedPost.post.author.handle,
                                                    pinnedFeedPost.post.author.displayName ?: "",
                                                    pinnedRecord?.text ?: "",
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
                                            onReportUser = { did -> reportTarget = "user" },
                                            onMuteUser = { did, handle -> muteConfirmTarget = Pair(did, handle) },
                                            onBlockUser = { did, handle -> blockConfirmTarget = Pair(did, handle) },
                                            isOwnPost = viewModel.isSelf,
                                            moderationDecision = modDecision,
                                            onHashtagClick = onHashtagClick,
                                            onMentionClick = onMentionClick,
                                            onSaveMedia = { imageUrls, videoUrl, videoThumbnail ->
                                                viewModel.saveMedia(context, imageUrls, videoUrl, videoThumbnail)
                                            },
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
                                    ) { CircularProgressIndicator() }
                                }
                            }

                            // Posts
                            itemsIndexed(
                                items = uiState.posts,
                                key = { index, feedPost -> "${feedPost.post.uri}#$index" },
                            ) { _, feedPost ->
                                val record = remember(feedPost.post.record) {
                                    try {
                                        com.kazahana.app.data.AppJson
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
                                    onQuote = { uri, cid, handle, displayName, text ->
                                        onQuote(uri, cid, handle, displayName, text)
                                    },
                                    onViewQuotes = { uri -> onViewQuotes(uri) },
                                    onReportPost = { uri, cid -> reportTarget = Pair(uri, cid) },
                                    onReportUser = { did -> reportTarget = "user" },
                                    onMuteUser = { did, handle -> muteConfirmTarget = Pair(did, handle) },
                                    onBlockUser = { did, handle -> blockConfirmTarget = Pair(did, handle) },
                                    moderationDecision = modDecision,
                                    onHashtagClick = onHashtagClick,
                                    onMentionClick = onMentionClick,
                                    onSaveMedia = { imageUrls, videoUrl, videoThumbnail ->
                                        viewModel.saveMedia(context, imageUrls, videoUrl, videoThumbnail)
                                    },
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { CircularProgressIndicator() }
                                }
                            }
                        }
                    }
                }
                } // PullToRefreshBox

                // Compact sticky header overlay
                AnimatedVisibility(
                    visible = showCompactHeader,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    CompactProfileHeader(
                        profile = profile,
                        selectedTab = uiState.selectedTab,
                        isSelf = viewModel.isSelf,
                        onNavigateBack = onNavigateBack,
                        onSettingsClick = if (viewModel.isSelf) onSettingsClick else null,
                        onTabSelect = { viewModel.selectTab(it) },
                    )
                }

                // FAB: compose with auto-mention for other users
                if (onCompose != null) {
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            if (!viewModel.isSelf) {
                                onCompose("@${profile.handle} ")
                            } else {
                                onCompose(null)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.compose_title),
                        )
                    }
                }
            }
        }
    }

    // List management bottom sheet
    if (uiState.showListSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideListSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_add_to_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                when {
                    uiState.isLoadingLists -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    uiState.curateLists.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.profile_no_curate_lists),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                    else -> {
                        uiState.curateLists.forEach { list ->
                            val isMember = uiState.listMembership[list.uri] != null
                            val isChecked = uiState.listMembership.containsKey(list.uri)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isChecked) {
                                        viewModel.toggleListMembership(list.uri) { result ->
                                            result
                                                .onSuccess { added ->
                                                    val msgRes = if (added) R.string.profile_list_add_success
                                                        else R.string.profile_list_remove_success
                                                    Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
                                                }
                                                .onFailure {
                                                    Toast.makeText(context, context.getString(R.string.profile_list_error), Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isMember,
                                    onCheckedChange = null, // handled by row click
                                    enabled = isChecked,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = list.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!list.description.isNullOrBlank()) {
                                        Text(
                                            text = list.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (!isChecked) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Report dialog
    val currentReportTarget = reportTarget
    if (currentReportTarget != null) {
        val isPost = currentReportTarget is Pair<*, *>
        com.kazahana.app.ui.common.ReportDialog(
            isPost = isPost,
            isSubmitting = isReportSubmitting,
            onSubmit = { reasonType, details ->
                isReportSubmitting = true
                if (isPost) {
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
                } else {
                    viewModel.reportUserAsync(reasonType, details) { result ->
                        isReportSubmitting = false
                        reportTarget = null
                        result.onSuccess {
                            Toast.makeText(context, context.getString(R.string.report_success), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.report_error), Toast.LENGTH_SHORT).show()
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

@Composable
private fun CompactProfileHeader(
    profile: com.kazahana.app.data.model.ProfileViewDetailed,
    selectedTab: ProfileTab,
    isSelf: Boolean,
    onNavigateBack: (() -> Unit)?,
    onSettingsClick: (() -> Unit)?,
    onTabSelect: (ProfileTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Avatar + Name + Settings row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            }

            AvatarImage(
                url = profile.avatar,
                size = 36.dp,
                modifier = Modifier
                    .padding(start = if (onNavigateBack != null) 0.dp else 8.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName ?: profile.handle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isBotAccount(profile.did, profile.labels)) {
                        Spacer(modifier = Modifier.width(4.dp))
                        BotBadge(size = 14.sp)
                    }
                }
                Text(
                    text = "@${profile.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.common_settings),
                    )
                }
            }
        }

        HorizontalDivider()

        // Tab bar
        ScrollableTabRow(
            selectedTabIndex = ProfileTab.entries.indexOf(selectedTab),
            edgePadding = 0.dp,
        ) {
            ProfileTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelect(tab) },
                    text = { Text(stringResource(tab.labelRes)) },
                )
            }
        }

        HorizontalDivider()
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
    onMuteToggle: (() -> Unit)? = null,
    onBlockToggle: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onAddToList: (() -> Unit)? = null,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    // More menu (mute/list/block/report)
                    if (onMuteToggle != null || onBlockToggle != null || onReport != null || onAddToList != null) {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                if (onAddToList != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_add_to_list)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.List,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onAddToList()
                                        },
                                    )
                                }
                                HorizontalDivider()
                                if (onMuteToggle != null) {
                                    val isMuted = profile.viewer?.muted == true
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (isMuted) R.string.profile_unmute_user
                                                    else R.string.profile_mute_user
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (isMuted) Icons.Outlined.VolumeUp
                                                else Icons.Outlined.VolumeOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onMuteToggle()
                                        },
                                    )
                                }
                                if (onBlockToggle != null) {
                                    val isBlocked = profile.viewer?.blocking != null
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (isBlocked) R.string.profile_unblock_user
                                                    else R.string.profile_block_user
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Block,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isBlocked) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onBlockToggle()
                                        },
                                    )
                                }
                                if (onReport != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_report)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Flag,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onReport()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    val isFollowing = profile.viewer?.following != null
                    if (isFollowing) {
                        OutlinedButton(
                            onClick = onFollowToggle,
                            enabled = !isFollowLoading,
                        ) {
                            Text(stringResource(R.string.profile_unfollow))
                        }
                    } else {
                        Button(
                            onClick = onFollowToggle,
                            enabled = !isFollowLoading,
                        ) {
                            Text(stringResource(R.string.profile_follow))
                        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.displayName ?: profile.handle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (isBotAccount(profile.did, profile.labels)) {
                    Spacer(modifier = Modifier.width(4.dp))
                    BotBadge(size = 18.sp)
                }
            }

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

            // Muted / Blocked notices
            if (profile.viewer?.muted == true) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profile_muted_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
            if (profile.viewer?.blocking != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profile_blocked_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        }
    }
}


@Composable
private fun StarterPackCard(starterPack: com.kazahana.app.data.model.StarterPackViewBasic) {
    // Parse name from the record JSON
    val name = remember(starterPack.record) {
        try {
            starterPack.record?.let { record ->
                val obj = record as? JsonObject
                (obj?.get("name") as? JsonPrimitive)?.content
            } ?: ""
        } catch (_: Exception) { "" }
    }
    val description = remember(starterPack.record) {
        try {
            starterPack.record?.let { record ->
                val obj = record as? JsonObject
                (obj?.get("description") as? JsonPrimitive)?.content
            }
        } catch (_: Exception) { null }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Starter packs don't have avatars, use a placeholder icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (name?.firstOrNull() ?: 'S').uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val memberCount = starterPack.listItemCount ?: 0
            if (memberCount > 0) {
                Text(
                    text = stringResource(R.string.profile_starter_pack_members, formatCount(memberCount)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
