package com.kazahana.app.ui.messages.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kazahana.app.R
import com.kazahana.app.data.model.JoinLinkEmbedView
import com.kazahana.app.ui.common.GroupAvatar
import androidx.compose.ui.res.stringResource

/**
 * Renders a `chat.bsky.embed.joinLink#view` as an in-chat invite card.
 * Active links show group name / owner / member count and a join button;
 * disabled or invalid links render a grayed-out state.
 */
@Composable
fun JoinLinkEmbed(
    embed: JoinLinkEmbedView,
    onJoin: (code: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = embed.preview

    if (embed.isDisabled || embed.isInvalid || preview == null || preview.name == null) {
        OutlinedCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (embed.isInvalid) R.string.join_link_invalid else R.string.join_link_disabled,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupAvatar(avatarUrls = listOf(preview.owner?.avatar), size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preview.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.messages_group_member_count, preview.memberCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = { onJoin(preview.code) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.join_link_join))
            }
        }
    }
}
