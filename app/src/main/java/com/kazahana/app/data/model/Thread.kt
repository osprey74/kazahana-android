package com.kazahana.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ThreadResponse(
    val thread: JsonElement,
)
