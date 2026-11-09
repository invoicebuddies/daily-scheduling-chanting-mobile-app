package com.chantingscheduler.app.scheduling

import com.chantingscheduler.app.data.ScheduleWithSounds

/**
 * Starts playback of a schedule's playlist when an alarm fires. The playback
 * module depends on this one, so the actual start (PlaybackService) is
 * implemented in the app module and Hilt-bound there.
 */
fun interface SessionStarter {
    fun start(item: ScheduleWithSounds)
}
