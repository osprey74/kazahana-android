package com.kazahana.app.data.ogp

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess

/**
 * OGP metadata extracted from a web page.
 */
data class OgpData(
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
)

/**
 * Fetches and parses Open Graph Protocol metadata from URLs.
 */
object OgpService {

    private val httpClient = HttpClient(OkHttp)

    private val URL_REGEX = Regex("""https?://[^\s\u3000]+""")

    /**
     * Extract the last URL from the given text, or null if none found.
     */
    fun extractUrl(text: String): String? {
        return URL_REGEX.findAll(text).lastOrNull()?.value
    }

    /**
     * Fetch OGP metadata from [url]. Returns null on failure.
     */
    suspend fun fetch(url: String): OgpData? {
        return try {
            val response = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (compatible; kazahana/0.1)")
                header("Accept", "text/html")
            }
            if (!response.status.isSuccess()) return null
            val html = response.bodyAsText()
            parse(url, html)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Download a thumbnail image as raw bytes. Returns null on failure.
     */
    suspend fun downloadImage(imageUrl: String): ByteArray? {
        return try {
            val response = httpClient.get(imageUrl) {
                header("User-Agent", "Mozilla/5.0 (compatible; kazahana/0.1)")
            }
            if (!response.status.isSuccess()) return null
            response.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(url: String, html: String): OgpData? {
        val ogTitle = getMetaContent(html, "og:title")
        val ogDescription = getMetaContent(html, "og:description")
        val ogImage = getMetaContent(html, "og:image")

        // Fallback to <title> tag
        val title = ogTitle ?: TITLE_REGEX.find(html)?.groupValues?.get(1)?.trim()
        val description = ogDescription ?: getMetaContent(html, "description")

        if (title.isNullOrBlank() && description.isNullOrBlank()) return null

        // Resolve relative image URL
        val imageUrl = resolveUrl(ogImage, url)

        return OgpData(
            url = url,
            title = decodeHtmlEntities(title ?: ""),
            description = decodeHtmlEntities(description ?: ""),
            imageUrl = imageUrl,
        )
    }

    private val TITLE_REGEX = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)

    /**
     * Extract content attribute from a <meta> tag matching the given property/name.
     */
    private fun getMetaContent(html: String, property: String): String? {
        // Match: <meta property="X" content="Y"> or <meta content="Y" property="X">
        // Also matches name= instead of property=
        val pattern = Regex(
            """<meta[^>]*(?:property|name)=["']${Regex.escape(property)}["'][^>]*content=["']([^"']*)["']""" +
                """|<meta[^>]*content=["']([^"']*)["'][^>]*(?:property|name)=["']${Regex.escape(property)}["']""",
            RegexOption.IGNORE_CASE,
        )
        val match = pattern.find(html) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.ifEmpty { null }
    }

    private fun resolveUrl(imageUrl: String?, baseUrl: String): String? {
        if (imageUrl == null) return null
        if (imageUrl.startsWith("http")) return imageUrl
        return try {
            java.net.URI(baseUrl).resolve(imageUrl).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { mr ->
                mr.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: mr.value
            }
            .replace(Regex("&#(\\d+);")) { mr ->
                mr.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: mr.value
            }
    }
}
