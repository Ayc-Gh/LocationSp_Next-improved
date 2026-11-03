package com.vincenthzr.locationspoofer.xposed.hooks.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WifiSimulationDataTest {
    private fun config(wifi: Any? = JSONObject()) = JSONObject().apply {
        put("active", true)
        put("mock_wifi", true)
        if (wifi != null) put("wifi_json", wifi)
    }

    private fun connectedWifi() = JSONObject().apply {
        put("isConnected", true)
        put("connectedWifi", JSONObject().put("ssid", "Test Wi-Fi").put("bssid", "00:11:22:33:44:55"))
        put("nearbyWifi", JSONArray())
    }

    @Test fun emptyRouteEnvironmentUsesRealNetwork() {
        for (wifi in listOf(null, JSONObject(), JSONArray(), JSONObject.NULL)) {
            assertNull(config(wifi).simulatedWifiEnvironment())
            assertNull(config(wifi).simulatedWifiConnection())
        }
    }

    @Test fun explicitDisconnectedEmptySampleUsesRealNetwork() {
        val settings = config(JSONObject().put("isConnected", false)
            .put("connectedWifi", JSONObject.NULL).put("nearbyWifi", JSONArray()))
        assertNull(settings.simulatedWifiEnvironment())
        assertNull(settings.simulatedWifiConnection())
    }

    @Test fun connectedFlagWithoutConnectionDetailsUsesRealNetwork() {
        for (details in listOf(JSONObject.NULL, JSONObject(), "invalid")) {
            val settings = config(JSONObject().put("isConnected", true).put("connectedWifi", details))
            assertNull(settings.simulatedWifiEnvironment())
            assertNull(settings.simulatedWifiConnection())
        }
    }

    @Test fun scanOnlySampleDoesNotDisconnectRealWifi() {
        val wifi = JSONObject().put("isConnected", false).put("connectedWifi", JSONObject.NULL)
            .put("nearbyWifi", JSONArray().put(JSONObject().put("ssid", "Nearby Wi-Fi")))
        val settings = config(wifi)
        assertSame(wifi, settings.simulatedWifiEnvironment())
        assertNull(settings.simulatedWifiConnection())
    }

    @Test fun validConnectionRemainsAvailableForSimulation() {
        val wifi = connectedWifi()
        val settings = config(wifi)
        assertSame(wifi, settings.simulatedWifiEnvironment())
        assertSame(wifi.getJSONObject("connectedWifi"), settings.simulatedWifiConnection())
    }

    @Test fun stoppedOrDisabledSimulationUsesRealNetwork() {
        for (flag in listOf("active", "mock_wifi")) {
            val settings = config(connectedWifi()).put(flag, false)
            assertNull(settings.simulatedWifiEnvironment())
            assertNull(settings.simulatedWifiConnection())
        }
    }

    @Test fun movingFromCoveredPointToEmptyPointDoesNotRetainFakeConnection() {
        val settings = config(connectedWifi())
        assertNotNull(settings.simulatedWifiConnection())
        settings.put("wifi_json", JSONObject())
        assertNull(settings.simulatedWifiEnvironment())
        assertNull(settings.simulatedWifiConnection())
        settings.put("wifi_json", connectedWifi())
        assertNotNull(settings.simulatedWifiConnection())
    }
}
