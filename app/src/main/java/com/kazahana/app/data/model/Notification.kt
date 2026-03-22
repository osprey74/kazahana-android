package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NotificationListResponse(
    val notifications: List<NotificationItem> = emptyList(),
    val cursor: String? = null,
    val seenAt: String? = null,
)

@Serializable
data class NotificationItem(
    val uri: String,
    val cid: String,
    val author: ProfileViewBasic,
    val reason: String,       // like, repost, follow, mention, reply, quote
    val reasonSubject: String? = null,  // URI of the subject (e.g. the post that was liked)
    val record: JsonElement,
    val isRead: Boolean,
    val indexedAt: String,
    val labels: List<ContentLabel> = emptyList(),
)

@Serializable
data class UnreadCountResponse(
    val count: Int,
)
