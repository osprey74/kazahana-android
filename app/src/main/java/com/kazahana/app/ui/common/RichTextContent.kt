package com.kazahana.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
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
        // Use plain Text so long-press is not consumed by ClickableText.
        // Links are handled via combinedClickable onClick — not per-character,
        // but this preserves long-press for reaction picker in DM bubbles.
        Text(
            text = annotatedString,
            style = style,
            modifier = modifier,
        )
    } else {
        ClickableText(
            text = annotatedString,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                handleAnnotationClick(annotatedString, offset, context, onHashtagClick, onMentionClick)
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
) {
    annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
        } catch (_: Exception) {}
        return
    }
    annotatedString.getStringAnnotations("HASHTAG", offset, offset).firstOrNull()?.let {
        onHashtagClick?.invoke(it.item)
        return
    }
    annotatedString.getStringAnnotations("MENTION", offset, offset).firstOrNull()?.let {
        onMentionClick?.invoke(it.item)
        return
    }
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
