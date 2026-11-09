package com.chantingscheduler.app.playback

/** Pure math for the linear volume fade-in used by [PlaybackService]. */
object VolumeFade {

    /**
     * Volume multiplier (0f..1f) for a linear fade-in of [durationMs] at
     * [elapsedMs] after playback started. A non-positive duration means no
     * fade, i.e. full volume immediately.
     */
    fun volumeAt(elapsedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 1f
        return (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}
