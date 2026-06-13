package com.kazahana.app.data.model

import com.kazahana.app.data.AppJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

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
    val status: String? = null,
    val opened: Boolean? = null,
    // chat.bsky.convo.defs#convoView.kind — union of directConvo | groupConvo.
    val kind: JsonElement? = null,
) {
    /** Group metadata when this convo is a group (kind == #groupConvo), else null. */
    val groupInfo: GroupConvo?
        get() = kind?.takeIf { it.typeName?.endsWith("#groupConvo") == true }
            ?.let { runCatching { AppJson.decodeFromJsonElement<GroupConvo>(it) }.getOrNull() }

    val isGroup: Boolean get() = groupInfo != null
}

/** chat.bsky.convo.defs#groupConvo */
@Serializable
data class GroupConvo(
    val createdAt: String? = null,
    val name: String = "",
    // unlocked | locked | locked-permanently
    val lockStatus: String? = null,
    val lockStatusModerationOverride: Boolean = false,
    val memberCount: Int = 0,
    val memberLimit: Int = 0,
    val joinLink: JoinLinkView? = null,
    val joinRequestCount: Int? = null,
    val unreadJoinRequestCount: Int? = null,
) {
    val isLocked: Boolean get() = lockStatus == "locked" || lockStatus == "locked-permanently"
}

/** chat.bsky.group.defs#joinLinkView */
@Serializable
data class JoinLinkView(
    val code: String = "",
    // enabled | disabled
    val enabledStatus: String? = null,
    val requireApproval: Boolean = false,
    // anyone | followedByOwner
    val joinRule: String? = null,
    val createdAt: String? = null,
) {
    val isEnabled: Boolean get() = enabledStatus == "enabled"
}

@Serializable
data class ChatMember(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
    // profileViewBasic.kind — union whose #groupConvoMember member carries `role`.
    val kind: JsonElement? = null,
) {
    /** Member role within a group: "owner" | "standard", or null outside groups. */
    val role: String?
        get() = (kind as? JsonObject)?.get("role")?.jsonPrimitive?.contentOrNull

    val isOwner: Boolean get() = role == "owner"
}

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
    val facets: List<Facet> = emptyList(),
    // messageView.embed — union of app.bsky.embed.record | chat.bsky.embed.joinLink#view.
    val embed: JsonElement? = null,
    // systemMessageView.data — union of the systemMessageData* types.
    val data: JsonElement? = null,
) {
    val isDeleted: Boolean get() = type?.endsWith("#deletedMessageView") == true
    val isSystem: Boolean get() = type?.endsWith("#systemMessageView") == true || data != null

    /** Decoded system message payload (member join/leave, lock, edit group, etc.). */
    val systemData: SystemMessageData?
        get() = data?.let { runCatching { AppJson.decodeFromJsonElement<SystemMessageData>(it) }.getOrNull() }

    /** Decoded join-link embed when the message carries one, else null. */
    val joinLinkEmbed: JoinLinkEmbedView?
        get() = embed?.takeIf { it.typeName?.contains("embed.joinLink") == true }
            ?.let { runCatching { AppJson.decodeFromJsonElement<JoinLinkEmbedView>(it) }.getOrNull() }
}

@Serializable
data class ChatSender(
    val did: String,
)

/**
 * Flattened model of chat.bsky.convo.defs#systemMessageData* union members.
 * The concrete kind is identified by [type]; only the fields relevant to that
 * kind are populated. Robust to unknown members via ignoreUnknownKeys.
 */
@Serializable
data class SystemMessageData(
    @SerialName("\$type") val type: String? = null,
    val member: SystemReferredUser? = null,
    val addedBy: SystemReferredUser? = null,
    val removedBy: SystemReferredUser? = null,
    val approvedBy: SystemReferredUser? = null,
    val lockedBy: SystemReferredUser? = null,
    val unlockedBy: SystemReferredUser? = null,
    val role: String? = null,
    val oldName: String? = null,
    val newName: String? = null,
)

/** chat.bsky.convo.defs#systemMessageReferredUser */
@Serializable
data class SystemReferredUser(
    val did: String,
    val handle: String? = null,
    val displayName: String? = null,
)

/** chat.bsky.embed.joinLink#view */
@Serializable
data class JoinLinkEmbedView(
    @SerialName("\$type") val type: String? = null,
    val joinLinkPreview: JsonElement? = null,
) {
    private val previewType: String? get() = joinLinkPreview?.typeName
    val isDisabled: Boolean get() = previewType?.endsWith("#disabledJoinLinkPreviewView") == true
    val isInvalid: Boolean get() = previewType?.endsWith("#invalidJoinLinkPreviewView") == true

    /** Full preview data for an active link, else null (disabled/invalid links carry only a code). */
    val preview: JoinLinkPreview?
        get() = joinLinkPreview?.let { runCatching { AppJson.decodeFromJsonElement<JoinLinkPreview>(it) }.getOrNull() }
}

/** chat.bsky.group.defs#joinLinkPreviewView (disabled/invalid variants populate only [code]). */
@Serializable
data class JoinLinkPreview(
    @SerialName("\$type") val type: String? = null,
    val convoId: String? = null,
    val code: String = "",
    val name: String? = null,
    val owner: ChatMember? = null,
    val memberCount: Int = 0,
    val memberLimit: Int = 0,
    val requireApproval: Boolean = false,
    val joinRule: String? = null,
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

@Serializable
data class RequestJoinResponse(
    // joined | pending
    val status: String,
    val convo: ConvoView? = null,
)

@Serializable
data class JoinLinkResponse(
    val joinLink: JoinLinkView? = null,
)

@Serializable
data class JoinRequestsResponse(
    val requests: List<JoinRequestView> = emptyList(),
    val cursor: String? = null,
)

/** chat.bsky.group.defs#joinRequestView (owner's view of a pending request). */
@Serializable
data class JoinRequestView(
    val convoId: String,
    val requestedBy: ChatMember,
    val requestedAt: String? = null,
)

@Serializable
data class JoinLinkPreviewsResponse(
    val joinLinkPreviews: List<JsonElement> = emptyList(),
)

/** Interpreted state of a single join-link preview (active / disabled / invalid). */
sealed class JoinLinkPreviewState {
    data class Active(val preview: JoinLinkPreview) : JoinLinkPreviewState()
    data object Disabled : JoinLinkPreviewState()
    data object Invalid : JoinLinkPreviewState()
}

fun JsonElement.toJoinLinkPreviewState(): JoinLinkPreviewState {
    val t = typeName
    return when {
        t?.endsWith("#disabledJoinLinkPreviewView") == true -> JoinLinkPreviewState.Disabled
        t?.endsWith("#invalidJoinLinkPreviewView") == true -> JoinLinkPreviewState.Invalid
        else -> runCatching { AppJson.decodeFromJsonElement<JoinLinkPreview>(this) }
            .getOrNull()
            ?.takeIf { it.name != null }
            ?.let { JoinLinkPreviewState.Active(it) }
            ?: JoinLinkPreviewState.Invalid
    }
}

/** `$type` discriminator of a JSON union member, or null if not an object. */
private val JsonElement.typeName: String?
    get() = (this as? JsonObject)?.get("\$type")?.jsonPrimitive?.contentOrNull
