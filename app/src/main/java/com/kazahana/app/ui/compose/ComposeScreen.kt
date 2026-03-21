package com.kazahana.app.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.kazahana.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(
    onNavigateBack: () -> Unit,
    replyTarget: ReplyTarget? = null,
    quoteTarget: QuoteTarget? = null,
    viewModel: ComposeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Set reply/quote targets once
    LaunchedEffect(replyTarget) {
        replyTarget?.let { viewModel.setReply(it) }
    }
    LaunchedEffect(quoteTarget) {
        quoteTarget?.let { viewModel.setQuote(it) }
    }

    // Navigate back after successful post
    LaunchedEffect(uiState.posted) {
        if (uiState.posted) {
            onNavigateBack()
        }
    }

    // Photo picker — maxItems fixed at 4; ViewModel trims excess
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = ComposeUiState.MAX_IMAGES,
        ),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    // Alt text dialog
    if (uiState.editingAltIndex != null) {
        val index = uiState.editingAltIndex!!
        val currentAlt = uiState.images.getOrNull(index)?.alt ?: ""
        var altText by remember(index) { mutableStateOf(currentAlt) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissAltEditor() },
            title = { Text(stringResource(R.string.compose_alt_title)) },
            text = {
                OutlinedTextField(
                    value = altText,
                    onValueChange = { altText = it },
                    label = { Text(stringResource(R.string.compose_alt_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateAlt(index, altText)
                    viewModel.dismissAltEditor()
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAltEditor() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            uiState.replyTarget != null -> stringResource(R.string.compose_reply_title)
                            uiState.quoteTarget != null -> stringResource(R.string.compose_quote_title)
                            else -> stringResource(R.string.compose_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = {
                    if (uiState.isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = { viewModel.post() },
                            enabled = uiState.canPost && !uiState.isOverLimit,
                        ) {
                            Text(stringResource(R.string.compose_post))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Reply context
            uiState.replyTarget?.let { reply ->
                ReplyQuotePreview(
                    label = stringResource(R.string.compose_replying_to),
                    authorName = reply.authorDisplayName ?: "@${reply.authorHandle}",
                    text = reply.text,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Quote context
            uiState.quoteTarget?.let { quote ->
                ReplyQuotePreview(
                    label = stringResource(R.string.compose_quoting),
                    authorName = quote.authorDisplayName ?: "@${quote.authorHandle}",
                    text = quote.text,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Text input
            OutlinedTextField(
                value = uiState.text,
                onValueChange = { viewModel.updateText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text(stringResource(R.string.compose_placeholder)) },
                maxLines = Int.MAX_VALUE,
            )

            // Character count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "${uiState.charCount}/${ComposeUiState.MAX_CHARS}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.isOverLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Error
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attached images
            if (uiState.images.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    uiState.images.forEachIndexed { index, image ->
                        ImageThumbnail(
                            image = image,
                            onRemove = { viewModel.removeImage(index) },
                            onEditAlt = { viewModel.startEditAlt(index) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Add image button
            if (uiState.canAddImage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                )
                            )
                        },
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = stringResource(R.string.compose_add_image),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.compose_add_image),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyQuotePreview(
    label: String,
    authorName: String,
    text: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = "$label $authorName",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (text.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImageThumbnail(
    image: AttachedImage,
    onRemove: () -> Unit,
    onEditAlt: () -> Unit,
) {
    Box(
        modifier = Modifier.size(100.dp),
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.alt.ifBlank { null },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onEditAlt() },
            contentScale = ContentScale.Crop,
        )

        // Remove button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape,
                )
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_delete),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ALT badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(
                    color = if (image.alt.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable { onEditAlt() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = "ALT",
                style = MaterialTheme.typography.labelSmall,
                color = if (image.alt.isNotBlank()) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
