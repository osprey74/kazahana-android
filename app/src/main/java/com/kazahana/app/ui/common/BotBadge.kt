package com.kazahana.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kazahana.app.R
import com.kazahana.app.data.model.ContentLabel

private val SmartToyFont = FontFamily(Font(R.font.smart_toy))

/**
 * Bot account badge displayed next to display names.
 * Uses the Material Symbols Rounded "smart_toy" glyph (U+F06C) from SmartToy.ttf.
 */
@Composable
fun BotBadge(
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
) {
    Text(
        text = "\uF06C",
        fontFamily = SmartToyFont,
        fontSize = size,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = modifier,
    )
}

/**
 * Returns true if the account with the given DID has self-applied the "bot" label.
 * Both conditions must be met: label value is "bot" AND label source equals the account's own DID.
 */
fun isBotAccount(did: String, labels: List<ContentLabel>?): Boolean {
    return labels?.any { it.labelValue == "bot" && it.src == did } ?: false
}
