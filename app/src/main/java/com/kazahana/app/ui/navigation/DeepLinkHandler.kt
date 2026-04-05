package com.kazahana.app.ui.navigation

import android.content.Intent
import android.net.Uri
import com.kazahana.app.service.PushNotificationService

/**
 * Represents a parsed deep link or share intent that maps to an in-app navigation action.
 */
sealed class DeepLink {
    /** Open a profile by DID or handle. */
    data class Profile(val actor: String) : DeepLink()

    /** Open a thread by AT-URI. */
    data class Post(val atUri: String) : DeepLink()

    /** Open compose screen with pre-filled text. */
    data class Compose(val text: String) : DeepLink()

    /** Open search with a query. */
    data class Search(val query: String) : DeepLink()

    /** Navigate to notifications tab, optionally switching to a target account. */
    data class Notification(val targetDid: String?) : DeepLink()
}

/**
 * Parses an Android Intent into a [DeepLink], or returns null if the intent
 * is not a recognized deep link or share action.
 */
object DeepLinkHandler {

    fun parse(intent: Intent): DeepLink? {
        return when (intent.action) {
            PushNotificationService.ACTION_PUSH_NOTIFICATION -> {
                val targetDid = intent.getStringExtra(PushNotificationService.EXTRA_TARGET_DID)
                DeepLink.Notification(targetDid)
            }
            Intent.ACTION_SEND -> parseShareIntent(intent)
            Intent.ACTION_VIEW -> intent.data?.let { parseViewUri(it) }
            else -> null
        }
    }

    private fun parseShareIntent(intent: Intent): DeepLink? {
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (subject == null && text == null) return null

        // Combine title and URL (e.g. Chrome sends title in EXTRA_SUBJECT, URL in EXTRA_TEXT)
        val combined = when {
            subject != null && text != null && subject != text -> "$subject\n$text"
            text != null -> text
            else -> subject!!
        }
        return DeepLink.Compose(combined)
    }

    private fun parseViewUri(uri: Uri): DeepLink? {
        return when (uri.scheme) {
            "kazahana" -> parseKazahanaUri(uri)
            "https" -> parseBskyAppUri(uri)
            else -> null
        }
    }

    /**
     * Parse kazahana:// scheme URIs.
     *
     * Supported patterns:
     * - kazahana://profile/{did_or_handle}
     * - kazahana://post/{url_encoded_at_uri}
     * - kazahana://compose?text={text}
     * - kazahana://hashtag/{tag}
     */
    private fun parseKazahanaUri(uri: Uri): DeepLink? {
        val pathSegments = uri.pathSegments
        return when (uri.host) {
            "profile" -> {
                val actor = pathSegments.firstOrNull() ?: return null
                DeepLink.Profile(actor)
            }
            "post" -> {
                val encodedUri = pathSegments.firstOrNull() ?: return null
                DeepLink.Post(Uri.decode(encodedUri))
            }
            "compose" -> {
                val text = uri.getQueryParameter("text") ?: ""
                DeepLink.Compose(text)
            }
            "hashtag" -> {
                val tag = pathSegments.firstOrNull() ?: return null
                DeepLink.Search("#$tag")
            }
            else -> null
        }
    }

    /**
     * Parse https://bsky.app URIs.
     *
     * Supported patterns:
     * - https://bsky.app/profile/{handle}
     * - https://bsky.app/profile/{handle}/post/{rkey}
     */
    private fun parseBskyAppUri(uri: Uri): DeepLink? {
        if (uri.host != "bsky.app") return null
        val segments = uri.pathSegments
        if (segments.isEmpty()) return null

        return when (segments.firstOrNull()) {
            "profile" -> {
                val handle = segments.getOrNull(1) ?: return null
                when {
                    // /profile/{handle}/post/{rkey}
                    segments.getOrNull(2) == "post" -> {
                        val rkey = segments.getOrNull(3) ?: return null
                        // Construct AT-URI using handle (AT Protocol resolves it)
                        DeepLink.Post("at://$handle/app.bsky.feed.post/$rkey")
                    }
                    // /profile/{handle}
                    else -> DeepLink.Profile(handle)
                }
            }
            else -> null
        }
    }
}
