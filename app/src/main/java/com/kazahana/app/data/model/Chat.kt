package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConvoListResponse(
    val convos: List<ConvoView> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class ConvoView(
    val id: String,
    val rev: String? = null,
    val members: List<ChatMember> = emptyList(),
    val lastMessage: ChatMessageOrDeleted? = null,
    val unreadCount: Int = 0,
    val muted: Boolean = false,
)

@Serializable
data class ChatMember(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
)

@Serializable
data class ChatReaction(
    val value: String,
    val sender: ChatSender,
)

@Serializable
data class ChatMessageOrDeleted(
    @SerialName("\$type") val type: String? = null,
    val id: String? = null,
    val rev: String? = null,
    val text: String? = null,
    val sender: ChatSender? = null,
    val sentAt: String? = null,
    val reactions: List<ChatReaction>? = null,
)

@Serializable
data class ChatSender(
    val did: String,
)

@Serializable
data class MessageListResponse(
    val messages: List<ChatMessageOrDeleted> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class GetConvoResponse(
    val convo: ConvoView,
)

@Serializable
data class SendMessageResponse(
    val id: String,
    val rev: String? = null,
    val text: String? = null,
    val sender: ChatSender? = null,
    val sentAt: String? = null,
    val reactions: List<ChatReaction>? = null,
)

@Serializable
data class ReactionResponse(
    val id: String,
    val rev: String? = null,
    val text: String? = null,
    val sender: ChatSender? = null,
    val sentAt: String? = null,
    val reactions: List<ChatReaction>? = null,
)
