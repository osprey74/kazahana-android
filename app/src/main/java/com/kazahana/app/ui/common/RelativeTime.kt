package com.kazahana.app.ui.common

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun relativeTime(isoString: String): String {
    return try {
        val instant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(isoString))
        val now = Instant.now()
        val duration = Duration.between(instant, now)

        val seconds = duration.seconds
        when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            seconds < 604800 -> "${seconds / 86400}d"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d")
                formatter.format(instant.atZone(java.time.ZoneId.systemDefault()))
            }
        }
    } catch (_: DateTimeParseException) {
        isoString
    }
}
