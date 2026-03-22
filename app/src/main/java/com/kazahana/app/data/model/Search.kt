package com.kazahana.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchPostsResponse(
    val posts: List<PostView> = emptyList(),
    val cursor: String? = null,
    val hitsTotal: Int? = null,
)

@Serializable
data class SearchActorsResponse(
    val actors: List<ProfileViewDetailed> = emptyList(),
    val cursor: String? = null,
)
