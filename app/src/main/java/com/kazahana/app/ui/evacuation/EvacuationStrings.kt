package com.kazahana.app.ui.evacuation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.kazahana.app.R
import com.kazahana.app.data.model.Hazard

/** 位置情報権限（fine/coarse いずれか）を保持しているか。 */
fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}

/** 災害種別の表示名リソース ID。 */
@StringRes
fun hazardLabelRes(hazard: Hazard): Int = when (hazard) {
    Hazard.FLOOD -> R.string.evacuation_hazard_flood
    Hazard.LANDSLIDE -> R.string.evacuation_hazard_landslide
    Hazard.STORM_SURGE -> R.string.evacuation_hazard_storm_surge
    Hazard.EARTHQUAKE -> R.string.evacuation_hazard_earthquake
    Hazard.TSUNAMI -> R.string.evacuation_hazard_tsunami
    Hazard.FIRE -> R.string.evacuation_hazard_fire
    Hazard.INLAND_FLOOD -> R.string.evacuation_hazard_inland_flood
    Hazard.VOLCANO -> R.string.evacuation_hazard_volcano
}

/** メートル距離を表示用文字列に整形。 */
fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format(java.util.Locale.US, "%.1f km", meters / 1000)
    } else {
        String.format(java.util.Locale.US, "%.0f m", meters)
    }
}
