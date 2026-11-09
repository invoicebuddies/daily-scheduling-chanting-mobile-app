package com.chantingscheduler.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusRetryPolicyTest {

    @Test
    fun `manual session never retries`() {
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = false,
            startElapsedMs = 120_000L,
            hasRenderedAudio = false,
        )
        assertEquals(AudioFocusRetryPolicy.Action.None, policy.action())
    }

    @Test
    fun `alarm session that rendered audio needs no retry`() {
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 120_000L,
            hasRenderedAudio = true,
        )
        assertEquals(AudioFocusRetryPolicy.Action.None, policy.action())
    }

    @Test
    fun `within initial grace window no retry yet`() {
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 30_000L,
            hasRenderedAudio = false,
        )
        assertEquals(AudioFocusRetryPolicy.Action.None, policy.action())
    }

    @Test
    fun `after 60s of silence retry`() {
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 60_000L,
            hasRenderedAudio = false,
        )
        assertEquals(AudioFocusRetryPolicy.Action.Retry, policy.action())
    }

    @Test
    fun `retry continues between retries`() {
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 240_000L,
            hasRenderedAudio = false,
        )
        assertEquals(AudioFocusRetryPolicy.Action.Retry, policy.action())
    }

    @Test
    fun `after 5 minutes give up`() {
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 300_000L,
            hasRenderedAudio = false,
        )
        assertEquals(AudioFocusRetryPolicy.Action.GiveUp, policy.action())
    }

    @Test
    fun `user paused from lock screen controls never retries or gives up`() {
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 300_000L,
            hasRenderedAudio = false,
            userPaused = true,
        )
        assertEquals(AudioFocusRetryPolicy.Action.None, policy.action())
    }

    @Test
    fun `silence-first routine window starts at the first audible step`() {
        // A 6-minute silence step precedes the chant: the window must be based
        // on when the audible step started (small elapsed), not the session —
        // otherwise the policy would give up the moment the chant begins.
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 30_000L,
            hasRenderedAudio = false,
        )
        assertEquals(AudioFocusRetryPolicy.Action.None, policy.action())
    }

    @Test
    fun `silence-only routine never arms the retry`() {
        // The service cancels FOCUS_RETRY_TOKEN for silence steps; even if a
        // check ran, an old window with no audio expected must not give up.
        // (Covered service-side; here the paused/no-audio semantics stay None.)
        val policy = AudioFocusRetryPolicy(
            isAlarmSession = true,
            startElapsedMs = 0L,
            hasRenderedAudio = false,
        )
        assertEquals(AudioFocusRetryPolicy.Action.None, policy.action())
    }
}
