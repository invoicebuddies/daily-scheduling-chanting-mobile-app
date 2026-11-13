package com.chantingscheduler.app.ui

import com.chantingscheduler.app.R
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.data.ScheduleEntity
import com.chantingscheduler.app.data.ScheduleWithSounds
import com.chantingscheduler.app.data.TriggerType
import com.chantingscheduler.app.playback.NowPlaying
import com.chantingscheduler.app.ui.motion.MotionTokens
import com.chantingscheduler.app.ui.motion.LocalMotion
import com.chantingscheduler.app.ui.motion.MotionLevel
import com.chantingscheduler.app.ui.motion.rememberAmbientFloat
import com.chantingscheduler.app.scheduling.AlarmScheduler
import com.chantingscheduler.app.scheduling.SunTimesCalculator
import com.chantingscheduler.app.ui.theme.UrbanistFamily
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm")
private val AMPM: DateTimeFormatter = DateTimeFormatter.ofPattern("a", Locale.US)
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

// Hero card gradient (constant across themes).
private val HeroGradientTop = Color(0xFF1C1B3A)
private val HeroGradientBottom = Color(0xFF4A2C1A)
private val HeroAmber = Color(0xFFE8A96B)
private val HeroText = Color(0xFFF7F3EA)

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** Lunar triggers fire on moon dates and ignore the days-of-week mask. */
private val TriggerType.isLunarType: Boolean
    get() = this == TriggerType.FULL_MOON || this == TriggerType.NEW_MOON

/** e.g. "Sunrise −15m", "Sunset", "Full moon". */
@Composable
fun sunTriggerCaption(schedule: ScheduleEntity): String {
    val name = when (schedule.triggerType) {
        TriggerType.SUNRISE -> stringResource(R.string.trigger_sunrise)
        TriggerType.SUNSET -> stringResource(R.string.trigger_sunset)
        TriggerType.FULL_MOON -> stringResource(R.string.trigger_full_moon)
        TriggerType.NEW_MOON -> stringResource(R.string.trigger_new_moon)
        TriggerType.FIXED -> stringResource(R.string.trigger_fixed_time)
    }
    return when {
        schedule.triggerType == TriggerType.FIXED ||
            schedule.triggerType == TriggerType.FULL_MOON ||
            schedule.triggerType == TriggerType.NEW_MOON -> name
        schedule.offsetMinutes == 0 -> name
        schedule.offsetMinutes > 0 -> "$name +${schedule.offsetMinutes}m"
        else -> "$name −${-schedule.offsetMinutes}m"
    }
}

/** Small-caps card label: schedule name if set, else the trigger type. */
@Composable
private fun cardOverline(schedule: ScheduleEntity): String =
    schedule.label.ifBlank {
        when (schedule.triggerType) {
            TriggerType.FIXED -> stringResource(R.string.trigger_fixed_time)
            TriggerType.SUNRISE -> stringResource(R.string.trigger_sunrise)
            TriggerType.SUNSET -> stringResource(R.string.trigger_sunset)
            TriggerType.FULL_MOON -> stringResource(R.string.trigger_full_moon)
            TriggerType.NEW_MOON -> stringResource(R.string.trigger_new_moon)
        }
    }

