package com.vincenthzr.locationspoofer.xposed.utils

import java.util.IdentityHashMap

/** Keep registrations distinct even when callbacks override equals; reject cancelled queued work. */
internal class CallbackSubscriptions<T> {
    class Entry<T>(val callback: Any, val delivery: T)
    private val entries = IdentityHashMap<Any, Entry<T>>()

    @Synchronized fun register(callback: Any, delivery: T): Entry<T> =
        Entry(callback, delivery).also { entries[callback] = it }

    @Synchronized fun remove(callback: Any) { entries.remove(callback) }
    @Synchronized fun contains(callback: Any): Boolean = entries.containsKey(callback)
    @Synchronized fun isCurrent(entry: Entry<T>): Boolean = entries[entry.callback] === entry
    @Synchronized fun snapshot(): List<Entry<T>> = entries.values.toList()
    @Synchronized fun clear() { entries.clear() }
}
