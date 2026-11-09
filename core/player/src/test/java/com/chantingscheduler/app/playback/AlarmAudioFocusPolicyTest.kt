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
    fun `permanent loss pauses`() {
        assertEquals(
            AlarmAudioFocusPolicy.Action.Pause,
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
}
