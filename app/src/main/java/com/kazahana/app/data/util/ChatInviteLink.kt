package com.kazahana.app.data.util

import android.net.Uri

/**
 * Group chat invite links: `https://bsky.app/chat/{code}` (or a relative `/chat/{code}`),
 * where the code is 7-10 alphanumeric characters. Mirrors social-app's CHAT_INVITE_CODE_REGEX.
 */
object ChatInviteLink {
    private val PATH_REGEX = Regex("^/chat/([a-zA-Z0-9]{7,10})$")

    /** Extract the invite code from a chat invite URL, or null if it isn't one. */
    fun extractCode(url: String): String? {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        val host = uri.host
        if (host != null && host != "bsky.app") return null
        val path = uri.path ?: return null
        return PATH_REGEX.find(path)?.groupValues?.getOrNull(1)
    }
}
