package com.kazahana.app.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    myDid: String = "",
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(retapFlow) {
        retapFlow?.collect {
            viewModel.refresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                    val otherDid = convo.members.firstOrNull { it.did != myDid }?.did
                                        ?: convo.members.firstOrNull()?.did
                                    otherDid?.let { onProfileClick(it) }
                                },
                                onAccept = { viewModel.acceptConvo(convo.id) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        SmallFloatingActionButton(
            onClick = onNewConversation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.messages_new_conversation),
            )
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
    val otherMember = convo.members.firstOrNull { it.did != myDid } ?: convo.members.firstOrNull()
    val isPending = convo.status != null && convo.status != "accepted" || convo.opened == false

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
        AvatarImage(url = otherMember?.avatar, size = 48.dp, onClick = onAvatarClick)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = otherMember?.displayName ?: otherMember?.handle ?: stringResource(R.string.messages_unknown),
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
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val lastText = if (convo.lastMessage?.type?.contains("deletedMessage") == true) {
                    stringResource(R.string.messages_deleted)
                } else {
                    convo.lastMessage?.text ?: ""
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
