package com.chantingscheduler.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistFailurePolicyTest {

    private val policy = PlaylistFailurePolicy()

    @Test
    fun `single failure among several tracks skips`() {
        val decision = policy.onTrackFailed("a", setOf("a", "b", "c"))
        assertEquals(PlaylistFailurePolicy.Decision.Skip, decision)
    }

    @Test
    fun `all unique uris failed falls back`() {
        policy.onTrackFailed("a", setOf("a", "b"))
        val decision = policy.onTrackFailed("b", setOf("a", "b"))
        assertEquals(PlaylistFailurePolicy.Decision.Fallback, decision)
    }

    @Test
    fun `same uri failing again across loop passes still counts once`() {
        // Auto-stop loop or repeat expansion replays the same broken file:
        // it must not accumulate toward the fallback while good audio plays.
        policy.onTrackFailed("b", setOf("a", "b"))
        val decision = policy.onTrackFailed("b", setOf("a", "b"))
        assertEquals(PlaylistFailurePolicy.Decision.Skip, decision)
        assertEquals(1, policy.failedCount)
    }

    @Test
    fun `broken last track with auto-stop loop keeps playing`() {
        // Regression: the old index-based counter stopped the session with
        // COMPLETED when the last track was broken, ending auto-stop sessions
        // early. The decision must be Skip so looping continues until the
        // auto-stop deadline.
        val decision = policy.onTrackFailed("c", setOf("a", "b", "c"))
        assertEquals(PlaylistFailurePolicy.Decision.Skip, decision)
    }

    @Test
    fun `every track failing across passes falls back`() {
        policy.onTrackFailed("a", setOf("a", "b"))
        policy.onTrackFailed("a", setOf("a", "b")) // repeat pass of the same file
        val decision = policy.onTrackFailed("b", setOf("a", "b"))
        assertEquals(PlaylistFailurePolicy.Decision.Fallback, decision)
    }

    @Test
    fun `single track playlist falls back on first failure`() {
        val decision = policy.onTrackFailed("only", setOf("only"))
        assertEquals(PlaylistFailurePolicy.Decision.Fallback, decision)
    }
}
