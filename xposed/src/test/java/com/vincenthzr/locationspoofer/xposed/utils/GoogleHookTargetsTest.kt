package com.vincenthzr.locationspoofer.xposed.utils

import org.junit.Assert.*
import org.junit.Test

class GoogleHookTargetsTest {
    interface ModernClient {
        fun requestLocationUpdates(callback: Any)
        fun getLastLocation(): Any?
    }
    abstract class ClientBase : ModernClient {
        override fun getLastLocation(): Any? = null
        fun getLastLocation(request: String): Any? = request
    }
    class RealClient : ClientBase() {
        override fun requestLocationUpdates(callback: Any) {}
    }
    open class CallbackBase { open fun onLocationResult(result: Any) {} }
    class InheritedCallback : CallbackBase()

    @Test fun `an interface provides no executable hook targets`() {
        assertTrue(GoogleHookTargets.methods(ModernClient::class.java, "requestLocationUpdates").isEmpty())
    }

    @Test fun `actual receiver discovers concrete and inherited methods`() {
        val methods = GoogleHookTargets.methods(RealClient::class.java, "requestLocationUpdates", "getLastLocation")
        assertEquals(3, methods.size)
        assertEquals(2, methods.count { it.declaringClass == ClientBase::class.java })
        assertTrue(methods.any { it.name == "requestLocationUpdates" && it.declaringClass == RealClient::class.java })
    }

    @Test fun `callbacks inherited from a parent are still hookable`() {
        val method = GoogleHookTargets.methods(InheritedCallback::class.java, "onLocationResult").single()
        assertEquals(CallbackBase::class.java, method.declaringClass)
    }

    @Test fun `unrelated client methods are excluded`() {
        assertTrue(GoogleHookTargets.methods(RealClient::class.java, "getCurrentLocation").isEmpty())
    }
}
