package com.kazahana.app.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.model.ConvoView
import com.kazahana.app.ui.common.AvatarImage
import com.kazahana.app.ui.common.GroupAvatar
import com.kazahana.app.ui.common.relativeTime
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    retapFlow: SharedFlow<Unit>? = null,
    viewModel: MessagesViewModel = hiltViewModel(),
    onConvoClick: (convoId: String) -> Unit = {},
    onProfileClick: (did: String) -> Unit = {},
    onNewConversation: () -> Unit = {},
    onNewGroup: () -> Unit = {},
    myDid: String = "",
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNewMenu by remember { mutableStateOf(false) }

    LaunchedEffect(retapFlow) {
        retapFlow?.collect {
            viewModel.refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // iOS 版と同様、新規会話ボタンは上部バーの右上に配置（下部 FAB は避難バナーと重なるため廃止）。
        // 外側 Scaffold が既にステータスバー分の余白を確保しているため、ここでは windowInsets を 0 にして二重余白を防ぐ。
        TopAppBar(
            title = { Text(stringResource(R.string.tab_messages)) },
            actions = {
                Box {
                    IconButton(onClick = { showNewMenu = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.messages_new_conversation),
                        )
                    }
                    DropdownMenu(
                        expanded = showNewMenu,
                        onDismissRequest = { showNewMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messages_new_conversation)) },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            onClick = {
                                showNewMenu = false
                                onNewConversation()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.create_group_title)) },
                            leadingIcon = { Icon(Icons.Outlined.Group, contentDescription = null) },
                            onClick = {
                                showNewMenu = false
                                onNewGroup()
                            },
                        )
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isLoading && uiState.conversations.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.conversations.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                uiState.conversations.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.messages_empty),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = uiState.conversations,
                            key = { it.id },
                        ) { convo ->
                            ConvoRow(
                                convo = convo,
                                myDid = myDid,
                                onClick = { onConvoClick(convo.id) },
                                onAvatarClick = {
                                    // Group avatars open the convo; 1:1 avatars open the other profile.
                                    if (convo.isGroup) {
                                        onConvoClick(convo.id)
                                    } else {
                                        val otherDid = convo.members.firstOrNull { it.did != myDid }?.did
                                            ?: convo.members.firstOrNull()?.did
                                        otherDid?.let { onProfileClick(it) }
                                    }
                                },
                                onAccept = { viewModel.acceptConvo(convo.id) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvoRow(
    convo: ConvoView,
    myDid: String,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onAccept: () -> Unit = {},
) {
    val group = convo.groupInfo
    val isGroup = group != null
    val otherMember = convo.members.firstOrNull { it.did != myDid } ?: convo.members.firstOrNull()
    val isPending = convo.status != null && convo.status != "accepted" || convo.opened == false
    val title = if (isGroup) {
        group.name.ifBlank { stringResource(R.string.messages_group_unnamed) }
    } else {
        otherMember?.displayName ?: otherMember?.handle ?: stringResource(R.string.messages_unknown)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPending) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isGroup) {
            GroupAvatar(
                avatarUrls = convo.members.map { it.avatar },
                size = 48.dp,
                modifier = Modifier.clickable(onClick = onAvatarClick),
            )
        } else {
            AvatarImage(url = otherMember?.avatar, size = 48.dp, onClick = onAvatarClick)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isGroup && group.isLocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.chat_group_locked),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (convo.unreadCount > 0 || isPending) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (convo.lastMessage?.sentAt != null) {
                    Text(
                        text = relativeTime(convo.lastMessage.sentAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            if (isPending) {
                Text(
                    text = stringResource(R.string.messages_request),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (isGroup) {
                Text(
                    text = stringResource(R.string.messages_group_member_count, group.memberCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val last = convo.lastMessage
                val systemData = last?.systemData
                val lastText = when {
                    last?.isDeleted == true -> stringResource(R.string.messages_deleted)
                    systemData != null -> systemMessageText(systemData, convo.members)
                    else -> last?.text ?: ""
                }
                Text(
                    text = lastText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isPending) {
                    FilledTonalButton(
                        onClick = onAccept,
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(
                            text = stringResource(R.string.messages_accept),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else if (convo.unreadCount > 0) {
                    Badge {
                        Text(convo.unreadCount.toString())
                    }
                }
            }
        }
    }
}
