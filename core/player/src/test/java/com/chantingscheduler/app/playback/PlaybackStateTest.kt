package com.chantingscheduler.app.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {

    @After
    fun reset() {
        PlaybackState.clear()
    }

    @Test
    fun `clearPlaying keeps a published ended session`() {
        val ended = EndedSession(
            scheduleId = 7L,
            logId = 42L,
            label = "Morning",
            startedAtMillis = 1000L,
            endedAtMillis = 2000L,
        )
        PlaybackState.update(label = "Morning", currentTrack = 1, totalTracks = 1, scheduleId = 7L)
        PlaybackState.publishEnded(ended)

        // Service teardown must not swallow the ended session: the ringing
        // page consumes it to show the complete state.
        PlaybackState.clearPlaying()

        assertEquals(ended, PlaybackState.endedSession.value)
        assertTrue(!PlaybackState.state.value.isPlaying)
    }

    @Test
    fun `full clear wipes both playing and ended state`() {
        PlaybackState.update(label = "Morning", currentTrack = 1, totalTracks = 1, scheduleId = 7L)
        PlaybackState.publishEnded(
            EndedSession(7L, 1L, "Morning", 1000L, 2000L),
        )

        PlaybackState.clear()

        assertNull(PlaybackState.endedSession.value)
        assertTrue(!PlaybackState.state.value.isPlaying)
    }

    @Test
    fun `a new session supersedes a published ended session`() {
        PlaybackState.publishEnded(
            EndedSession(7L, 1L, "Morning", 1000L, 2000L),
        )

        PlaybackState.update(label = "Evening", currentTrack = 1, totalTracks = 2, scheduleId = 8L)

        assertNull(PlaybackState.endedSession.value)
        assertTrue(PlaybackState.state.value.isPlaying)
    }

    @Test
    fun `clearEnded consumes the published ended session`() {
        PlaybackState.publishEnded(
            EndedSession(7L, 1L, "Morning", 1000L, 2000L),
        )

        PlaybackState.clearEnded()

        assertNull(PlaybackState.endedSession.value)
    }
}
