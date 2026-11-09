package com.chantingscheduler.app.playback

import android.media.AudioManager

/**
 * Maps framework audio-focus changes to actions for an alarm-stream session.
 *
 * ExoPlayer only supports automatic focus handling for media/game usage, so
 * alarm-stream chants (`USAGE_ALARM`) manage focus themselves: a granted
 * request means the chant may play; a transient loss (e.g. an incoming call)
 * means pause and wait for the matching gain. The service owns the state
 * ([focusLossPaused], [userPaused]) this policy reads — pure decision logic.
 */
object AlarmAudioFocusPolicy {

    enum class Action {
        /** Pause playback until focus returns (never overrides a user pause). */
        Pause,

        /** Resume playback after a transient loss (only if WE paused, not the user). */
        Resume,

        /** Keep playing — the loss allows ducking and the alarm stream doesn't duck. */
        KeepPlaying,
    }

    /**
     * @param focusChange one of the `AudioManager.AUDIOFOCUS_*` change constants.
     * @param focusLossPaused true when this session paused itself on a transient loss.
     * @param userPaused true when the user deliberately paused (lock-screen/Bluetooth).
     * @return the action to take, or null when nothing should happen.
     */
    fun actionFor(focusChange: Int, focusLossPaused: Boolean, userPaused: Boolean): Action? =
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS,
            -> Action.Pause

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Action.KeepPlaying

            AudioManager.AUDIOFOCUS_GAIN ->
                if (focusLossPaused && !userPaused) Action.Resume else null

            else -> null
        }
}
