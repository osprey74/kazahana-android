package com.kazahana.app.ui.messages

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.model.JoinLinkPreviewState
import com.kazahana.app.ui.common.GroupAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    viewModel: JoinGroupViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onJoined: (convoId: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Joined → open the conversation.
    LaunchedEffect(uiState.joinedConvoId) {
        uiState.joinedConvoId?.let { onJoined(it) }
    }
    // Pending → confirm and go back.
    LaunchedEffect(uiState.pending) {
        if (uiState.pending) {
            Toast.makeText(context, context.getString(R.string.join_group_pending_toast), Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }
    // Join error → localized toast.
    LaunchedEffect(uiState.joinErrorCode) {
        uiState.joinErrorCode?.let { code ->
            Toast.makeText(context, context.getString(joinErrorRes(code)), Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_group_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.previewError -> StatusText(stringResource(R.string.join_link_invalid))
                else -> when (val preview = uiState.preview) {
                    is JoinLinkPreviewState.Disabled -> StatusText(stringResource(R.string.join_link_disabled))
                    is JoinLinkPreviewState.Invalid, null -> StatusText(stringResource(R.string.join_link_invalid))
                    is JoinLinkPreviewState.Active -> {
                        val p = preview.preview
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GroupAvatar(avatarUrls = listOf(p.owner?.avatar), size = 72.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = p.name ?: stringResource(R.string.messages_group_unnamed),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.messages_group_member_count, p.memberCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            if (p.requireApproval) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.join_group_requires_approval),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.join() },
                                enabled = !uiState.isJoining,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (uiState.isJoining) {
                                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                } else {
                                    Text(stringResource(R.string.join_link_join))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
    )
}

/** Map a requestJoin atproto error code to a localized message resource. */
private fun joinErrorRes(code: String): Int = when {
    code.contains("ConvoLocked") -> R.string.join_error_locked
    code.contains("FollowRequired") -> R.string.join_error_follow_required
    code.contains("InvalidCode") -> R.string.join_link_invalid
    code.contains("LinkDisabled") -> R.string.join_link_disabled
    code.contains("MemberLimitReached") -> R.string.join_error_member_limit
    code.contains("UserKicked") -> R.string.join_error_kicked
    else -> R.string.join_error_generic
}
