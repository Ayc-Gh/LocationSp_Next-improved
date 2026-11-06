package com.vincenthzr.locationspoofer.xposed.utils

import com.vincenthzr.locationspoofer.utils.PaceProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceProfileRouteIntegrationTest {
    private val start = 42_000L
    private fun config(speed: Double = 3.0) = JSONObject().apply {
        put("is_route_mode", true)
        put("route_points", JSONArray().apply {
            put(JSONObject().put("lat", 0.0).put("lng", 0.0))
            put(JSONObject().put("lat", 1.0).put("lng", 0.0))
        })
        put("speed_m_s", speed)
        put("start_timestamp", start)
        put("pace_variation_sec", 10.0)
        put("fatigue_level", 2)
        put("first_km_bias_sec", -3.0)
        put("fatigue_start_km", 2.0)
    }

    @Test fun `hook output follows the same pace profile across kilometer boundaries`() {
        val cfg = config()
        val profile = PaceProfile.fromConfig(3.0, 10.0, 2, -3.0, 2.0, start)
        for (seconds in listOf(10.0, 400.0, 900.0, 1800.0, 3600.0)) {
            val now = start + (seconds * 1000).toLong()
            val distance = RouteEngine.traveledDistance(cfg, now)
            assertEquals(profile.distanceAt(seconds), distance, 1e-7)
            val output = RouteEngine.calculateCurrentPosition(cfg, now)
            assertEquals(profile.speedAt(seconds).toFloat(), output.speed)
            // Straight northbound route: verify location itself, not only reported speed.
            assertEquals(distance / 111_320.0, output.lat, 1e-5)
        }
    }

    @Test fun `resuming preserves distance beyond the fatigue threshold`() {
        val cfg = config().put("route_distance_offset", 3500.0)
        val profile = PaceProfile.fromConfig(3.0, 10.0, 2, -3.0, 2.0, start)
        assertEquals(3500.0, RouteEngine.traveledDistance(cfg, start), 1e-7)
        val after = RouteEngine.traveledDistance(cfg, start + 60_000L)
        assertEquals(profile.distanceAfter(3500.0, 60.0), after, 1e-7)
        assertTrue(after > 3500.0)
        assertEquals(profile.speedAt(profile.elapsedAt(after)).toFloat(),
            RouteEngine.calculateCurrentPosition(cfg, start + 60_000L).speed)
    }

    @Test fun `new pace model keeps custom speeds above previous caps`() {
        for (speed in listOf(500.25, 2000.0, 20_000.0)) {
            val cfg = config(speed).put("pace_variation_sec", 0.0)
                .put("fatigue_level", 0).put("first_km_bias_sec", 0.0)
            assertEquals(speed * 2.0, RouteEngine.traveledDistance(cfg, start + 2000L), 1e-6)
            assertEquals(speed.toFloat(), RouteEngine.calculateCurrentPosition(cfg, start + 2000L).speed)
        }
    }
}
