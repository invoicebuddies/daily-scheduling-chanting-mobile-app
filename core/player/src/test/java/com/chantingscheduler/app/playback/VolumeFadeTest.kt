package com.chantingscheduler.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeFadeTest {

    @Test
    fun `no fade returns full volume`() {
        assertEquals(1f, VolumeFade.volumeAt(0L, 0L))
        assertEquals(1f, VolumeFade.volumeAt(500L, -100L))
    }

    @Test
    fun `fade starts silent`() {
        assertEquals(0f, VolumeFade.volumeAt(0L, 30_000L))
    }

    @Test
    fun `fade ramps linearly`() {
        assertEquals(0.5f, VolumeFade.volumeAt(15_000L, 30_000L), 0.001f)
        assertEquals(0.25f, VolumeFade.volumeAt(1_250L, 5_000L), 0.001f)
    }

    @Test
    fun `fade clamps at full volume after duration`() {
        assertEquals(1f, VolumeFade.volumeAt(30_000L, 30_000L))
        assertEquals(1f, VolumeFade.volumeAt(60_000L, 30_000L))
    }
}
