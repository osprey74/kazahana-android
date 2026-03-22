package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedGeneratorView(
    val uri: String,
    val cid: String,
    val did: String,
    val creator: ProfileViewBasic,
    val displayName: String,
    val description: String? = null,
    val avatar: String? = null,
    val likeCount: Int? = null,
    val indexedAt: String,
)

@Serializable
data class GetFeedGeneratorsResponse(
    val feeds: List<FeedGeneratorView> = emptyList(),
)

@Serializable
data class PreferencesResponse(
    val preferences: List<PreferenceItem> = emptyList(),
)

@Serializable
data class PreferenceItem(
    @SerialName("\$type") val type: String? = null,
    val pinned: List<String>? = null,   // savedFeedsPrefV2
    val saved: List<String>? = null,
    val items: List<SavedFeedItem>? = null,
)

@Serializable
data class SavedFeedItem(
    val id: String = "",
    val type: String = "",       // feed, timeline, list
    val value: String = "",      // at:// URI for feed/list, or "following" for timeline
    val pinned: Boolean = false,
)

@Serializable
data class FeedResponse(
    val feed: List<FeedViewPost> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class GetListResponse(
    val list: ListView,
    val cursor: String? = null,
)

@Serializable
data class GetListsResponse(
    val lists: List<ListView> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class ListView(
    val uri: String = "",
    val cid: String = "",
    val name: String = "",
    val purpose: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val indexedAt: String? = null,
)

/**
 * Result of fetching all saved feed items (feeds + lists).
 * Equivalent to iOS getAllSavedFeedItems.
 */
data class AllSavedFeedItems(
    val feeds: List<FeedGeneratorView>,
    val lists: List<ListView>,
)
