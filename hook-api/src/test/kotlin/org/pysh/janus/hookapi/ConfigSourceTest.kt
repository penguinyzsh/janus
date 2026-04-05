package org.pysh.janus.hookapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the nested [ConfigSource.Empty] sentinel and demonstrates how a
 * purely in-memory [ConfigSource] can be constructed for future engine tests.
 */
class ConfigSourceTest {

    private class InMemoryConfigSource(
        private val strings: Map<String, String> = emptyMap(),
        private val booleans: Map<String, Boolean> = emptyMap(),
    ) : ConfigSource {
        override fun getString(key: String, defaultValue: String?): String? =
            strings[key] ?: defaultValue

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            booleans[key] ?: defaultValue
    }

    @Test
    fun `empty sentinel returns provided defaults`() {
        assertEquals("fallback", ConfigSource.Empty.getString("missing", "fallback"))
        assertEquals(null, ConfigSource.Empty.getString("missing", null))
        assertTrue(ConfigSource.Empty.getBoolean("missing", true))
        assertFalse(ConfigSource.Empty.getBoolean("missing", false))
    }

    @Test
    fun `in-memory config source honours stored values over defaults`() {
        val src = InMemoryConfigSource(
            strings = mapOf("name" to "janus"),
            booleans = mapOf("enabled" to true),
        )

        assertEquals("janus", src.getString("name", "default"))
        assertEquals("default", src.getString("missing", "default"))
        assertTrue(src.getBoolean("enabled", false))
        assertFalse(src.getBoolean("missing", false))
    }
}
