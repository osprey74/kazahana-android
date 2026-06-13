package com.kazahana.app.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of the persistent evacuation banner overlay currently shown at the bottom
 * of the screen (0.dp when hidden). Screens with bottom-anchored content (e.g. the
 * chat input) read this to reserve space so the banner doesn't cover them.
 */
val LocalEvacBannerInset = compositionLocalOf { 0.dp }
