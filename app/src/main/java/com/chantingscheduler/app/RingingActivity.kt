package com.chantingscheduler.app

import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chantingscheduler.app.data.AppTheme
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.data.ThemeStore
import com.chantingscheduler.app.data.TriggerType
import com.chantingscheduler.app.ui.rememberReduceMotion
import com.chantingscheduler.app.playback.EndedSession
import com.chantingscheduler.app.playback.PlaybackService
import com.chantingscheduler.app.playback.PlaybackState
import com.chantingscheduler.app.playback.RingingTimer
import com.chantingscheduler.app.playback.RingingTimerLabel
import com.chantingscheduler.app.ui.AppIcons
import com.chantingscheduler.app.ui.theme.ChantingSchedulerTheme
import com.chantingscheduler.app.ui.theme.UrbanistFamily
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Full-screen "alarm ringing" page shown while a chanting session plays.
 * Ambient animated backdrop depends on the schedule's trigger type: dawn for
 * sunrise, dusk for sunset, deep night (drifting clouds, twinkling stars,
 * wobbling crescent) for fixed times. Opened from the playback notification
 * (tap or full-screen intent) and from the in-app ringing card.
 */
@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var playbackSettingsStore: PlaybackSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)

        setContent {
            val theme by ThemeStore.get(this).theme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            ChantingSchedulerTheme(
                darkTheme = when (theme) {
                    AppTheme.AUTO -> systemDark
                    AppTheme.LIGHT -> false
                    AppTheme.DARK -> true
                },
            ) {
                var triggerType by remember { mutableStateOf(TriggerType.FIXED) }
                LaunchedEffect(scheduleId) {
                    if (scheduleId > 0) {
                        repository.getById(scheduleId)
                            ?.let { triggerType = it.schedule.triggerType }
                    }
                }
                val nowPlaying by PlaybackState.state.collectAsStateWithLifecycle()
                val endedSession by PlaybackState.endedSession.collectAsStateWithLifecycle()
                val snoozeMinutes by playbackSettingsStore.snoozeMinutes.collectAsStateWithLifecycle()

                // The session ended by itself (completed / auto-stop / focus
                // loss): close immediately when the screen is off or locked,
                // otherwise switch to the calm complete state and auto-close
                // after a quiet minute with no interaction.
                var complete by remember { mutableStateOf<EndedSession?>(null) }
                LaunchedEffect(endedSession) {
                    val ended = endedSession ?: return@LaunchedEffect
                    PlaybackState.clearEnded()
                    if (complete != null) return@LaunchedEffect
                    val interactive =
                        getSystemService(PowerManager::class.java)?.isInteractive != false
                    if (!interactive) {
                        finish()
                    } else {
                        complete = ended
                    }
                }
                LaunchedEffect(complete) {
                    if (complete != null) {
                        delay(SESSION_COMPLETE_CLOSE_MS)
                        finish()
                    }
                }

                RingingScreen(
                    triggerType = triggerType,
                    label = nowPlaying.label,
                    currentTrack = nowPlaying.currentTrack,
                    totalTracks = nowPlaying.totalTracks,
                    currentStep = nowPlaying.currentStep,
                    totalSteps = nowPlaying.totalSteps,
                    startedAtMillis = nowPlaying.startedAtMillis,
                    stepEndsAtMillis = nowPlaying.stepEndsAtMillis,
                    autoStopAtMillis = nowPlaying.autoStopAtMillis,
                    complete = complete,
                    snoozeMinutes = snoozeMinutes,
                    onSnooze = {
                        PlaybackService.snooze(this)
                        finish()
                    },
                    onStop = {
                        PlaybackService.stop(this)
                        finish()
                    },
                    onDone = {
                        complete = null
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        /** Auto-close of the complete state when nothing was touched. */
        private const val SESSION_COMPLETE_CLOSE_MS = 60_000L
    }
}

@Composable
private fun RingingScreen(
    triggerType: TriggerType,
    label: String,
    currentTrack: Int,
    totalTracks: Int,
    currentStep: Int,
    totalSteps: Int,
    startedAtMillis: Long,
    stepEndsAtMillis: Long,
    autoStopAtMillis: Long,
    complete: EndedSession?,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit,
) {
    // Tick once a second; the pill text is derived from this "now".
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val timer = RingingTimerLabel.resolve(
        isRoutine = totalSteps > 0,
        currentStep = currentStep,
        totalSteps = totalSteps,
        stepEndsAtMs = stepEndsAtMillis,
        autoStopAtMs = autoStopAtMillis,
        nowMs = nowMs,
        startedAtMs = startedAtMillis,
    )

    Box(Modifier.fillMaxSize()) {
        when (triggerType) {
            TriggerType.SUNRISE -> DawnBackground()
            TriggerType.SUNSET -> DuskBackground()
            TriggerType.FIXED, TriggerType.FULL_MOON, TriggerType.NEW_MOON -> NightBackground()
        }

        val ended = complete
        if (ended != null) {
            SessionCompleteContent(ended = ended, onDone = onDone)
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                label.ifBlank { stringResource(R.string.ringing_default_label) },
                fontFamily = UrbanistFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = Color.White,
            )
            if (totalSteps > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.step_status, currentStep, totalSteps),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            } else if (totalTracks > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.track_status, currentTrack, totalTracks),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.16f),
            ) {
                Text(
                    timerText(timer),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = onSnooze,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) {
                    Icon(
                        AppIcons.Bell,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.snooze_button, snoozeMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1A2340),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                ) {
                    Icon(
                        AppIcons.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.stop_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Pill text for the resolved timer: "Step x of n · mm:ss left", "mm:ss left", or "mm:ss". */
@Composable
private fun timerText(timer: RingingTimer): String = when (timer) {
    is RingingTimer.Step -> stringResource(
        R.string.ringing_step_time_left,
        timer.step,
        timer.totalSteps,
        RingingTimerLabel.formatClock(timer.remainingMs),
    )
    is RingingTimer.Countdown ->
        stringResource(R.string.ringing_time_left, RingingTimerLabel.formatClock(timer.remainingMs))
    is RingingTimer.Elapsed -> RingingTimerLabel.formatClock(timer.elapsedMs)
}

/** Calm end-of-session state: what played, for how long, and a quiet way out. */
@Composable
private fun SessionCompleteContent(ended: EndedSession, onDone: () -> Unit) {
    val minutes = ((ended.endedAtMillis - ended.startedAtMillis) / 60_000L)
        .toInt()
        .coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.session_complete_title),
            fontFamily = UrbanistFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            ended.label.ifBlank { stringResource(R.string.ringing_default_label) },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Text(
            stringResource(R.string.session_complete_minutes, minutes),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF1A2340),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                stringResource(R.string.done_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Slow ambient animation clock shared by the backdrops. */
@Composable
private fun ambientClock(): Float {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "ambient")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = if (reduceMotion) infiniteRepeatable(tween(0)) else infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambientT",
    )
    return t
}

/** Fixed-time: deep indigo night, drifting clouds, twinkling stars, crescent. */
@Composable
private fun NightBackground() {
    val t = ambientClock()
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1030), Color(0xFF1E2A5A), Color(0xFF3A2A5E)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Twinkling stars (phase varies by index).
            val starPositions = listOf(
                Offset(0.10f, 0.10f), Offset(0.30f, 0.06f), Offset(0.55f, 0.12f),
                Offset(0.80f, 0.08f), Offset(0.92f, 0.20f), Offset(0.16f, 0.26f),
                Offset(0.42f, 0.20f), Offset(0.68f, 0.28f), Offset(0.86f, 0.38f),
                Offset(0.08f, 0.44f), Offset(0.34f, 0.36f), Offset(0.60f, 0.44f),
            )
            starPositions.forEachIndexed { i, p ->
                val phase = (t + i * 0.11f) % 1f
                val alpha = 0.35f + 0.6f * kotlin.math.abs(phase - 0.5f) * 2f
                drawCircle(
                    Color.White.copy(alpha = alpha),
                    radius = (1.5f + (i % 3) * 0.7f).dp.toPx(),
                    center = Offset(p.x * w, p.y * h),
                )
            }
            // Slowly wobbling crescent moon.
            val moon = Offset(w * 0.76f, h * 0.20f)
            val r = w * 0.09f
            val wobble = Math.toRadians(((t * 360f) % 30f - 15f).toDouble()).toFloat()
            val cos = kotlin.math.cos(wobble)
            val sin = kotlin.math.sin(wobble)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFF3C4).copy(alpha = 0.30f), Color.Transparent),
                    center = moon,
                    radius = r * 2.6f,
                ),
                radius = r * 2.6f,
                center = moon,
            )
            drawCircle(Color(0xFFFFF3C4), radius = r, center = moon)
            // Shadow disc rotates gently around the moon.
            val shadowOffset = Offset(r * 0.45f * cos - (-r * 0.2f) * sin, r * 0.45f * sin + (-r * 0.2f) * cos)
            drawCircle(Color(0xFF16204A), radius = r * 0.85f, center = moon + shadowOffset)
            // Drifting clouds.
            val cloudColor = Color(0xFF8E9AC8).copy(alpha = 0.35f)
            fun cloud(cx: Float, cy: Float, s: Float) {
                drawCircle(cloudColor, radius = 20.dp.toPx() * s, center = Offset(cx, cy))
                drawCircle(cloudColor, radius = 28.dp.toPx() * s, center = Offset(cx + 26.dp.toPx() * s, cy - 8.dp.toPx() * s))
                drawCircle(cloudColor, radius = 20.dp.toPx() * s, center = Offset(cx + 52.dp.toPx() * s, cy))
            }
            val drift1 = ((t * 1.4f) % 1f)
            val drift2 = ((t * 1.4f + 0.5f) % 1f)
            cloud(-w * 0.2f + drift1 * w * 1.4f, h * 0.62f, 1f)
            cloud(-w * 0.2f + drift2 * w * 1.4f, h * 0.78f, 0.7f)
        }
    }
}

