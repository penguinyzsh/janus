package org.pysh.janus.hook

import android.util.Log
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Reflection utilities replacing XposedHelpers after migration to libxposed modern API.
 */
object ReflectUtils {

    private const val TAG = "Janus-Reflect"

    private val fieldCache = ConcurrentHashMap<String, Field>()

    private fun findField(clazz: Class<*>, name: String): Field {
        val key = "${clazz.name}#$name"
        return fieldCache.computeIfAbsent(key) {
            var cls: Class<*>? = clazz
            while (cls != null) {
                try {
                    return@computeIfAbsent cls.getDeclaredField(name).apply { isAccessible = true }
                } catch (_: NoSuchFieldException) {
                    cls = cls.superclass
                }
            }
            throw NoSuchFieldException("$name not found in ${clazz.name} hierarchy")
        }
    }

    fun getField(obj: Any, name: String): Any? {
        return findField(obj.javaClass, name).get(obj)
    }

    fun setField(obj: Any, name: String, value: Any?) {
        findField(obj.javaClass, name).set(obj, value)
    }

    fun getStaticField(clazz: Class<*>, name: String): Any? {
        return findField(clazz, name).get(null)
    }

    fun setStaticField(clazz: Class<*>, name: String, value: Any?) {
        findField(clazz, name).set(null, value)
    }

    fun getBooleanField(obj: Any, name: String): Boolean {
        return findField(obj.javaClass, name).getBoolean(obj)
    }

    fun setBooleanField(obj: Any, name: String, value: Boolean) {
        findField(obj.javaClass, name).setBoolean(obj, value)
    }

    fun getIntField(obj: Any, name: String): Int {
        return findField(obj.javaClass, name).getInt(obj)
    }

    fun setIntField(obj: Any, name: String, value: Int) {
        findField(obj.javaClass, name).setInt(obj, value)
    }

    fun getLongField(obj: Any, name: String): Long {
        return findField(obj.javaClass, name).getLong(obj)
    }

    fun getFloatField(obj: Any, name: String): Float {
        return findField(obj.javaClass, name).getFloat(obj)
    }

    fun getDoubleField(obj: Any, name: String): Double {
        return findField(obj.javaClass, name).getDouble(obj)
    }

    fun callMethod(obj: Any, name: String, vararg args: Any?): Any? {
        val candidates = obj.javaClass.declaredMethods.filter { it.name == name && it.parameterCount == args.size }
        if (candidates.isEmpty()) {
            throw NoSuchMethodException("$name(${args.size} args) not found in ${obj.javaClass.name}")
        }
        if (candidates.size > 1) {
            Log.w(TAG, "Multiple methods named $name(${args.size} args) in ${obj.javaClass.name}, using first match")
        }
        val method = candidates.first()
        method.isAccessible = true
        return method.invoke(obj, *args)
    }

    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val candidates = clazz.declaredMethods.filter { it.name == name && it.parameterCount == args.size }
        if (candidates.isEmpty()) {
            throw NoSuchMethodException("$name(${args.size} args) not found in ${clazz.name}")
        }
        if (candidates.size > 1) {
            Log.w(TAG, "Multiple static methods named $name(${args.size} args) in ${clazz.name}, using first match")
        }
        val method = candidates.first()
        method.isAccessible = true
        return method.invoke(null, *args)
    }
}
