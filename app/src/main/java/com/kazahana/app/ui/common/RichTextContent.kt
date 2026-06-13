package com.kazahana.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.kazahana.app.data.model.Facet
import com.kazahana.app.data.richtext.RichTextParser

/**
 * Renders text with clickable URLs, hashtags, and mentions.
 *
 * When [facets] are provided (from server), uses those with proper UTF-8 byte offset
 * conversion. Otherwise falls back to local auto-detection via [RichTextParser].
 *
 * When [onLongPress] is provided, uses a plain Text with combinedClickable so that
 * long-press gestures are not swallowed. Link taps are still handled but only when
 * the tap lands on an annotated span.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RichTextContent(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    facets: List<Facet>? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onNonLinkClick: (() -> Unit)? = null,
    // Intercept URL taps. Return true if handled in-app (e.g. group invite link),
    // false to fall back to opening the URL externally.
    onUrlClick: ((String) -> Boolean)? = null,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary

    val annotatedString = remember(text, facets) {
        buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(color = color), 0, text.length)

            if (!facets.isNullOrEmpty()) {
                val byteToChar = buildByteToCharMap(text)
                for (facet in facets) {
                    val start = byteToChar.getOrElse(facet.index.byteStart) { -1 }
                    val end = byteToChar.getOrElse(facet.index.byteEnd) { -1 }
                    if (start < 0 || end < 0 || start >= end || end > text.length) continue
                    for (feature in facet.features) {
                        addStyle(SpanStyle(color = linkColor), start, end)
                        when (feature.type) {
                            "app.bsky.richtext.facet#link" -> {
                                addStringAnnotation("URL", feature.uri ?: "", start, end)
                            }
                            "app.bsky.richtext.facet#tag" -> {
                                addStringAnnotation("HASHTAG", feature.tag ?: "", start, end)
                            }
                            "app.bsky.richtext.facet#mention" -> {
                                addStringAnnotation("MENTION", feature.did ?: "", start, end)
                            }
                        }
                    }
                }
            } else {
                val detected = RichTextParser.detectFacets(text)
                for (facet in detected) {
                    val start = facet.start.coerceAtMost(text.length)
                    val end = facet.end.coerceAtMost(text.length)
                    if (start >= end) continue
                    addStyle(SpanStyle(color = linkColor), start, end)
                    when (facet.feature.type) {
                        "app.bsky.richtext.facet#link" -> {
                            addStringAnnotation("URL", facet.feature.uri ?: "", start, end)
                        }
                        "app.bsky.richtext.facet#tag" -> {
                            addStringAnnotation("HASHTAG", facet.feature.tag ?: "", start, end)
                        }
                        "app.bsky.richtext.facet#mention" -> {
                            val handle = text.substring(start, end).removePrefix("@")
                            addStringAnnotation("MENTION", handle, start, end)
                        }
                    }
                }
            }
        }
    }

    if (onLongPress != null) {
        // Detect taps (to follow links) while preserving long-press for the DM
        // reaction picker. A plain Text alone would swallow link taps entirely.
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = annotatedString,
            style = style,
            onTextLayout = { layoutResult = it },
            modifier = modifier.pointerInput(annotatedString) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { pos ->
                        val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                        val handled = offset >= 0 && handleAnnotationClick(
                            annotatedString, offset, context, onHashtagClick, onMentionClick, onUrlClick,
                        )
                        if (!handled) onNonLinkClick?.invoke()
                    },
                )
            },
        )
    } else {
        ClickableText(
            text = annotatedString,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                val handled = handleAnnotationClick(annotatedString, offset, context, onHashtagClick, onMentionClick, onUrlClick)
                if (!handled) {
                    onNonLinkClick?.invoke()
                }
            },
        )
    }
}

private fun handleAnnotationClick(
    annotatedString: androidx.compose.ui.text.AnnotatedString,
    offset: Int,
    context: android.content.Context,
    onHashtagClick: ((String) -> Unit)?,
    onMentionClick: ((String) -> Unit)?,
    onUrlClick: ((String) -> Boolean)?,
): Boolean {
    annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
        if (onUrlClick?.invoke(it.item) == true) return true
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
        } catch (_: Exception) {}
        return true
    }
    annotatedString.getStringAnnotations("HASHTAG", offset, offset).firstOrNull()?.let {
        onHashtagClick?.invoke(it.item)
        return true
    }
    annotatedString.getStringAnnotations("MENTION", offset, offset).firstOrNull()?.let {
        onMentionClick?.invoke(it.item)
        return true
    }
    return false
}

/**
 * Build a map from UTF-8 byte offset → char index for the given text.
 */
private fun buildByteToCharMap(text: String): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    var byteOffset = 0
    for (i in text.indices) {
        map[byteOffset] = i
        val codePoint = text.codePointAt(i)
        byteOffset += when {
            codePoint <= 0x7F -> 1
            codePoint <= 0x7FF -> 2
            codePoint <= 0xFFFF -> 3
            else -> 4
        }
    }
    map[byteOffset] = text.length
    return map
}
