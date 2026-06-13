package com.kazahana.app.ui.messages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kazahana.app.R
import com.kazahana.app.data.model.ChatMember
import com.kazahana.app.data.model.SystemMessageData
import com.kazahana.app.data.model.SystemReferredUser

/**
 * Localized human-readable text for a group system message
 * (chat.bsky.convo.defs#systemMessageData*). Resolves referred-user DIDs against
 * the convo [members] for display names, falling back to the name embedded in the
 * referred user, then to an empty string.
 */
@Composable
fun systemMessageText(data: SystemMessageData, members: List<ChatMember>): String {
    fun nameOf(user: SystemReferredUser?): String {
        if (user == null) return ""
        members.firstOrNull { it.did == user.did }?.let { return it.displayName ?: it.handle }
        return user.displayName ?: user.handle ?: ""
    }

    val t = data.type ?: ""
    return when {
        t.endsWith("#systemMessageDataAddMember") ->
            stringResource(R.string.chat_system_add_member, nameOf(data.addedBy), nameOf(data.member))
        t.endsWith("#systemMessageDataRemoveMember") ->
            stringResource(R.string.chat_system_remove_member, nameOf(data.removedBy), nameOf(data.member))
        t.endsWith("#systemMessageDataMemberJoin") ->
            stringResource(R.string.chat_system_member_join, nameOf(data.member))
        t.endsWith("#systemMessageDataMemberLeave") ->
            stringResource(R.string.chat_system_member_leave, nameOf(data.member))
        t.endsWith("#systemMessageDataLockConvo") ->
            stringResource(R.string.chat_system_lock, nameOf(data.lockedBy))
        t.endsWith("#systemMessageDataUnlockConvo") ->
            stringResource(R.string.chat_system_unlock, nameOf(data.unlockedBy))
        t.endsWith("#systemMessageDataLockConvoPermanently") ->
            stringResource(R.string.chat_system_lock_permanent)
        t.endsWith("#systemMessageDataEditGroup") -> {
            val newName = data.newName
            if (!newName.isNullOrBlank()) stringResource(R.string.chat_system_edit_group_named, newName)
            else stringResource(R.string.chat_system_edit_group)
        }
        t.endsWith("#systemMessageDataCreateJoinLink") ->
            stringResource(R.string.chat_system_create_join_link)
        t.endsWith("#systemMessageDataEditJoinLink") ->
            stringResource(R.string.chat_system_edit_join_link)
        t.endsWith("#systemMessageDataEnableJoinLink") ->
            stringResource(R.string.chat_system_enable_join_link)
        t.endsWith("#systemMessageDataDisableJoinLink") ->
            stringResource(R.string.chat_system_disable_join_link)
        else -> stringResource(R.string.chat_system_generic)
    }
}