/** Sunrise trigger: dawn gradient with a warm glow slowly rising. */
@Composable
private fun DawnBackground() {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "dawn")
    val rise by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = if (reduceMotion) infiniteRepeatable(tween(0)) else infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dawnRise",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A2340), Color(0xFF6B3B2A), Color(0xFFE8A35C)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val glowCenter = Offset(size.width / 2f, size.height * (0.85f - rise * 0.25f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFD9A0).copy(alpha = 0.85f), Color.Transparent),
                    center = glowCenter,
                    radius = size.width * 0.55f,
                ),
                radius = size.width * 0.55f,
                center = glowCenter,
            )
            drawCircle(Color(0xFFFFC36B), radius = size.width * 0.10f, center = glowCenter)
            val haze = Color.White.copy(alpha = 0.12f)
            drawOval(
                haze,
                topLeft = Offset(size.width * 0.08f, size.height * 0.55f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.05f),
            )
        }
    }
}

/** Sunset trigger: dusk gradient with a glow slowly sinking. */
@Composable
private fun DuskBackground() {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "dusk")
    val sink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = if (reduceMotion) infiniteRepeatable(tween(0)) else infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "duskSink",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF131A3A), Color(0xFF4A2C5E), Color(0xFFB4552A)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Sinking glow stays low so the text above it remains readable.
            val glowCenter = Offset(size.width / 2f, size.height * (0.68f + sink * 0.14f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFF9E6B).copy(alpha = 0.8f), Color.Transparent),
                    center = glowCenter,
                    radius = size.width * 0.5f,
                ),
                radius = size.width * 0.5f,
                center = glowCenter,
            )
            drawCircle(Color(0xFFFFB36B), radius = size.width * 0.09f, center = glowCenter)
            val star = Color.White.copy(alpha = 0.5f)
            listOf(Offset(0.2f, 0.1f), Offset(0.7f, 0.14f), Offset(0.45f, 0.22f)).forEach { p ->
                drawCircle(star, radius = 2.dp.toPx(), center = Offset(p.x * size.width, p.y * size.height))
            }
        }
    }
}
