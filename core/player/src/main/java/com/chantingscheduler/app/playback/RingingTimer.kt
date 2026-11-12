package com.chantingscheduler.app.playback

/**
 * What the ringing-page timer pill should show, decided purely from session
 * facts. Priority: routine step countdown, auto-stop countdown, then elapsed
 * time. Formatting-free so the UI can render strings from resources.
 */
sealed interface RingingTimer {
    /** Routine mode: "Step x of n · mm:ss left" — time left in the current step. */
    data class Step(val step: Int, val totalSteps: Int, val remainingMs: Long) : RingingTimer

    /** Auto-stop armed: "mm:ss left" until the session deadline. */
    data class Countdown(val remainingMs: Long) : RingingTimer

    /** Otherwise: elapsed "mm:ss" since the session started. */
    data class Elapsed(val elapsedMs: Long) : RingingTimer
}

object RingingTimerLabel {

    /**
     * Chooses the pill content. [stepEndsAtMs] and [autoStopAtMs] are
     * wall-clock deadlines; 0 = not applicable. Remaining times never go
     * negative (a step may outlive its deadline by a tick).
     */
    fun resolve(
        isRoutine: Boolean,
        currentStep: Int,
        totalSteps: Int,
        stepEndsAtMs: Long,
        autoStopAtMs: Long,
        nowMs: Long,
        startedAtMs: Long,
    ): RingingTimer = when {
        isRoutine -> RingingTimer.Step(
            step = currentStep,
            totalSteps = totalSteps,
            remainingMs = (stepEndsAtMs - nowMs).coerceAtLeast(0),
        )
        autoStopAtMs > 0 -> RingingTimer.Countdown((autoStopAtMs - nowMs).coerceAtLeast(0))
        else -> RingingTimer.Elapsed((nowMs - startedAtMs).coerceAtLeast(0))
    }

    /** "mm:ss" for a (possibly negative-clamped) millisecond duration. */
    fun formatClock(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
