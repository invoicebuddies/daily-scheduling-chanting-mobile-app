package com.chantingscheduler.app.scheduling

import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeRestoreTest {

    @Test
    fun `future snoozes are restored`() {
        val now = 1_000_000L
        val entries = mapOf(1L to now + 60_000L, 2L to now + 300_000L)
        assertEquals(entries, SnoozeRestore.pendingToRestore(entries, now))
    }

    @Test
    fun `past snoozes are dropped`() {
        val now = 1_000_000L
        val entries = mapOf(1L to now - 1_000L, 2L to now - 60_000L)
        assertEquals(emptyMap<Long, Long>(), SnoozeRestore.pendingToRestore(entries, now))
    }

    @Test
    fun `mixed entries keep only the future ones`() {
        val now = 1_000_000L
        val entries = mapOf(1L to now - 1_000L, 2L to now + 60_000L)
        assertEquals(mapOf(2L to now + 60_000L), SnoozeRestore.pendingToRestore(entries, now))
    }

    @Test
    fun `empty store restores nothing`() {
        assertEquals(emptyMap<Long, Long>(), SnoozeRestore.pendingToRestore(emptyMap(), 1_000_000L))
    }
}
