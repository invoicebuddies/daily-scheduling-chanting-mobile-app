package com.chantingscheduler.app.playback

/**
 * Decides what to do when an alarm session has produced no audio — e.g. the
 * audio focus request was denied during a phone call. The session timers
 * keep running either way, so without a policy the session would hang
 * silently until its auto-stop or the user.
 *
 * Pure decision logic; the service owns the scheduling.
 */
class AudioFocusRetryPolicy(
    private val isAlarmSession: Boolean,
    /** Milliseconds since the session started. */
    private val startElapsedMs: Long,
    /** True once the player has actually rendered audio this session. */
    private val hasRenderedAudio: Boolean,
) {

    sealed interface Action {
        /** Nothing to do (manual session, or audio already playing). */
        data object None : Action

        /** Re-issue play() and check again after [RETRY_INTERVAL_MS]. */
        data object Retry : Action

        /** Grace window exhausted: end the session as STOPPED. */
        data object GiveUp : Action
    }

    fun action(): Action = when {
        !isAlarmSession || hasRenderedAudio -> Action.None
        startElapsedMs >= MAX_WAIT_MS -> Action.GiveUp
        startElapsedMs >= FIRST_RETRY_MS -> Action.Retry
        else -> Action.None
    }

    companion object {
        /** How long to wait for first audio before the first re-try. */
        const val FIRST_RETRY_MS = 60_000L

        /** Interval between re-tries. */
        const val RETRY_INTERVAL_MS = 30_000L

        /** Total grace window from session start, then give up. */
        const val MAX_WAIT_MS = 5 * 60_000L
    }
}
