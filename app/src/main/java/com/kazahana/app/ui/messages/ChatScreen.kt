package com.kazahana.app.ui.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.model.ChatMessageOrDeleted
import com.kazahana.app.data.model.ChatReaction
import com.kazahana.app.ui.common.relativeTime

private val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🎉")
private const val DELETED_MESSAGE_TYPE = "chat.bsky.convo.defs#deletedMessageView"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    myDid: String = "",
    onNavigateBack: () -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val otherMember = uiState.convo?.members?.firstOrNull { it.did != myDid }
        ?: uiState.convo?.members?.firstOrNull()

    var reactionTargetId by remember { mutableStateOf<String?>(null) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5 && uiState.hasMore && !uiState.isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = otherMember?.displayName ?: otherMember?.handle ?: "",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            when {
                uiState.isLoading && uiState.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.messages_no_messages),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        reverseLayout = true,
                    ) {
                        items(
                            items = uiState.messages,
                            key = { it.id ?: it.hashCode().toString() },
                        ) { message ->
                            MessageBubble(
                                message = message,
                                isMine = message.sender?.did == myDid,
                                myDid = myDid,
                                showReactionPicker = reactionTargetId == message.id,
                                onLongPress = {
                                    val isDeleted = message.type?.contains("deletedMessage") == true
                                    if (!isDeleted && message.id != null) {
                                        reactionTargetId = if (reactionTargetId == message.id) null else message.id
                                    }
                                },
                                onReaction = { emoji ->
                                    message.id?.let { id ->
                                        viewModel.toggleReaction(id, emoji, myDid)
                                    }
                                    reactionTargetId = null
                                },
                                onReactionBadgeTap = { emoji ->
                                    message.id?.let { id ->
                                        viewModel.toggleReaction(id, emoji, myDid)
                                    }
                                },
                                onDismissReactions = { reactionTargetId = null },
                                onDelete = {
                                    message.id?.let { id ->
                                        viewModel.deleteMessage(id)
                                    }
                                    reactionTargetId = null
                                },
                                onHashtagClick = onHashtagClick,
                                onProfileClick = onProfileClick,
                            )
                        }
                    }
                }
            }

            // Message input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.messageText,
                    onValueChange = { viewModel.updateMessageText(it) },
                    placeholder = { Text(stringResource(R.string.messages_placeholder)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = uiState.messageText.isNotBlank() && !uiState.isSending,
                ) {
                    if (uiState.isSending) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (uiState.messageText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessageOrDeleted,
    isMine: Boolean,
    myDid: String,
    showReactionPicker: Boolean = false,
    onLongPress: () -> Unit = {},
    onReaction: (String) -> Unit = {},
    onReactionBadgeTap: (String) -> Unit = {},
    onDismissReactions: () -> Unit = {},
    onDelete: () -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
) {
    val isDeleted = message.type?.contains("deletedMessage") == true
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val reactions = message.reactions ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        // Reaction picker (shown above the bubble)
        AnimatedVisibility(
            visible = showReactionPicker,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        val alreadyReacted = reactions.any { it.value == emoji && it.sender.did == myDid }
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (alreadyReacted)
                                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    else Modifier
                                )
                                .clickable { onReaction(emoji) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    if (isMine) {
                        Text(
                            text = "\uD83D\uDDD1\uFE0F",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onDelete() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // Message bubble
        Column(
            modifier = Modifier
                .widthIn(max = screenWidth * 0.75f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp,
                    )
                )
                .background(
                    if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .combinedClickable(
                    onClick = {
                        if (showReactionPicker) onDismissReactions()
                    },
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (isDeleted) {
                Text(
                    text = stringResource(R.string.messages_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            } else {
                com.kazahana.app.ui.common.RichTextContent(
                    text = message.text ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    onHashtagClick = onHashtagClick,
                    onMentionClick = onProfileClick,
                    onLongPress = onLongPress,
                )
            }
            if (message.sentAt != null) {
                Text(
                    text = relativeTime(message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }

        // Reaction badges (below the bubble)
        if (reactions.isNotEmpty()) {
            ReactionBadges(
                reactions = reactions,
                myDid = myDid,
                onTap = onReactionBadgeTap,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionBadges(
    reactions: List<ChatReaction>,
    myDid: String,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Group reactions by emoji
    val grouped = remember(reactions) {
        reactions.groupBy { it.value }.map { (emoji, list) ->
            Triple(emoji, list.size, list.any { it.sender.did == myDid })
        }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        grouped.forEach { (emoji, count, isMine) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTap(emoji) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = emoji, fontSize = 14.sp)
                    if (count > 1) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isMine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
