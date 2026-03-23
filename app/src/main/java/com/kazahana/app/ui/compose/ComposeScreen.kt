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
import androidx.compose.material3.FilterChip
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
    initialText: String? = null,
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
    // Pre-fill shared text
    LaunchedEffect(initialText) {
        initialText?.let { viewModel.updateText(it) }
    }

    // Navigate back after successful post
    LaunchedEffect(uiState.posted) {
        if (uiState.posted) {
            onNavigateBack()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

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

    // Video picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
            val size = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            } catch (_: Exception) { 0L }
            viewModel.addVideo(uri, mimeType, size)
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

    // Video alt text dialog
    if (uiState.editingVideoAlt) {
        val currentVideoAlt = uiState.video?.alt ?: ""
        var videoAltText by remember(uiState.editingVideoAlt) { mutableStateOf(currentVideoAlt) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissVideoAltEditor() },
            title = { Text(stringResource(R.string.compose_alt_title)) },
            text = {
                OutlinedTextField(
                    value = videoAltText,
                    onValueChange = { videoAltText = it },
                    label = { Text(stringResource(R.string.compose_video_alt_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateVideoAlt(videoAltText)
                    viewModel.dismissVideoAltEditor()
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissVideoAltEditor() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // Threadgate dialog
    if (uiState.showThreadgateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissThreadgateDialog() },
            title = { Text(stringResource(R.string.threadgate_title)) },
            text = {
                Column {
                    ThreadgateSetting.entries.forEach { setting ->
                        val labelRes = when (setting) {
                            ThreadgateSetting.EVERYONE -> R.string.threadgate_everyone
                            ThreadgateSetting.NO_ONE -> R.string.threadgate_no_one
                            ThreadgateSetting.MENTION -> R.string.threadgate_mention
                            ThreadgateSetting.FOLLOWER -> R.string.threadgate_follower
                            ThreadgateSetting.FOLLOWING -> R.string.threadgate_following
                            ThreadgateSetting.MENTION_AND_FOLLOWER -> R.string.threadgate_mention_and_follower
                            ThreadgateSetting.MENTION_AND_FOLLOWING -> R.string.threadgate_mention_and_following
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThreadgateSetting(setting)
                                    viewModel.dismissThreadgateDialog()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (uiState.threadgateSetting == setting) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (uiState.threadgateSetting == setting) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    // Postgate dialog
    if (uiState.showPostgateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPostgateDialog() },
            title = { Text(stringResource(R.string.postgate_title)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setDisableEmbedding(false)
                                viewModel.dismissPostgateDialog()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.postgate_allow),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (!uiState.disableEmbedding) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!uiState.disableEmbedding) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setDisableEmbedding(true)
                                viewModel.dismissPostgateDialog()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.postgate_disable),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.disableEmbedding) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (uiState.disableEmbedding) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
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

            // Video preview
            uiState.video?.let { video ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_play),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.compose_video),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val mb = video.sizeBytes / 1_048_576.0
                            Text(
                                text = String.format("%.1f MB", mb),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // ALT badge
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(
                                    color = if (video.alt.isNotBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable { viewModel.startEditVideoAlt() }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "ALT",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (video.alt.isNotBlank()) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        // Remove button
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                )
                                .clickable { viewModel.removeVideo() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_delete),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Link card preview
            if (uiState.isFetchingLinkCard) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.compose_fetching_link_card),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val linkCard = uiState.linkCard
            if (linkCard != null && uiState.images.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LinkCardPreview(ogp = linkCard)
                    // Dismiss button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = CircleShape,
                            )
                            .clickable { viewModel.dismissLinkCard() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_delete),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Posting status (e.g. video uploading)
            uiState.postingStatus?.let { status ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Bottom action bar: add image/video + threadgate + postgate
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Add image button
                if (uiState.canAddImage) {
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
                }

                // Add video button
                if (uiState.canAddVideo) {
                    IconButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly,
                                )
                            )
                        },
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_play),
                            contentDescription = stringResource(R.string.compose_add_video),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Threadgate button — only for new posts (not replies)
                if (uiState.replyTarget == null) {
                    FilterChip(
                        selected = uiState.threadgateSetting != ThreadgateSetting.EVERYONE,
                        onClick = { viewModel.showThreadgateDialog() },
                        label = {
                            val labelRes = when (uiState.threadgateSetting) {
                                ThreadgateSetting.EVERYONE -> R.string.threadgate_everyone
                                ThreadgateSetting.NO_ONE -> R.string.threadgate_no_one
                                ThreadgateSetting.MENTION -> R.string.threadgate_mention
                                ThreadgateSetting.FOLLOWER -> R.string.threadgate_follower
                                ThreadgateSetting.FOLLOWING -> R.string.threadgate_following
                                ThreadgateSetting.MENTION_AND_FOLLOWER -> R.string.threadgate_mention_and_follower
                                ThreadgateSetting.MENTION_AND_FOLLOWING -> R.string.threadgate_mention_and_following
                            }
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Postgate button
                    FilterChip(
                        selected = uiState.disableEmbedding,
                        onClick = { viewModel.showPostgateDialog() },
                        label = {
                            Text(
                                text = if (uiState.disableEmbedding) {
                                    stringResource(R.string.postgate_disable)
                                } else {
                                    stringResource(R.string.postgate_allow)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
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

@Composable
private fun LinkCardPreview(ogp: com.kazahana.app.data.ogp.OgpData) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape)
            .padding(bottom = 8.dp),
    ) {
        ogp.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (ogp.title.isNotBlank()) {
                Text(
                    text = ogp.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (ogp.description.isNotBlank()) {
                Text(
                    text = ogp.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                text = ogp.url.removePrefix("https://").removePrefix("http://").take(40),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
