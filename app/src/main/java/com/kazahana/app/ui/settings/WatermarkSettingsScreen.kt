package com.kazahana.app.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kazahana.app.R
import com.kazahana.app.data.WatermarkPosition
import com.kazahana.app.data.WatermarkPreset
import com.kazahana.app.data.WatermarkService
import com.kazahana.app.data.WatermarkSettings
import kotlin.math.roundToInt

private val w3cColors = listOf(
    "#FFFFFF", "#C0C0C0", "#808080", "#000000",
    "#FF0000", "#800000", "#FFFF00", "#808000",
    "#00FF00", "#008000", "#00FFFF", "#008080",
    "#0000FF", "#000080", "#FF00FF", "#800080",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatermarkSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val wmSettings by viewModel.watermarkSettings.collectAsState()
    val handle by viewModel.currentHandle.collectAsState()
    val context = LocalContext.current

    // Localized labels for watermark burn-in text
    val labelCopyright = stringResource(R.string.watermark_label_copyright)
    val labelAiJa = stringResource(R.string.watermark_label_ai_ja)
    val labelPhoto = stringResource(R.string.watermark_label_photo)
    val labels = remember(labelCopyright, labelAiJa, labelPhoto) {
        WatermarkService.buildLabelMap(labelCopyright, labelAiJa, labelPhoto)
    }

    // Generate preview bitmap
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wmSettings, handle, labels) {
        if (!wmSettings.enabled) {
            previewBitmap = null
            return@LaunchedEffect
        }
        try {
            val base = BitmapFactory.decodeResource(context.resources, R.drawable.watermark_preview)
                ?: return@LaunchedEffect
            val result = base.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            canvas.drawBitmap(base, 0f, 0f, null)
            WatermarkService.drawWatermark(
                canvas, base.width.toFloat(), base.height.toFloat(),
                wmSettings, handle, labels,
            )
            if (base !== result) base.recycle()
            previewBitmap = result
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watermark_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Section 1: Enable Toggle ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateWatermark(wmSettings.copy(enabled = !wmSettings.enabled)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.watermark_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = wmSettings.enabled,
                    onCheckedChange = { viewModel.updateWatermark(wmSettings.copy(enabled = it)) },
                )
            }
            Text(
                text = stringResource(R.string.watermark_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (wmSettings.enabled) {
                HorizontalDivider()

                // ── Section 2: Preview ──
                SectionHeader(stringResource(R.string.watermark_preview))
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.watermark_preview),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

                // ── Section 3: Preset ──
                SectionHeader(stringResource(R.string.watermark_preset))
                val presets = listOf(
                    WatermarkPreset.COPYRIGHT to R.string.watermark_preset_copyright,
                    WatermarkPreset.AI_JA to R.string.watermark_preset_ai_ja,
                    WatermarkPreset.AI_EN to R.string.watermark_preset_ai_en,
                    WatermarkPreset.AI_BOTH to R.string.watermark_preset_ai_both,
                    WatermarkPreset.PHOTO to R.string.watermark_preset_photo,
                    WatermarkPreset.CUSTOM to R.string.watermark_preset_custom,
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    presets.forEach { (preset, labelRes) ->
                        val selected = wmSettings.presetEnum == preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateWatermark(wmSettings.copy(preset = preset.name))
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // Custom text input
                    if (wmSettings.presetEnum == WatermarkPreset.CUSTOM) {
                        OutlinedTextField(
                            value = wmSettings.customText,
                            onValueChange = { text ->
                                if (text.length <= 50) {
                                    viewModel.updateWatermark(wmSettings.copy(customText = text))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            placeholder = { Text(stringResource(R.string.watermark_custom_placeholder)) },
                            maxLines = 4,
                        )
                        Text(
                            text = stringResource(R.string.watermark_custom_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                // ── Section 4: Position ──
                SectionHeader(stringResource(R.string.watermark_position))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // 3x2 grid
                    val fixedPositions = listOf(
                        listOf(
                            WatermarkPosition.TL to R.string.watermark_pos_top_left,
                            WatermarkPosition.TC to R.string.watermark_pos_top_center,
                            WatermarkPosition.TR to R.string.watermark_pos_top_right,
                        ),
                        listOf(
                            WatermarkPosition.BL to R.string.watermark_pos_bottom_left,
                            WatermarkPosition.BC to R.string.watermark_pos_bottom_center,
                            WatermarkPosition.BR to R.string.watermark_pos_bottom_right,
                        ),
                    )
                    fixedPositions.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { (pos, labelRes) ->
                                PositionButton(
                                    label = stringResource(labelRes),
                                    selected = wmSettings.positionEnum == pos,
                                    onClick = { viewModel.updateWatermark(wmSettings.copy(position = pos.name)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Random + Tile row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PositionButton(
                            label = stringResource(R.string.watermark_pos_random),
                            selected = wmSettings.positionEnum == WatermarkPosition.RANDOM,
                            onClick = { viewModel.updateWatermark(wmSettings.copy(position = WatermarkPosition.RANDOM.name)) },
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        PositionButton(
                            label = stringResource(R.string.watermark_pos_tile),
                            selected = wmSettings.positionEnum == WatermarkPosition.TILE,
                            onClick = { viewModel.updateWatermark(wmSettings.copy(position = WatermarkPosition.TILE.name)) },
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

                // ── Section 5: Opacity & Font Size ──
                SectionHeader(stringResource(R.string.watermark_opacity))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.watermark_opacity),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${wmSettings.opacity.roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Slider(
                        value = wmSettings.opacity,
                        onValueChange = { viewModel.updateWatermark(wmSettings.copy(opacity = it)) },
                        valueRange = 20f..100f,
                        steps = 15, // (100-20)/5 - 1 = 15
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.watermark_font_size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${wmSettings.fontSize.roundToInt()}px",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Slider(
                        value = wmSettings.fontSize,
                        onValueChange = { viewModel.updateWatermark(wmSettings.copy(fontSize = it)) },
                        valueRange = 8f..20f,
                        steps = 11, // (20-8)/1 - 1 = 11
                    )
                    Text(
                        text = stringResource(R.string.watermark_font_size_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

                // ── Section 6: Text Color ──
                SectionHeader(stringResource(R.string.watermark_text_color))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Color palette grid — 8 columns
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(80.dp),
                    ) {
                        items(w3cColors) { hex ->
                            val selected = wmSettings.textColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        viewModel.updateWatermark(wmSettings.copy(textColor = hex))
                                    },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // HEX input
                    var hexInput by remember(wmSettings.textColor) {
                        mutableStateOf(wmSettings.textColor.removePrefix("#"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { raw ->
                                val cleaned = raw.uppercase().filter { it.isLetterOrDigit() && it in "0123456789ABCDEF" }.take(6)
                                hexInput = cleaned
                                if (cleaned.length == 6) {
                                    viewModel.updateWatermark(wmSettings.copy(textColor = "#$cleaned"))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("FFFFFF") },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    try {
                                        Color(android.graphics.Color.parseColor(wmSettings.textColor))
                                    } catch (_: Exception) {
                                        Color.White
                                    }
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

                // ── Section 7: Toggles ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateWatermark(
                                wmSettings.copy(confirmBeforePost = !wmSettings.confirmBeforePost)
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.watermark_confirm_before_post),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = wmSettings.confirmBeforePost,
                        onCheckedChange = {
                            viewModel.updateWatermark(wmSettings.copy(confirmBeforePost = it))
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateWatermark(wmSettings.copy(skipVideo = !wmSettings.skipVideo))
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.watermark_skip_video),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = wmSettings.skipVideo,
                        onCheckedChange = {
                            viewModel.updateWatermark(wmSettings.copy(skipVideo = it))
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PositionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
