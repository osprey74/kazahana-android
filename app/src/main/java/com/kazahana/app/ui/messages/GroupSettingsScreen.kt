package com.kazahana.app.ui.messages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.model.ChatMember
import com.kazahana.app.data.model.JoinRequestView
import com.kazahana.app.ui.common.AvatarImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    viewModel: GroupSettingsViewModel = hiltViewModel(),
    myDid: String = "",
    onNavigateBack: () -> Unit = {},
    onLeft: () -> Unit = {},
    onProfileClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.left) {
        if (uiState.left) onLeft()
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val convo = uiState.convo
    val group = convo?.groupInfo
    val isOwner = convo?.members?.firstOrNull { it.did == myDid }?.isOwner == true

    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var kickTarget by remember { mutableStateOf<ChatMember?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading || group == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                if (uiState.isLoading) CircularProgressIndicator()
                else Text(stringResource(R.string.messages_load_failed))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Group name
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isOwner) Modifier.clickable { showRenameDialog = true } else Modifier)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.name.ifBlank { stringResource(R.string.messages_group_unnamed) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.messages_group_member_count, group.memberCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    if (isOwner) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.group_rename))
                    }
                }
                HorizontalDivider()
            }

            // Owner-only: pending join requests
            if (isOwner && uiState.joinRequests.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.group_join_requests))
                }
                items(uiState.joinRequests, key = { it.requestedBy.did }) { req ->
                    JoinRequestRow(
                        req = req,
                        onApprove = { viewModel.approve(req.requestedBy.did) },
                        onReject = { viewModel.reject(req.requestedBy.did) },
                        onClick = { onProfileClick(req.requestedBy.did) },
                        enabled = !uiState.isWorking,
                    )
                }
                item { HorizontalDivider() }
            }

            // Owner-only: invite link
            if (isOwner) {
                item {
                    SectionHeader(stringResource(R.string.group_invite_link))
                    val link = group.joinLink
                    val url = link?.let { "https://bsky.app/chat/${it.code}" }
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        if (url != null) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (link.isEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { copyToClipboard(context, url); toast(context, R.string.profile_qr_copied_toast) },
                                    enabled = link.isEnabled,
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.profile_qr_copy))
                                }
                                OutlinedButton(
                                    onClick = { shareUrl(context, url) },
                                    enabled = link.isEnabled,
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.profile_qr_share))
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.group_invite_link_enabled),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Switch(
                                checked = link?.isEnabled == true,
                                onCheckedChange = { viewModel.toggleJoinLink() },
                                enabled = !uiState.isWorking,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            // Owner-only: lock toggle
            if (isOwner) {
                item {
                    val locked = group.isLocked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.group_lock), style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(
                            checked = locked,
                            onCheckedChange = { viewModel.toggleLock(locked) },
                            enabled = !uiState.isWorking && group.lockStatus != "locked-permanently",
                        )
                    }
                    HorizontalDivider()
                }
            }

            // Members
            item { SectionHeader(stringResource(R.string.group_members)) }
            items(convo.members, key = { it.did }) { member ->
                MemberRow(
                    member = member,
                    canKick = isOwner && member.did != myDid && !member.isOwner,
                    onKick = { kickTarget = member },
                    onClick = { onProfileClick(member.did) },
                    enabled = !uiState.isWorking,
                )
            }

            // Mute + Leave
            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.group_mute), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = convo.muted,
                        onCheckedChange = { viewModel.toggleMute(convo.muted) },
                        enabled = !uiState.isWorking,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !uiState.isWorking) { showLeaveDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.group_leave),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(group?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.group_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.create_group_name_label)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(newName)
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank(),
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.group_leave)) },
            text = { Text(stringResource(R.string.group_leave_confirm)) },
            confirmButton = {
                TextButton(onClick = { showLeaveDialog = false; viewModel.leave() }) {
                    Text(stringResource(R.string.group_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    kickTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            title = { Text(stringResource(R.string.group_kick)) },
            text = { Text(stringResource(R.string.group_kick_confirm, target.displayName ?: target.handle)) },
            confirmButton = {
                TextButton(onClick = { viewModel.kick(target.did); kickTarget = null }) {
                    Text(stringResource(R.string.group_kick))
                }
            },
            dismissButton = {
                TextButton(onClick = { kickTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun MemberRow(
    member: ChatMember,
    canKick: Boolean,
    onKick: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(url = member.avatar, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.displayName ?: member.handle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${member.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (member.isOwner) {
            Text(
                text = stringResource(R.string.group_role_owner),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (canKick) {
            IconButton(onClick = onKick, enabled = enabled) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.group_kick),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun JoinRequestRow(
    req: JoinRequestView,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(url = req.requestedBy.avatar, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = req.requestedBy.displayName ?: req.requestedBy.handle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${req.requestedBy.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onApprove, enabled = enabled) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.group_approve), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onReject, enabled = enabled) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.group_reject), tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun copyToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Group invite", url))
}

private fun shareUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun toast(context: Context, resId: Int) {
    Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
}
