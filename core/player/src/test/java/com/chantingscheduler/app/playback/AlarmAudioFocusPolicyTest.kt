package com.chantingscheduler.app.playback

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmAudioFocusPolicyTest {

    @Test
    fun `transient loss pauses`() {
        assertEquals(
            AlarmAudioFocusPolicy.Action.Pause,
            AlarmAudioFocusPolicy.actionFor(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                focusLossPaused = false,
                userPaused = false,
            ),
        )
    }

    @Test
    fun `permanent loss stops the session`() {
        assertEquals(
            AlarmAudioFocusPolicy.Action.Stop,
            AlarmAudioFocusPolicy.actionFor(
                AudioManager.AUDIOFOCUS_LOSS,
                focusLossPaused = false,
                userPaused = false,
            ),
        )
    }

    @Test
    fun `duckable loss keeps playing`() {
        assertEquals(
            AlarmAudioFocusPolicy.Action.KeepPlaying,
            AlarmAudioFocusPolicy.actionFor(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                focusLossPaused = false,
                userPaused = false,
            ),
        )
    }

    @Test
    fun `gain resumes after our transient-loss pause`() {
        assertEquals(
            AlarmAudioFocusPolicy.Action.Resume,
            AlarmAudioFocusPolicy.actionFor(
                AudioManager.AUDIOFOCUS_GAIN,
                focusLossPaused = true,
                userPaused = false,
            ),
        )
    }

    @Test
    fun `gain does nothing when not focus-paused`() {
        assertNull(
            AlarmAudioFocusPolicy.actionFor(
                AudioManager.AUDIOFOCUS_GAIN,
                focusLossPaused = false,
                userPaused = false,
            ),
        )
    }

    @Test
    fun `gain never overrides a user pause`() {
        assertNull(
            AlarmAudioFocusPolicy.actionFor(
                AudioManager.AUDIOFOCUS_GAIN,
                focusLossPaused = true,
                userPaused = true,
            ),
        )
    }

    @Test
    fun `unknown change is ignored`() {
        assertNull(
            AlarmAudioFocusPolicy.actionFor(
                -999,
                focusLossPaused = true,
                userPaused = false,
            ),
        )
    }

    // mayStart: the gate for every automatic play() (gap end, routine step,
    // fallback chime, retry, failure seek).

    @Test
    fun `mayStart false when a gap ends mid-call`() {
        assertEquals(
            false,
            AlarmAudioFocusPolicy.mayStart(
                useAlarmStream = true, focusHeld = true,
                focusLossPaused = true, userPaused = false,
            ),
        )
    }

    @Test
    fun `mayStart false when a routine step starts mid-call`() {
        assertEquals(
            false,
            AlarmAudioFocusPolicy.mayStart(
                useAlarmStream = true, focusHeld = true,
                focusLossPaused = true, userPaused = false,
            ),
        )
    }

    @Test
    fun `mayStart false for the fallback chime mid-call`() {
        assertEquals(
            false,
            AlarmAudioFocusPolicy.mayStart(
                useAlarmStream = true, focusHeld = true,
                focusLossPaused = true, userPaused = false,
            ),
        )
    }

    @Test
    fun `mayStart false when focus was never granted`() {
        assertEquals(
            false,
            AlarmAudioFocusPolicy.mayStart(
                useAlarmStream = true, focusHeld = false,
                focusLossPaused = false, userPaused = false,
            ),
        )
    }

    @Test
    fun `mayStart false on user pause`() {
        assertEquals(
            false,
            AlarmAudioFocusPolicy.mayStart(
                useAlarmStream = true, focusHeld = true,
                focusLossPaused = false, userPaused = true,
            ),
        )
    }

    @Test
    fun `mayStart true for a healthy alarm-stream session`() {
        assertEquals(
            true,
            AlarmAudioFocusPolicy.mayStart(
                useAlarmStream = true, focusHeld = true,
                focusLossPaused = false, userPaused = false,
            ),
        )
    }

    @Test
    fun `media-stream sessions ignore focus state entirely`() {
        // ExoPlayer owns focus for media streams: any combination may start.
        for (held in listOf(true, false)) {
            for (lossPaused in listOf(true, false)) {
                assertEquals(
                    true,
                    AlarmAudioFocusPolicy.mayStart(
                        useAlarmStream = false, focusHeld = held,
                        focusLossPaused = lossPaused, userPaused = false,
                    ),
                )
            }
        }
    }
}
