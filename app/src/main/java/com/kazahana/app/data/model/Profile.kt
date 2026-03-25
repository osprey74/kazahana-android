package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileViewDetailed(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val followsCount: Int? = null,
    val followersCount: Int? = null,
    val postsCount: Int? = null,
    val indexedAt: String? = null,
    val viewer: ProfileViewerState? = null,
    val labels: List<ContentLabel> = emptyList(),
    val createdAt: String? = null,
    val pinnedPost: PinnedPost? = null,
)

@Serializable
data class PinnedPost(
    val uri: String,
)

@Serializable
data class ProfileViewerState(
    val muted: Boolean? = null,
    val blockedBy: Boolean? = null,
    val blocking: String? = null,
    val following: String? = null,
    val followedBy: String? = null,
)

@Serializable
data class AuthorFeedResponse(
    val feed: List<FeedViewPost> = emptyList(),
    val cursor: String? = null,
)
