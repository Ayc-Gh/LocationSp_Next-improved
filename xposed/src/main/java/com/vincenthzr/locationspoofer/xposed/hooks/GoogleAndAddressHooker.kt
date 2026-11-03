@file:Suppress(
    "UNUSED_PARAMETER",
    "UNUSED_VARIABLE",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "DEPRECATION",
    "NAME_SHADOWING",
    "FunctionName",
    "PrivatePropertyName",
    "SpellCheckingInspection",
    "RedundantUnitReturnType",
    "RemoveRedundantQualifierName",
    "OPT_IN_USAGE",
    "unused",
    "UnusedImport"
)

package com.vincenthzr.locationspoofer.xposed.hooks

import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.*
import com.vincenthzr.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

/**
 * 在地图 SDK 的 Location 对象中注入地址/城市/省份等通用模拟值。
 * 当 WiFi/基站被模拟时，地图 SDK 的云端逆地理编码通常会失败，导致这些字段为空。
 * 返回 null 会导致目标 App (如钉钉、微信) 报“获取位置失败”。
 */
internal fun LocationHooker.hookAddressFields(clazz: Class<*>, classLoader: ClassLoader) {
    val stringMethods = arrayOf(
        "getCity",
        "getProvince",
        "getDistrict",
        "getAddress",
        "getAddrStr",
        "getCountry",
        "getNation",
        "getStreet",
        "getStreetNum",
        "getStreetNumber",
        "getStreetNo",
        "getCityCode",
        "getAdCode",
        "getPoiName",
        "getAoiName",
        "getTown",
        "getVillage",
        "getLocationDescribe"
    )
    for (methodName in stringMethods) {
        try {
            XposedHelpers.hookAllMethods(clazz, methodName) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                if (method !is Method) return@hookAllMethods result
                if (method.returnType != String::class.java) return@hookAllMethods result

                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val res = result as? String
                    if (res.isNullOrEmpty() || res.contains("Unknown", ignoreCase = true)) {
                        result = when (method.name) {
                            "getCity" -> cachedCity
                            "getProvince" -> cachedProvince
                            "getDistrict" -> cachedDistrict
                            "getAddress", "getAddrStr" -> cachedAddress
                            "getCountry", "getNation" -> cachedCountry
                            "getStreet" -> cachedStreet
                            "getStreetNum", "getStreetNumber", "getStreetNo" -> cachedStreetNum
                            "getCityCode" -> ""
                            "getAdCode" -> ""
                            "getPoiName" -> cachedPoiName
                            "getAoiName" -> cachedPoiName
                            "getTown" -> ""
                            "getVillage" -> ""
                            "getLocationDescribe" -> "在${cachedPoiName}附近"
                            else -> ""
                        }
                    }
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) { /* ignore */
        }
    }
}

/** Delivery context belongs to a registration, not to a callback class. */
internal class GoogleDelivery(val executor: java.util.concurrent.Executor, val listener: Boolean) {
    val delivered = java.util.concurrent.atomic.AtomicBoolean()
    @Volatile var lastErrorAt = 0L
}

internal fun LocationHooker.hookGoogleFusedLocation(classLoader: ClassLoader) {
    val clientType = XposedHelpers.findClassIfExists(
        "com.google.android.gms.location.FusedLocationProviderClient", classLoader
    ) ?: return

    fun discover(receiver: Any?) {
        if (receiver != null && clientType.isInstance(receiver)) installGoogleClient(receiver.javaClass)
    }

    // Modern Play services exposes an interface. Its abstract methods have no hookable body.
    installGoogleClient(clientType)
    val services = XposedHelpers.findClassIfExists("com.google.android.gms.location.LocationServices", classLoader)
    if (services != null && hookedGoogleDiscoveryClasses.add(services)) {
        XposedHelpers.hookAllMethods(services, "getFusedLocationProviderClient") { chain, _ ->
            chain.proceed(chain.args.toTypedArray()).also(::discover)
        }
        runCatching { XposedHelpers.getStaticObjectField(services, "FusedLocationApi") }
            .getOrNull()?.let { installGoogleClient(it.javaClass) }
    }
    // Also covers clients constructed directly instead of through LocationServices.
    val googleApi = XposedHelpers.findClassIfExists("com.google.android.gms.common.api.GoogleApi", classLoader)
    if (googleApi != null && hookedGoogleDiscoveryClasses.add(googleApi)) {
        XposedHelpers.hookAllConstructors(googleApi) { chain, _ ->
            chain.proceed(chain.args.toTypedArray()).also { discover(chain.thisObject) }
        }
    }
    val availability = XposedHelpers.findClassIfExists("com.google.android.gms.location.LocationAvailability", classLoader)
    if (availability != null && hookedGoogleDiscoveryClasses.add(availability)) {
        XposedHelpers.hookAllMethods(availability, "isLocationAvailable") { chain, _ ->
            if (createSimulatedLocation() != null) true else chain.proceed(chain.args.toTypedArray())
        }
    }
}

