package com.chantingscheduler.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class RingingTimerTest {

    private val now = 1_000_000L
    private val started = now - 5 * 60_000L // session started 5 min ago

    @Test
    fun `routine mode shows step countdown`() {
        val timer = RingingTimerLabel.resolve(
            isRoutine = true,
            currentStep = 2,
            totalSteps = 4,
            stepEndsAtMs = now + 3 * 60_000L,
            autoStopAtMs = now + 60 * 60_000L,
            nowMs = now,
            startedAtMs = started,
        )
        assertEquals(RingingTimer.Step(2, 4, 3 * 60_000L), timer)
    }

    @Test
    fun `playlist with auto-stop counts down to the deadline`() {
        val timer = RingingTimerLabel.resolve(
            isRoutine = false,
            currentStep = 0,
            totalSteps = 0,
            stepEndsAtMs = 0,
            autoStopAtMs = now + 10 * 60_000L,
            nowMs = now,
            startedAtMs = started,
        )
        assertEquals(RingingTimer.Countdown(10 * 60_000L), timer)
    }

    @Test
    fun `playlist without auto-stop shows elapsed time`() {
        val timer = RingingTimerLabel.resolve(
            isRoutine = false,
            currentStep = 0,
            totalSteps = 0,
            stepEndsAtMs = 0,
            autoStopAtMs = 0,
            nowMs = now,
            startedAtMs = started,
        )
        assertEquals(RingingTimer.Elapsed(5 * 60_000L), timer)
    }

    @Test
    fun `routine priority beats auto-stop`() {
        val timer = RingingTimerLabel.resolve(
            isRoutine = true,
            currentStep = 1,
            totalSteps = 2,
            stepEndsAtMs = now + 60_000L,
            autoStopAtMs = now + 5 * 60_000L,
            nowMs = now,
            startedAtMs = started,
        )
        // Step label wins even though auto-stop is also armed.
        assertEquals(RingingTimer.Step(1, 2, 60_000L), timer)
    }

    @Test
    fun `remaining time never goes negative`() {
        val timer = RingingTimerLabel.resolve(
            isRoutine = false,
            currentStep = 0,
            totalSteps = 0,
            stepEndsAtMs = 0,
            autoStopAtMs = now - 1_000L,
            nowMs = now,
            startedAtMs = started,
        )
        assertEquals(RingingTimer.Countdown(0), timer)
    }

    @Test
    fun `clock formats minutes and seconds`() {
        assertEquals("0:00", RingingTimerLabel.formatClock(0))
        assertEquals("0:05", RingingTimerLabel.formatClock(5_000L))
        assertEquals("1:00", RingingTimerLabel.formatClock(60_000L))
        assertEquals("12:34", RingingTimerLabel.formatClock((12 * 60 + 34) * 1000L))
        assertEquals("0:00", RingingTimerLabel.formatClock(-5_000L))
    }
}
