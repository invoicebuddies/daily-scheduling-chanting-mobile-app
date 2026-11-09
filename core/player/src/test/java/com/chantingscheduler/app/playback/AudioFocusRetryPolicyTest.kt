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
}
