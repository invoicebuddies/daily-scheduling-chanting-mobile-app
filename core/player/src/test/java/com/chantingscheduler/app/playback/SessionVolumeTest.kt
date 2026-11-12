package com.chantingscheduler.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionVolumeTest {

    @Test
    fun `schedule volume replaces the global ceiling`() {
        assertEquals(0.25f, SessionVolume.resolve(scheduleVolumePercent = 25, globalPercent = 70), 0.001f)
        assertEquals(1.0f, SessionVolume.resolve(scheduleVolumePercent = 100, globalPercent = 70), 0.001f)
    }

    @Test
    fun `null schedule volume follows the global setting`() {
        assertEquals(0.7f, SessionVolume.resolve(scheduleVolumePercent = null, globalPercent = 70), 0.001f)
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals(0.25f, SessionVolume.resolve(scheduleVolumePercent = 5, globalPercent = 70), 0.001f)
        assertEquals(1.0f, SessionVolume.resolve(scheduleVolumePercent = null, globalPercent = 140), 0.001f)
    }
}
