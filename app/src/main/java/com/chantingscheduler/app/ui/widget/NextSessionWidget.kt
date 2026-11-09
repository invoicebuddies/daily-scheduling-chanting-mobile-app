package com.chantingscheduler.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.chantingscheduler.app.R
import com.chantingscheduler.app.data.AppDatabase
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.playback.PlaybackService
import com.chantingscheduler.app.playback.PlaybackState
import com.chantingscheduler.app.scheduling.AlarmScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Small home-screen widget: the next upcoming session and a play/stop toggle.
 * Data is recomputed on every update — requested from schedule and playback
 * changes via the widget bridge, plus the system's periodic refresh.
 */
class NextSessionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadNextSession(context)
        val playing = PlaybackState.state.value.isPlaying
        val strings = WidgetStrings(
            appName = context.getString(R.string.app_name),
            noSession = context.getString(R.string.widget_no_session),
            stopLabel = context.getString(R.string.stop),
            chantNowLabel = context.getString(R.string.widget_chant_now),
            defaultSessionName = context.getString(R.string.default_session_name),
        )
        provideContent {
            GlanceTheme {
                WidgetContent(
                    strings = strings,
                    nextLabel = snapshot?.first,
                    nextTime = snapshot?.second,
                    isPlaying = playing,
                )
            }
        }
    }

    private suspend fun loadNextSession(context: Context): Pair<String, String>? {
        val repository = ScheduleRepository(AppDatabase.get(context))
        val scheduler = AlarmScheduler(context)
        val zone = ZoneId.systemDefault()
        val timeFormat = DateTimeFormatter.ofPattern("EEE HH:mm")
        return repository.getEnabled()
            .mapNotNull { item ->
                scheduler.nextTriggerMillis(item.schedule)
                    .takeIf { it > 0 }
                    ?.let { item to it }
            }
            .minByOrNull { it.second }
            ?.let { (item, millis) ->
                val name = item.schedule.label
                val time = Instant.ofEpochMilli(millis).atZone(zone).format(timeFormat)
                name to time
            }
    }

    @Composable
    private fun WidgetContent(
        strings: WidgetStrings,
        nextLabel: String?,
        nextTime: String?,
        isPlaying: Boolean,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(14.dp)
                .clickable(actionRunCallback<PlayToggleCallback>()),
        ) {
            Text(
                strings.appName,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.secondary,
                ),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                nextLabel?.ifBlank { null } ?: strings.noSession,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
            if (nextTime != null) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    nextTime,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(
                        if (isPlaying) {
                            com.chantingscheduler.app.core.player.R.drawable.ic_stop
                        } else {
                            R.drawable.ic_play_arrow
                        },
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    if (isPlaying) strings.stopLabel else strings.chantNowLabel,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary,
                    ),
                )
            }
        }
    }
}

/** Widget tap: stop an active session, otherwise chant the first schedule now. */
class PlayToggleCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        if (PlaybackState.state.value.isPlaying) {
            PlaybackService.stop(context)
        } else {
            val repository = ScheduleRepository(AppDatabase.get(context))
            val first = repository.getEnabled()
                .firstOrNull { it.sounds.isNotEmpty() || it.steps.isNotEmpty() }
            if (first != null) {
                if (first.isRoutine) {
                    PlaybackService.start(
                        context = context,
                        uris = emptyList(),
                        gapSeconds = 0,
                        label = first.schedule.label,
                        scheduleId = first.schedule.id,
                        stepUris = first.sortedSteps.map { it.soundUri.orEmpty() },
                        stepMinutes = first.sortedSteps.map { it.durationMinutes },
                    )
                } else {
                    PlaybackService.start(
                        context = context,
                        uris = first.sortedSounds.map { it.uri },
                        gapSeconds = first.schedule.gapSeconds,
                        label = first.schedule.label,
                        scheduleId = first.schedule.id,
                        repeatCount = first.schedule.repeatCount,
                        maxDurationMinutes = first.schedule.maxDurationMinutes ?: -1,
                    )
                }
            }
        }
        com.chantingscheduler.app.playback.WidgetBridge.requestUpdate(context)
    }
}

class NextSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextSessionWidget()
}

/** User-facing strings resolved outside the Glance composition. */
private data class WidgetStrings(
    val appName: String,
    val noSession: String,
    val stopLabel: String,
    val chantNowLabel: String,
    val defaultSessionName: String,
)