/** Today's fire time of a sun trigger at [location], offset applied; null if unknown. */
private fun todaySunInstant(schedule: ScheduleEntity, location: LatLng?): ZonedDateTime? {
    val sunTrigger = schedule.triggerType == TriggerType.SUNRISE ||
        schedule.triggerType == TriggerType.SUNSET
    if (!sunTrigger || location == null) return null
    val base = when (schedule.triggerType) {
        TriggerType.SUNRISE -> SunTimesCalculator.sunrise(LocalDate.now(), location)
        TriggerType.SUNSET -> SunTimesCalculator.sunset(LocalDate.now(), location)
        else -> null
    }
    return base?.plusMinutes(schedule.offsetMinutes.toLong())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    schedules: List<ScheduleWithSounds>,
    nowPlaying: NowPlaying,
    location: LatLng?,
    missingSoundUris: Set<String> = emptySet(),
    missedChantCard: MissedChantUi? = null,
    onOpenReliability: () -> Unit = {},
    onDismissMissed: () -> Unit = {},
    updateCard: UpdateCardUi? = null,
    onUpdateDownload: () -> Unit = {},
    onUpdateLater: () -> Unit = {},
    onUpdateSkip: () -> Unit = {},
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSupport: () -> Unit = {},
    onToggle: (ScheduleWithSounds, Boolean) -> Unit,
    onPlayToggle: (ScheduleWithSounds) -> Unit,
    onStop: () -> Unit,
    onOpenRinging: () -> Unit,
    deletedItem: ScheduleWithSounds? = null,
    onUndoDelete: () -> Unit = {},
    onUndoExpired: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val defaultName = stringResource(R.string.default_schedule_name)
    val deletedTemplate = stringResource(R.string.deleted_snackbar)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(deletedItem) {
        val item = deletedItem ?: return@LaunchedEffect
        val name = item.schedule.label.ifBlank { defaultName }
        val result = snackbarHostState.showSnackbar(
            message = String.format(deletedTemplate, name),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoDelete()
        } else {
            onUndoExpired()
        }
    }
    val needsLocation = location == null &&
        schedules.any {
            it.schedule.triggerType == TriggerType.SUNRISE ||
                it.schedule.triggerType == TriggerType.SUNSET
        }

    val nextSession = remember(schedules, location) {
        val scheduler = AlarmScheduler(context)
        schedules
            .filter { it.schedule.enabled }
            .mapNotNull { item ->
                scheduler.nextTriggerMillis(item.schedule)
                    .takeIf { it > 0 }
                    ?.let { item to it }
            }
            .minByOrNull { it.second }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (schedules.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                ) {
                    LivingHeader(
                        location = location,
                        isChanting = nowPlaying.isPlaying,
                        onOpenSettings = onOpenSettings,
                        onOpenStats = onOpenStats,
                        onOpenSupport = onOpenSupport,
                    )
                    EmptyState(onAdd = onAdd, modifier = Modifier.weight(1f))
                }
            } else {
                val level = LocalMotion.current
                // Cards leaving the list (delete) keep composing until their
                // exit fade finishes; undo re-inserts the id and the ghost
                // yields to the real card, which re-expands in place.
                var previousSchedules by remember { mutableStateOf(schedules) }
                val exiting = remember {
                    mutableStateMapOf<Long, Pair<ScheduleWithSounds, MutableTransitionState<Boolean>>>()
                }
                LaunchedEffect(schedules) {
                    val old = previousSchedules
                    if (old !== schedules) {
                        val currentIds = schedules.map { it.schedule.id }.toSet()
                        old.filter { prev -> prev.schedule.id !in currentIds }
                            .forEach { gone ->
                                exiting[gone.schedule.id] = gone to MutableTransitionState(true)
                                    .apply { targetState = false }
                            }
                        // Undo (or a same-id re-add): drop the ghost.
                        exiting.keys.retainAll(currentIds)
                        previousSchedules = schedules
                    }
                }
                val ghostItems = exiting.values
                    .map { it.first }
                    .filter { ghost -> schedules.none { it.schedule.id == ghost.schedule.id } }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = if (nowPlaying.isPlaying) 208.dp else 128.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") {
                        LivingHeader(
                            location = location,
                            isChanting = nowPlaying.isPlaying,
                            onOpenSettings = onOpenSettings,
                            onOpenStats = onOpenStats,
                            onOpenSupport = onOpenSupport,
                        )
                    }
                    item(key = "hero") {
                        // The hero crossfades when the next session changes.
                        Crossfade(
                            targetState = nextSession,
                            animationSpec = MotionTokens.fadeTween(level, MotionTokens.DurationMedium),
                            label = "heroSwap",
                        ) { session ->
                            if (session != null) {
                                NextSessionHero(
                                    item = session.first,
                                    triggerAt = session.second,
                                    onClick = { onEdit(session.first.schedule.id) },
                                )
                            }
                        }
                    }
                    missedChantCard?.let { card ->
                        item(key = "missed-chant") {
                            MissedChantBanner(
                                card = card,
                                onFixReliability = onOpenReliability,
                                onDismiss = onDismissMissed,
                            )
                        }
                    }
                    updateCard?.let { card ->
                        item(key = "update-card") {
                            UpdateCardBanner(
                                card = card,
                                onDownload = onUpdateDownload,
                                onLater = onUpdateLater,
                                onSkip = onUpdateSkip,
                            )
                        }
                    }
                    if (needsLocation) {
                        item(key = "location-warning") {
                            LocationWarningBanner(onClick = onOpenSettings)
                        }
                    }
                    items(
                        schedules + ghostItems,
                        key = { it.schedule.id },
                        contentType = { "schedule" },
                    ) { item ->
                        // Staggered entrance (40 ms steps, first six only) —
                        // decorative; input is never blocked.
                        val index = (schedules + ghostItems).indexOfFirst { it.schedule.id == item.schedule.id }
                        val startDelay = (index.coerceAtMost(MotionTokens.StaggerMaxItems) *
                            MotionTokens.StaggerStepMs)
                        val visibleState = exiting[item.schedule.id]?.second
                        val enter: EnterTransition = if (level == MotionLevel.CALM) {
                            fadeIn(MotionTokens.enterTween(level, MotionTokens.DurationMedium, startDelay))
                        } else {
                            fadeIn(MotionTokens.enterTween(level, MotionTokens.DurationShort, startDelay / 2))
                        }
                        val content: @Composable () -> Unit = {
                            ScheduleCard(
                                item = item,
                                isPlayingThis = nowPlaying.isPlayingSchedule(item.schedule.id),
                                location = location,
                                hasMissingSounds = item.sortedSounds.any { it.uri in missingSoundUris } ||
                                    item.sortedSteps.any { it.soundUri in missingSoundUris },
                                onClick = { onEdit(item.schedule.id) },
                                onToggle = { onToggle(item, it) },
                                onPlayToggle = { onPlayToggle(item) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (visibleState != null) {
                            AnimatedVisibility(
                                visibleState = visibleState,
                                enter = enter,
                                exit = fadeOut(MotionTokens.exitTween(level)) +
                                    shrinkVertically(MotionTokens.exitTween(level)),
                            ) { content() }
                        } else {
                            AnimatedVisibility(
                                visible = true,
                                enter = enter,
                            ) { content() }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AddScheduleButton(onClick = onAdd)
            AnimatedVisibility(
                visible = nowPlaying.isPlaying,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NowPlayingPill(
                    nowPlaying = nowPlaying,
                    onStop = onStop,
                    onOpen = onOpenRinging,
                )
            }
        }
    }
}

/** UI model for the update-prompt card (see ScheduleViewModel.updateCard). */
data class UpdateCardUi(
    val version: String,
    val notes: String,
    val downloadUrl: String,
)

/** UI model for the missed-chant card (see ScheduleViewModel.missedChantCard). */
data class MissedChantUi(
    val time: String,
    val weekday: String,
    val label: String,
    val count: Int,
)

/**
 * Gentle, honest card: a scheduled chant never played. One miss names the
 * schedule and day; several collapse into a single count. Offers the
 * reliability settings, never blames the user.
 */
@Composable
private fun MissedChantBanner(
    card: MissedChantUi,
    onFixReliability: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Enter slides down; dismiss slides back up and only then unloads.
    val level = LocalMotion.current
    val visibility = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibility.targetState = true }
    AnimatedVisibility(
        visibleState = visibility,
        enter = slideInVertically(MotionTokens.enterTween(level, MotionTokens.DurationMedium)) { -it / 2 } +
            fadeIn(MotionTokens.enterTween(level, MotionTokens.DurationMedium)),
        exit = slideOutVertically(MotionTokens.exitTween(level)) { -it / 2 } +
            fadeOut(MotionTokens.exitTween(level)),
    ) {
        MissedChantBannerContent(
            card = card,
            onFixReliability = onFixReliability,
            onDismiss = { visibility.targetState = false },
        )
    }
    if (visibility.isIdle && !visibility.currentState) {
        LaunchedEffect(Unit) { onDismiss() }
    }
}

@Composable
private fun MissedChantBannerContent(
    card: MissedChantUi,
    onFixReliability: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val defaultName = stringResource(R.string.default_schedule_name)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                if (card.count == 1) {
                    stringResource(
                        R.string.missed_chant_single,
                        card.time,
                        card.label.ifBlank { defaultName },
                        card.weekday,
                    )
                } else {
                    stringResource(R.string.missed_chant_multiple, card.count)
                },
                style = MaterialTheme.typography.titleMedium,
                fontFamily = UrbanistFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.missed_chant_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFixReliability()
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        stringResource(R.string.missed_chant_fix),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.missed_chant_dismiss),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** Calm, dismissible card at the top of the list: a newer version exists. */
@Composable
private fun UpdateCardBanner(
    card: UpdateCardUi,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val level = LocalMotion.current
    val visibility = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibility.targetState = true }
    AnimatedVisibility(
        visibleState = visibility,
        enter = slideInVertically(MotionTokens.enterTween(level, MotionTokens.DurationMedium)) { -it / 2 } +
            fadeIn(MotionTokens.enterTween(level, MotionTokens.DurationMedium)),
        exit = slideOutVertically(MotionTokens.exitTween(level)) { -it / 2 } +
            fadeOut(MotionTokens.exitTween(level)),
    ) {
        UpdateCardBannerContent(
            card = card,
            onDownload = onDownload,
            onLater = {
                visibility.targetState = false
                onLater()
            },
            onSkip = {
                visibility.targetState = false
                onSkip()
            },
        )
    }
}

@Composable
private fun UpdateCardBannerContent(
    card: UpdateCardUi,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                stringResource(R.string.update_available_title, card.version),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = UrbanistFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (card.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    card.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDownload()
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        stringResource(R.string.update_download),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onLater) {
                    Text(
                        stringResource(R.string.update_later),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.update_skip),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NextSessionHero(    item: ScheduleWithSounds,
    triggerAt: Long,
    onClick: () -> Unit,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val zdt = Instant.ofEpochMilli(triggerAt).atZone(ZoneId.systemDefault())
    // Gentle breathing pulse on the glow behind the time.
    val glowAlpha by rememberAmbientFloat(0.2f, 0.35f, MotionTokens.AmbientPulseMs, "heroGlowAlpha")
    val caption = run {
        val date = zdt.toLocalDate()
        val today = LocalDate.now()
        val relative = when (date) {
            today -> stringResource(R.string.today)
            today.plusDays(1) -> stringResource(R.string.tomorrow)
            else -> date.dayOfWeek.name.lowercase()
                .replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }
        "$relative · ${date.format(FULL_DATE)}"
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(HeroGradientTop, HeroGradientBottom),
                    ),
                ),
        ) {
            // Soft amber glow behind the time.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(220.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(HeroAmber.copy(alpha = glowAlpha), Color.Transparent),
                        ),
                    ),
            )
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.next_session_overline),
                    style = MaterialTheme.typography.labelSmall,
                    color = HeroAmber,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (is24Hour) zdt.format(TIME_24) else zdt.format(TIME_12),
                        style = MaterialTheme.typography.displaySmall,
                        color = HeroText,
                    )
                    if (!is24Hour) {
                        Text(
                            zdt.format(AMPM),
                            style = MaterialTheme.typography.titleMedium,
                            color = HeroText.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    caption.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HeroAmber,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.1f)),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(
                                if (item.isRoutine) {
                                    stringResource(R.string.step_count_summary, item.steps.size)
                                } else {
                                    "♪ " + stringResource(R.string.sound_count, item.sounds.size)
                                },
                            )
                            if (item.schedule.gapSeconds > 0) {
                            append(" · " + stringResource(R.string.gap_seconds, item.schedule.gapSeconds))
                        }
                            if (item.schedule.triggerType != TriggerType.FIXED) {
                                append(" · ${sunTriggerCaption(item.schedule)}")
                            }
                            if (!item.schedule.triggerType.isLunarType) {
                                append(" · ${repeatSummaryText(item.schedule.daysOfWeek)}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = HeroText.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.edit),
                        style = MaterialTheme.typography.labelLarge,
                        color = HeroAmber,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddScheduleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .accentGlow(RoundedCornerShape(16.dp))
            .pressScale(interactionSource),
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.add_schedule), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    // Slow floating drift on the decorative icon.
    val floatY by rememberAmbientFloat(-4f, 4f, MotionTokens.AmbientPulseMs, "emptyFloatY")
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            AppIcons.Sunrise,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { translationY = floatY.dp.toPx() },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.no_schedules_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.no_schedules_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            interactionSource = interactionSource,
            modifier = Modifier
                .accentGlow(RoundedCornerShape(16.dp))
                .pressScale(interactionSource),
        ) {
            Text(stringResource(R.string.create_schedule), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LocationWarningBanner(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Sunrise,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.location_needed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.location_needed_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleCard(
    item: ScheduleWithSounds,
    isPlayingThis: Boolean,
    location: LatLng?,
    hasMissingSounds: Boolean = false,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val schedule = item.schedule
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val sunTime = remember(schedule.triggerType, schedule.offsetMinutes, location) {
        todaySunInstant(schedule, location)
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cardOverline(schedule).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.accentGlow(
                        RoundedCornerShape(50),
                        enabled = schedule.enabled,
                    ),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    when {
                        schedule.triggerType == TriggerType.FIXED ->
                            if (is24Hour) {
                                formatTime(schedule.hour, schedule.minute)
                            } else {
                                "%d:%02d".format(
                                    if (schedule.hour % 12 == 0) 12 else schedule.hour % 12,
                                    schedule.minute,
                                )
                            }
                        sunTime != null ->
                            "~" + sunTime.format(if (is24Hour) TIME_24 else TIME_12)
                        schedule.triggerType == TriggerType.SUNRISE -> stringResource(R.string.trigger_sunrise)
                        schedule.triggerType == TriggerType.FULL_MOON -> stringResource(R.string.trigger_full_moon)
                        schedule.triggerType == TriggerType.NEW_MOON -> stringResource(R.string.trigger_new_moon)
                        else -> stringResource(R.string.trigger_sunset)
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = UrbanistFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (schedule.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!is24Hour && (schedule.triggerType == TriggerType.FIXED ||
                        schedule.triggerType == TriggerType.FULL_MOON ||
                        schedule.triggerType == TriggerType.NEW_MOON)
                ) {
                    Text(
                        if (schedule.hour < 12) "am" else "pm",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                    )
                } else if (!is24Hour && sunTime != null) {
                    Text(
                        sunTime.format(AMPM),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(
                            if (item.isRoutine) {
                                stringResource(R.string.step_count_summary, item.steps.size)
                            } else {
                                "♪ " + stringResource(R.string.sound_count, item.sounds.size)
                            },
                        )
                        if (schedule.gapSeconds > 0) {
                            append(" · " + stringResource(R.string.gap_seconds, schedule.gapSeconds))
                        }
                        if (schedule.triggerType != TriggerType.FIXED) {
                            append(" · ${sunTriggerCaption(schedule)}")
                        }
                        if (!schedule.triggerType.isLunarType) {
                            append(" · ${repeatSummaryText(schedule.daysOfWeek)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PlayToggle(
                    isPlaying = isPlayingThis,
                    enabled = item.sounds.isNotEmpty() || item.steps.isNotEmpty(),
                    onClick = onPlayToggle,
                )
            }
            if (hasMissingSounds) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            AppIcons.Alert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.repick_sounds_chip),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayToggle(isPlaying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .size(48.dp)
            .accentGlow(CircleShape, enabled = isPlaying && enabled),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val level = LocalMotion.current
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    fadeIn(MotionTokens.fadeTween(level, MotionTokens.DurationMicro)) togetherWith
                        fadeOut(MotionTokens.fadeTween(level, MotionTokens.DurationMicro))
                },
                label = "playStopIcon",
            ) { playing ->
                Icon(
                    if (playing) AppIcons.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (playing) stringResource(R.string.stop) else stringResource(R.string.play_now),
                    tint = if (playing) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingPill(
    nowPlaying: NowPlaying,
    onStop: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pulsing accent ring marks the active session.
    val ringAlpha by rememberAmbientFloat(0.35f, 1f, MotionTokens.AmbientPulseMs, "ringAlpha")
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
        ),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot()
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    nowPlaying.label.ifBlank { stringResource(R.string.default_session_name) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Track ${nowPlaying.currentTrack} of ${nowPlaying.totalTracks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onStop,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Stop,
                        contentDescription = stringResource(R.string.stop_playback),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val alpha by rememberAmbientFloat(0.25f, 1f, MotionTokens.AmbientPulseMs, "dot")
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
    )
}
