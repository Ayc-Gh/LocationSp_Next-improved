package com.vincenthzr.locationspoofer.xposed.utils

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class CallbackSubscriptionsTest {
    private class EqualCallback {
        override fun equals(other: Any?) = other is EqualCallback
        override fun hashCode() = 1
    }

    @Test fun `equal callbacks remain independent subscriptions`() {
        val subscriptions = CallbackSubscriptions<String>()
        val first = EqualCallback()
        val second = EqualCallback()
        subscriptions.register(first, "first")
        subscriptions.register(second, "second")
        subscriptions.remove(first)
        assertFalse(subscriptions.contains(first))
        assertTrue(subscriptions.contains(second))
        assertEquals("second", subscriptions.snapshot().single().delivery)
    }

    @Test fun `reregistering a callback invalidates already queued delivery`() {
        val subscriptions = CallbackSubscriptions<Executor>()
        val pending = mutableListOf<Runnable>()
        val executor = Executor { pending += it }
        val callback = Any()
        val previous = subscriptions.register(callback, executor)
        var deliveries = 0
        previous.delivery.execute { if (subscriptions.isCurrent(previous)) deliveries++ }
        val replacement = subscriptions.register(callback, executor)
        pending.forEach { it.run() }
        assertEquals(0, deliveries)
        assertTrue(subscriptions.isCurrent(replacement))
        assertEquals(1, subscriptions.snapshot().size)
    }

    @Test fun `cancelled callbacks receive no queued location`() {
        val subscriptions = CallbackSubscriptions<String>()
        val callback = Any()
        val entry = subscriptions.register(callback, "delivery")
        subscriptions.remove(callback)
        assertFalse(subscriptions.isCurrent(entry))
        assertTrue(subscriptions.snapshot().isEmpty())
    }

    @Test fun `snapshots remain safe while registrations change`() {
        val subscriptions = CallbackSubscriptions<String>()
        val callback = Any()
        val first = subscriptions.register(callback, "first")
        val snapshot = subscriptions.snapshot()
        subscriptions.clear()
        subscriptions.register(callback, "second")
        assertEquals("first", snapshot.single().delivery)
        assertFalse(subscriptions.isCurrent(first))
        assertEquals("second", subscriptions.snapshot().single().delivery)
    }
}
