package org.pysh.janus.hook

import java.lang.reflect.Field

/**
 * Reflection utilities replacing XposedHelpers after migration to libxposed modern API.
 */
object ReflectUtils {

    private val fieldCache = java.util.concurrent.ConcurrentHashMap<String, Field>()

    private fun findField(clazz: Class<*>, name: String): Field {
        val key = "${clazz.name}#$name"
        return fieldCache.getOrPut(key) {
            var cls: Class<*>? = clazz
            while (cls != null) {
                try {
                    return@getOrPut cls.getDeclaredField(name).apply { isAccessible = true }
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
        val method = obj.javaClass.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
            ?: throw NoSuchMethodException("$name(${args.size} args) not found in ${obj.javaClass.name}")
        method.isAccessible = true
        return method.invoke(obj, *args)
    }

    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val method = clazz.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
            ?: throw NoSuchMethodException("$name(${args.size} args) not found in ${clazz.name}")
        method.isAccessible = true
        return method.invoke(null, *args)
    }
}
