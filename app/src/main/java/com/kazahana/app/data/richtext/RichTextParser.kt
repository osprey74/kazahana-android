package com.kazahana.app.data.richtext

import com.kazahana.app.data.model.Facet
import com.kazahana.app.data.model.FacetByteSlice
import com.kazahana.app.data.model.FacetFeature

/**
 * Detects mentions, URLs, and hashtags in post text and generates
 * AT Protocol facets with UTF-8 byte offsets.
 */
object RichTextParser {

    data class DetectedFacet(
        val start: Int,  // char index in original string
        val end: Int,    // char index in original string
        val feature: FacetFeature,
    )

    private val URL_REGEX = Regex(
        "https?://[^\\s\\p{Cc}\\p{Cs})\\]}>\"']+"
    )

    private val MENTION_REGEX = Regex(
        "(?<=^|[\\s])@([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
    )

    private val HASHTAG_REGEX = Regex(
        "(?<=^|[\\s])#[^\\s\\p{Cc}\\p{Cs}\\p{Punct}][^\\s\\p{Cc}\\p{Cs}]*"
    )

    /**
     * Detect all facets in the given text.
     * Mentions will have did=null — the caller must resolve handles to DIDs.
     */
    fun detectFacets(text: String): List<DetectedFacet> {
        val facets = mutableListOf<DetectedFacet>()

        // URLs
        for (match in URL_REGEX.findAll(text)) {
            var uri = match.value
            // Trim trailing punctuation that's likely not part of the URL
            while (uri.isNotEmpty() && uri.last() in ".,:;!?") {
                uri = uri.dropLast(1)
            }
            facets.add(
                DetectedFacet(
                    start = match.range.first,
                    end = match.range.first + uri.length,
                    feature = FacetFeature(
                        type = "app.bsky.richtext.facet#link",
                        uri = uri,
                    ),
                )
            )
        }

        // Mentions
        for (match in MENTION_REGEX.findAll(text)) {
            val handle = match.value.removePrefix("@")
            facets.add(
                DetectedFacet(
                    start = match.range.first,
                    end = match.range.last + 1,
                    feature = FacetFeature(
                        type = "app.bsky.richtext.facet#mention",
                        did = null, // Caller must resolve
                    ),
                )
            )
        }

        // Hashtags
        for (match in HASHTAG_REGEX.findAll(text)) {
            val tag = match.value.removePrefix("#")
            if (tag.isNotEmpty()) {
                facets.add(
                    DetectedFacet(
                        start = match.range.first,
                        end = match.range.last + 1,
                        feature = FacetFeature(
                            type = "app.bsky.richtext.facet#tag",
                            tag = tag,
                        ),
                    )
                )
            }
        }

        return facets.sortedBy { it.start }
    }

    /**
     * Convert char-index-based DetectedFacets to AT Protocol Facets with UTF-8 byte offsets.
     */
    fun toAtProtoFacets(text: String, detectedFacets: List<DetectedFacet>): List<Facet> {
        val utf8Bytes = text.toByteArray(Charsets.UTF_8)
        val charToByteOffset = buildCharToByteOffsetMap(text, utf8Bytes)

        return detectedFacets.mapNotNull { detected ->
            val byteStart = charToByteOffset.getOrNull(detected.start) ?: return@mapNotNull null
            val byteEnd = if (detected.end >= text.length) {
                utf8Bytes.size
            } else {
                charToByteOffset.getOrNull(detected.end) ?: return@mapNotNull null
            }

            Facet(
                index = FacetByteSlice(byteStart = byteStart, byteEnd = byteEnd),
                features = listOf(detected.feature),
            )
        }
    }

    /**
     * Build a mapping from char index to UTF-8 byte offset.
     * Index i maps to the byte offset where char i starts.
     */
    private fun buildCharToByteOffsetMap(text: String, utf8Bytes: ByteArray): IntArray {
        val map = IntArray(text.length + 1)
        var byteOffset = 0
        for (i in text.indices) {
            map[i] = byteOffset
            val codePoint = text.codePointAt(i)
            byteOffset += when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            // Skip low surrogate of supplementary character
            if (Character.isHighSurrogate(text[i]) && i + 1 < text.length) {
                // The next char index will be handled in the next iteration
            }
        }
        map[text.length] = byteOffset
        return map
    }

    /**
     * Extract handle from a mention DetectedFacet for DID resolution.
     */
    fun extractHandle(text: String, facet: DetectedFacet): String? {
        if (facet.feature.type != "app.bsky.richtext.facet#mention") return null
        return text.substring(facet.start, facet.end).removePrefix("@")
    }
}
