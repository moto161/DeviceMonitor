package com.devicemonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    @Test
    fun `formatBytes - GB`() {
        val result = (2L * 1024 * 1024 * 1024).formatBytes()
        assertEquals("2.0 GB", result)
    }

    @Test
    fun `formatBytes - MB`() {
        val result = (500L * 1024 * 1024).formatBytes()
        assertEquals("500.0 MB", result)
    }

    @Test
    fun `formatBytes - KB`() {
        val result = 500L.formatBytes()
        assertEquals("0 KB", result)
    }

    @Test
    fun `formatUptime - days`() {
        val result = (2L * 86400 + 5 * 3600).formatUptime()
        assertEquals("2天5小时", result)
    }

    @Test
    fun `formatUptime - hours`() {
        val result = (5L * 3600 + 30 * 60).formatUptime()
        assertEquals("5小时30分", result)
    }

    @Test
    fun `formatUptime - minutes`() {
        val result = 30L * 60.formatUptime()
        assertEquals("30分钟", result)
    }

    @Test
    fun `formatTime - returns non-empty`() {
        val result = System.currentTimeMillis().formatTime()
        assert(result.isNotEmpty())
        assert(result.contains("-"))
        assert(result.contains(":"))
    }
}
