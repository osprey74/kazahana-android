package com.kazahana.app.ui.evacuation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kazahana.app.R
import com.kazahana.app.data.model.AlertLevel
import com.kazahana.app.data.model.Prefecture

/**
 * 画面下部に常駐する避難バナー。
 * level3 は控えめな黄、level4 は赤、level5 はピンク（特別警報級）。色は iOS と一致。
 */
@Composable
fun EvacuationBannerView(
    highestLevel: AlertLevel,
    prefecture: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bannerColor = when (highestLevel) {
        AlertLevel.LEVEL3 -> Color(0xFFCA8A04) // 黄系
        AlertLevel.LEVEL4 -> Color(0xFFDC2626) // 赤系
        AlertLevel.LEVEL5 -> Color(0xFFBE185D) // ピンク系（特別警報級）
    }
    val titleRes = when (highestLevel) {
        AlertLevel.LEVEL3 -> R.string.evacuation_banner_title_level3
        AlertLevel.LEVEL4 -> R.string.evacuation_banner_title_level4
        AlertLevel.LEVEL5 -> R.string.evacuation_banner_title_level5
    }
    val prefName = Prefecture.fromRawValue(prefecture)?.displayName

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bannerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = Color.White,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            val secondary = prefName?.let { "$it ・ " }.orEmpty() +
                stringResource(R.string.evacuation_banner_action)
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White,
        )
    }
}
