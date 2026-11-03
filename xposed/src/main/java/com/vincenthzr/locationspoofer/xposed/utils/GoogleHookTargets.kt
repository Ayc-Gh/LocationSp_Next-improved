package com.vincenthzr.locationspoofer.xposed.utils

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Public SDK types can be interfaces. Hook the actual receiver, including inherited implementations. */
internal object GoogleHookTargets {
    fun methods(type: Class<*>, vararg names: String): List<Method> = type.methods.filter {
        it.name in names && !Modifier.isAbstract(it.modifiers) && !Modifier.isStatic(it.modifiers)
    }.distinct()
}
