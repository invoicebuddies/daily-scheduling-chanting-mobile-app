package com.chantingscheduler.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.chantingscheduler.app.R
import com.chantingscheduler.app.data.AppDatabase
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.playback.PlaybackService
import com.chantingscheduler.app.playback.PlaybackState
import com.chantingscheduler.app.playback.WidgetBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: tap to chant the first enabled schedule right now;
 * tap again to stop. The label follows the global playback state.
 */
class ChantTileService : TileService() {

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        if (PlaybackState.state.value.isPlaying) {
            PlaybackService.stop(this)
            updateTile()
            WidgetBridge.requestUpdate(this)
        } else {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val first = ScheduleRepository(AppDatabase.get(this@ChantTileService))
                    .getEnabled()
                    .firstOrNull { it.sounds.isNotEmpty() || it.steps.isNotEmpty() }
                if (first != null) {
                    val intent = if (first.isRoutine) {
                        PlaybackService.playIntent(
                            context = this@ChantTileService,
                            uris = emptyList(),
                            gapSeconds = 0,
                            label = first.schedule.label,
                            scheduleId = first.schedule.id,
                            stepUris = first.sortedSteps.map { it.soundUri.orEmpty() },
                            stepMinutes = first.sortedSteps.map { it.durationMinutes },
                        )
                    } else {
                        PlaybackService.playIntent(
                            context = this@ChantTileService,
                            uris = first.sortedSounds.map { it.uri },
                            gapSeconds = first.schedule.gapSeconds,
                            label = first.schedule.label,
                            scheduleId = first.schedule.id,
                            repeatCount = first.schedule.repeatCount,
                            maxDurationMinutes = first.schedule.maxDurationMinutes ?: -1,
                        )
                    }
                    runOnMainThread {
                        // Android 12+ can deny a background FGS start; route
                        // through the guarded helper instead of crashing.
                        val started = PlaybackService.tryStart(this@ChantTileService, intent)
                        if (!started && Build.VERSION.SDK_INT >= 34) {
                            // Refused: collapse the shade straight into the app.
                            startActivityAndCollapse(openAppPendingIntent())
                        }
                        updateTile()
                    }
                } else {
                    runOnMainThread { updateTile() }
                }
                WidgetBridge.requestUpdate(this@ChantTileService)
            }
        }
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent()
            .setClassName(packageName, "com.chantingscheduler.app.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun updateTile() {
        val tile = qsTile ?: return
        val playing = PlaybackState.state.value.isPlaying
        tile.label = if (playing) getString(R.string.tile_stop_chanting) else getString(R.string.tile_chant_now)
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun runOnMainThread(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}
