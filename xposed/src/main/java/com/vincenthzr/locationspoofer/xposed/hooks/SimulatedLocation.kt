package com.vincenthzr.locationspoofer.xposed.hooks

import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import org.json.JSONObject

/** Set real fields and their presence bits together; never invent hasX values while unparcelling. */
internal fun LocationHooker.createSimulatedLocation(
    provider: String = LocationManager.GPS_PROVIDER,
    config: JSONObject? = readConfig(),
): Location? {
    if (config == null || !config.optBoolean("active", false) ||
        currentPackageName.substringBefore(':') == "com.vincenthzr.locationspoofer") return null
    val coordinateSystem = if (provider.equals("baidu", true)) "BD-09" else "WGS-84"
    val motion = getCurrentSpoofedMotion(coordinateSystem) ?: return null
    return Location(provider.ifBlank { LocationManager.GPS_PROVIDER }).apply {
        latitude = motion.lat
        longitude = motion.lng
        accuracy = if (motion.speed <= 0.05f) 2.5f else getJitteredAccuracy()
        speed = motion.speed
        bearing = motion.bearing
        altitude = RouteEngine.realisticAltitude(config)
        speedAccuracyMetersPerSecond = 0.1f
        bearingAccuracyDegrees = 1.5f
        verticalAccuracyMeters = 1.2f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        extras = Bundle().apply {
            val count = config.optInt("satellite_count", 20)
            putInt("satellites", count)
            putInt("satellites_in_view", count)
            putInt("satellites_used_in_fix", count)
            putInt("satellites_visible", count)
            putBoolean("mockLocation", false)
        }
    }
}
