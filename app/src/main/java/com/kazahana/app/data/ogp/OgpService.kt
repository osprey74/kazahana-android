package com.kazahana.app.data.ogp

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.nio.charset.Charset

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
        val html = fetchHtml(url) ?: return null
        return parse(url, html)
    }

    /**
     * Fetch the raw HTML of [url]. Returns null on failure.
     */
    suspend fun fetchHtml(url: String): String? {
        return try {
            val response = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (compatible; kazahana/0.1)")
                header("Accept", "text/html")
            }
            if (!response.status.isSuccess()) return null
            val bytes = response.readBytes()
            val charset = detectCharset(bytes, response.headers[HttpHeaders.ContentType])
            String(bytes, charset)
        } catch (_: Exception) {
            null
        }
    }

    private val HEADER_CHARSET_REGEX = Regex("""charset=([^\s;"']+)""", RegexOption.IGNORE_CASE)
    private val META_CHARSET_REGEX =
        Regex("""<meta[^>]+charset\s*=\s*["']?([^"'>\s;]+)""", RegexOption.IGNORE_CASE)
    private val META_HTTP_EQUIV_CHARSET_REGEX =
        Regex("""<meta[^>]+http-equiv\s*=\s*["']?content-type[^>]*charset=([^"'>\s;]+)""", RegexOption.IGNORE_CASE)

    /**
     * Detect the character encoding of an HTML byte stream following the HTML
     * Living Standard precedence: HTTP `Content-Type` charset, then a `<meta>`
     * charset within the first ~4096 bytes, falling back to UTF-8. This prevents
     * mojibake for Shift_JIS / EUC-JP / ISO-2022-JP pages whose OGP titles would
     * otherwise be persisted corrupted into the post's external embed record.
     */
    private fun detectCharset(bytes: ByteArray, contentType: String?): Charset {
        val headerCharset = contentType?.let { HEADER_CHARSET_REGEX.find(it)?.groupValues?.get(1) }
        val head = String(bytes, 0, minOf(4096, bytes.size), Charsets.US_ASCII)
        val metaCharset = META_CHARSET_REGEX.find(head)?.groupValues?.get(1)
            ?: META_HTTP_EQUIV_CHARSET_REGEX.find(head)?.groupValues?.get(1)
        val name = headerCharset ?: metaCharset
        return runCatching { if (name != null) Charset.forName(name) else Charsets.UTF_8 }
            .getOrDefault(Charsets.UTF_8)
    }

    /** Parse OGP metadata from already-fetched [html]. Returns null on failure. */
    fun parseOgp(url: String, html: String): OgpData? = parse(url, html)

    /**
     * Extract Standard Site AT-URIs from `<link rel="site.standard.*" href="at://...">`
     * tags in [html]. Returns a de-duplicated list (empty if none — i.e. a normal page).
     */
    fun extractStandardSiteUris(html: String): List<String> {
        val uris = LinkedHashSet<String>()
        for (match in LINK_TAG_REGEX.findAll(html)) {
            val tag = match.value
            if (!STANDARD_SITE_REL_REGEX.containsMatchIn(tag)) continue
            STANDARD_SITE_HREF_REGEX.find(tag)?.let { uris.add(it.groupValues[1]) }
        }
        return uris.toList()
    }

    private val LINK_TAG_REGEX = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val STANDARD_SITE_REL_REGEX =
        Regex("""rel=["']site\.standard\.[a-z]+["']""", RegexOption.IGNORE_CASE)
    private val STANDARD_SITE_HREF_REGEX =
        Regex("""href=["'](at://[^"']+)["']""", RegexOption.IGNORE_CASE)

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
