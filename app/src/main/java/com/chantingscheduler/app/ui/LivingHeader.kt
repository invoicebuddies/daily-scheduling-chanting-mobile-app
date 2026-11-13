package com.chantingscheduler.app.ui

import com.chantingscheduler.app.R
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.data.MotionStore
import com.chantingscheduler.app.scheduling.SunTimesCalculator
import com.chantingscheduler.app.ui.motion.ArtScene
import com.chantingscheduler.app.ui.motion.DayPhase
import com.chantingscheduler.app.ui.motion.DayPhasePolicy
import com.chantingscheduler.app.ui.motion.LocalMotion
import com.chantingscheduler.app.ui.motion.MotionTokens
import com.chantingscheduler.app.ui.motion.SceneArt
import com.chantingscheduler.app.ui.motion.artSceneContentDescription
import com.chantingscheduler.app.ui.motion.rememberAmbientActive
import com.chantingscheduler.app.ui.motion.sceneBackdropBrush
import com.chantingscheduler.app.ui.motion.sceneBackdropColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

/**
 * Living overview header: greeting over a time-of-day backdrop (dawn / day /
 * dusk / night) with the user's chosen [ArtScene] artwork breathing behind it.
 * Decorative layers are semantics-free; the scene has a single content
 * description. List content stays below it.
 */
@Composable
fun LivingHeader(
    location: LatLng?,
    isChanting: Boolean,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSupport: () -> Unit = {},
) {
    val phase = rememberDayPhase(location)
    val level = LocalMotion.current
    val ambient = rememberAmbientActive()
    val scene = com.chantingscheduler.app.ui.motion.rememberArtScene()

    // Text ink follows backdrop brightness, so contrast holds in every
    // phase × theme combination (AA over the scrim below).
    val backdropMid = sceneBackdropColors(phase)[1]
    val lightBackdrop = backdropMid.luminance() > 0.45f
    val ink = if (lightBackdrop) Color(0xFF4A2C1A) else Color(0xFFF1EFFF)
    val scrim = if (lightBackdrop) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.25f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(290.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Box(Modifier.fillMaxSize().background(sceneBackdropBrush(phase)))

        // Scene artwork, crossfaded on change (320 ms, level-aware).
        Crossfade(
            targetState = scene,
            animationSpec = MotionTokens.fadeTween(level, MotionTokens.DurationMedium),
            label = "sceneCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { targetScene ->
            Box(Modifier.fillMaxSize()) {
                if (targetScene == ArtScene.MONK) {
                    SceneArt(
                        scene = targetScene,
                        phase = phase,
                        animate = ambient,
                        contentDescription = artSceneContentDescription(targetScene),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp)
                            .size(150.dp),
                    )
                } else {
                    SceneArt(
                        scene = targetScene,
                        phase = phase,
                        animate = ambient,
                        contentDescription = artSceneContentDescription(targetScene),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(210.dp),
                    )
                }
            }
        }

        // Scrim behind the text block for WCAG AA over any scene.
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(listOf(scrim, Color.Transparent)),
                ),
        )

        Column(Modifier.padding(20.dp).padding(end = 48.dp)) {
            Text(
                greeting(),
                style = MaterialTheme.typography.headlineMedium,
                color = ink,
            )
            Text(
                stringResource(R.string.header_subtitle, LocalDate.now().format(FULL_DATE)),
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = onOpenSupport) {
                Icon(
                    AppIcons.Heart,
                    contentDescription = stringResource(R.string.content_desc_support),
                    tint = ink.copy(alpha = 0.85f),
                )
            }
            IconButton(onClick = onOpenStats) {
                Icon(
                    AppIcons.Chart,
                    contentDescription = stringResource(R.string.content_desc_stats),
                    tint = ink.copy(alpha = 0.85f),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    AppIcons.SettingsGear,
                    contentDescription = stringResource(R.string.content_desc_settings),
                    tint = ink.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun rememberDayPhase(location: LatLng?): DayPhase {
    val now = remember { System.currentTimeMillis() }
    val times = remember(location, now) { sunTimesMillis(location, now) }
    return DayPhasePolicy.phaseAt(now, times?.first, times?.second)
}

/** Today's sunrise/sunset epoch millis, or null without a location/times. */
private fun sunTimesMillis(location: LatLng?, now: Long): Pair<Long, Long>? {
    if (location == null) return null
    val today = LocalDate.now()
    val sunrise = SunTimesCalculator.sunrise(today, location) ?: return null
    val sunset = SunTimesCalculator.sunset(today, location) ?: return null
    return sunrise.toInstant().toEpochMilli() to sunset.toInstant().toEpochMilli()
}

/** Summit-style greeting by device hour. */
@Composable
private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> stringResource(R.string.good_morning)
    in 12..17 -> stringResource(R.string.good_afternoon)
    else -> stringResource(R.string.good_evening)
}
