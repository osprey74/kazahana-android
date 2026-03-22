package com.kazahana.app.data

import kotlinx.serialization.json.Json

/** Application-wide shared [Json] instance. */
val AppJson = Json { ignoreUnknownKeys = true }