private fun LocationHooker.installGoogleClient(type: Class<*>) {
    for (method in GoogleHookTargets.methods(type, "requestLocationUpdates", "removeLocationUpdates", "getLastLocation", "getCurrentLocation")) {
        if (!hookedGoogleMembers.add(method)) continue
        try {
            XposedHelpers.module.hook(method).intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                    if (method.name == "getLastLocation" || method.name == "getCurrentLocation") {
                        val cancelled = chain.args.filterNotNull().any { arg ->
                            runCatching { arg.javaClass.getMethod("isCancellationRequested").invoke(arg) == true }.getOrDefault(false)
                        }
                        if (!cancelled) {
                            val fix = createSimulatedLocation()
                            if (fix != null) {
                                // The deprecated FusedLocationProviderApi returns Location, not Task.
                                if (method.returnType == android.location.Location::class.java) return fix
                                val task = runCatching {
                                    if (method.returnType.name != "com.google.android.gms.tasks.Task") return@runCatching null
                                    val tasks = type.classLoader!!.loadClass("com.google.android.gms.tasks.Tasks")
                                    tasks.getMethod("forResult", Any::class.java).invoke(null, fix)
                                }.getOrNull()
                                if (task != null) return task
                            }
                        }
                    }
                    if (method.name == "removeLocationUpdates") {
                        chain.args.filterNotNull().forEach { callback ->
                            googleSubscriptions.remove(callback)
                            capturedFusedLocationCallbacks.removeAll { it === callback }
                        }
                    }
                    val result = chain.proceed(chain.args.toTypedArray())
                    if (method.name == "requestLocationUpdates") captureGoogleRegistration(chain.args)
                    return result
                }
            })
            XposedBridge.log("[GoogleLocation] Hooked concrete client ${method.declaringClass.name}#${method.name}")
        } catch (error: Throwable) {
            hookedGoogleMembers.remove(method)
            XposedBridge.log(error)
        }
    }
}

private fun LocationHooker.captureGoogleRegistration(args: List<Any?>) {
    val callback = args.firstOrNull { arg -> arg != null && (
        LocationHooker.hasTypeByName(arg.javaClass, "com.google.android.gms.location.LocationCallback") ||
        LocationHooker.hasTypeByName(arg.javaClass, "com.google.android.gms.location.LocationListener"))
    } ?: return
    val listener = LocationHooker.hasTypeByName(callback.javaClass, "com.google.android.gms.location.LocationListener")
    val looper = args.filterIsInstance<android.os.Looper>().firstOrNull()
        ?: android.os.Looper.myLooper() ?: android.os.Looper.getMainLooper()
    val handler = android.os.Handler(looper)
    val executor = args.filterIsInstance<java.util.concurrent.Executor>().firstOrNull()
        ?: java.util.concurrent.Executor { action -> handler.post(action) }
    googleSubscriptions.register(callback, GoogleDelivery(executor, listener))
    if (capturedFusedLocationCallbacks.none { it === callback }) capturedFusedLocationCallbacks.add(callback)
    val callbackName = if (listener) "onLocationChanged" else "onLocationResult"
    for (method in GoogleHookTargets.methods(callback.javaClass, callbackName)) {
        if (!hookedGoogleMembers.add(method)) continue
        try {
            XposedHelpers.module.hook(method).intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                    val values = chain.args.toTypedArray()
                    if (googleSubscriptions.contains(chain.thisObject ?: callback)) {
                        val fix = createSimulatedLocation()
                        if (fix != null && values.isNotEmpty()) {
                            values[0] = if (listener) fix else googleLocationResult(callback.javaClass.classLoader!!, fix)
                        }
                    }
                    return chain.proceed(values)
                }
            })
        } catch (error: Throwable) {
            hookedGoogleMembers.remove(method)
            XposedBridge.log(error)
        }
    }
    XposedBridge.log("[GoogleLocation] Registered ${if (listener) "listener" else "callback"}; subscriptions=${googleSubscriptions.snapshot().size}")
}

private fun googleLocationResult(loader: ClassLoader, location: android.location.Location): Any {
    val result = loader.loadClass("com.google.android.gms.location.LocationResult")
    return result.getMethod("create", List::class.java).invoke(null, listOf(location))
}

internal fun LocationHooker.dispatchGoogleLocationCallbacks(config: JSONObject) {
    if (!config.optBoolean("active", false)) return
    // Hot reload can restore a callback from an older generation without its delivery metadata.
    for (callback in capturedFusedLocationCallbacks) {
        if (!googleSubscriptions.contains(callback)) captureGoogleRegistration(listOf(callback))
    }
    for (registration in googleSubscriptions.snapshot()) {
        val delivery = registration.delivery
        try {
            delivery.executor.execute {
                if (!googleSubscriptions.isCurrent(registration)) return@execute
                val fix = createSimulatedLocation() ?: return@execute
                try {
                    val callback = registration.callback
                    val payload = if (delivery.listener) fix else googleLocationResult(callback.javaClass.classLoader!!, fix)
                    XposedHelpers.callMethod(callback, if (delivery.listener) "onLocationChanged" else "onLocationResult", payload)
                    if (delivery.delivered.compareAndSet(false, true)) XposedBridge.log("[GoogleLocation] Continuous delivery started")
                } catch (error: Throwable) {
                    val now = System.currentTimeMillis()
                    if (now - delivery.lastErrorAt > 10_000L) {
                        delivery.lastErrorAt = now
                        XposedBridge.log(error)
                    }
                }
            }
        } catch (error: Throwable) {
            val now = System.currentTimeMillis()
            if (now - delivery.lastErrorAt > 10_000L) {
                delivery.lastErrorAt = now
                XposedBridge.log(error)
            }
        }
    }
}
