package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bot Definition JSON published by BSAF bot developers. */
@Serializable
data class BsafBotDefinition(
    @SerialName("bsaf_schema") val bsafSchema: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("self_url") val selfUrl: String,
    val bot: BsafBotInfo,
    val filters: List<BsafFilterDef> = emptyList(),
)

@Serializable
data class BsafBotInfo(
    val handle: String,
    val did: String,
    val name: String,
    val description: String = "",
    val source: String = "",
    @SerialName("source_url") val sourceUrl: String = "",
)

@Serializable
data class BsafFilterDef(
    val tag: String,
    val label: String,
    val options: List<BsafFilterOption> = emptyList(),
)

@Serializable
data class BsafFilterOption(
    val value: String,
    val label: String,
)

/** Persisted registered bot with user's filter selections. */
@Serializable
data class BsafRegisteredBot(
    val did: String,
    val handle: String,
    val name: String,
    val description: String = "",
    val source: String = "",
    val sourceUrl: String = "",
    val selfUrl: String,
    val updatedAt: String,
    val lastCheckedAt: String = "",
    val filters: List<BsafRegisteredFilter> = emptyList(),
)

@Serializable
data class BsafRegisteredFilter(
    val tag: String,
    val label: String,
    val options: List<BsafFilterOption> = emptyList(),
    val enabledValues: List<String> = emptyList(),
)

/** Parsed BSAF tags from a post. */
data class BsafParsedTags(
    val version: String,
    val type: String = "",
    val value: String = "",
    val time: String = "",
    val target: String = "",
    val source: String = "",
)

/** Duplicate tracking info. */
data class BsafDuplicateInfo(
    val duplicateUris: List<String> = emptyList(),
    val duplicateHandles: List<String> = emptyList(),
)
