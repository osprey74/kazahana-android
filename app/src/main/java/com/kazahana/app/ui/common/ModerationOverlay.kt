package com.kazahana.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kazahana.app.R
import com.kazahana.app.data.local.ModerationPref
import com.kazahana.app.data.model.ContentLabel

data class ModerationSettings(
    val adultContentEnabled: Boolean = false,
    val nudityPref: ModerationPref = ModerationPref.WARN,
    val sexualPref: ModerationPref = ModerationPref.WARN,
    val pornPref: ModerationPref = ModerationPref.WARN,
    val graphicMediaPref: ModerationPref = ModerationPref.WARN,
)

val LocalModerationSettings = compositionLocalOf { ModerationSettings() }

data class ModerationDecision(
    val shouldHide: Boolean = false,
    val shouldWarn: Boolean = false,
    val label: String = "",
)

fun checkModeration(
    labels: List<ContentLabel>,
    settings: ModerationSettings,
): ModerationDecision {
    for (label in labels) {
        if (label.neg == true) continue
        val value = label.labelValue ?: continue

        val pref = when (value) {
            "nudity" -> if (!settings.adultContentEnabled) ModerationPref.HIDE else settings.nudityPref
            "sexual" -> if (!settings.adultContentEnabled) ModerationPref.HIDE else settings.sexualPref
            "porn" -> if (!settings.adultContentEnabled) ModerationPref.HIDE else settings.pornPref
            "graphic-media" -> settings.graphicMediaPref
            else -> null
        }

        when (pref) {
            ModerationPref.HIDE -> return ModerationDecision(shouldHide = true, label = value)
            ModerationPref.WARN -> return ModerationDecision(shouldWarn = true, label = value)
            ModerationPref.SHOW, null -> continue
        }
    }
    return ModerationDecision()
}

fun checkModeration(
    labels: List<ContentLabel>,
    adultContentEnabled: Boolean,
    nudityPref: ModerationPref,
    sexualPref: ModerationPref,
    pornPref: ModerationPref,
    graphicMediaPref: ModerationPref,
): ModerationDecision = checkModeration(
    labels = labels,
    settings = ModerationSettings(adultContentEnabled, nudityPref, sexualPref, pornPref, graphicMediaPref),
)

@Composable
fun ModerationWarnOverlay(
    decision: ModerationDecision,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!decision.shouldWarn) {
        content()
        return
    }

    var revealed by remember { mutableStateOf(false) }

    if (revealed) {
        content()
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable { revealed = true }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 24.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.moderation_content_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
