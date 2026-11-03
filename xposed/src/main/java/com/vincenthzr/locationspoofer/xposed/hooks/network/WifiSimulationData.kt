package com.vincenthzr.locationspoofer.xposed.hooks.network

import org.json.JSONObject

/** Missing environment samples must never be interpreted as a disconnected real network. */
internal fun JSONObject.simulatedWifiEnvironment(): JSONObject? {
    if (!optBoolean("active", false) || !optBoolean("mock_wifi", true)) return null
    val wifi = optJSONObject("wifi_json") ?: return null
    val connected = wifi.optJSONObject("connectedWifi")
    val hasConnection = wifi.optBoolean("isConnected", false) && connected != null && connected.length() > 0
    val hasScanResults = (wifi.optJSONArray("nearbyWifi")?.length() ?: 0) > 0
    return wifi.takeIf { hasConnection || hasScanResults }
}

/** Scan-only samples can replace scans, but cannot replace the device's connection state. */
internal fun JSONObject.simulatedWifiConnection(): JSONObject? {
    val wifi = simulatedWifiEnvironment() ?: return null
    if (!wifi.optBoolean("isConnected", false)) return null
    return wifi.optJSONObject("connectedWifi")?.takeIf { it.length() > 0 }
}
