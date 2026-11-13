package com.chantingscheduler.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.chantingscheduler.app.R
import com.chantingscheduler.app.data.SessionEndReason
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chantingscheduler.app.ui.theme.UrbanistFamily
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val WEEKDAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** Practice stats: streak, mindful minutes, and a soft month calendar. */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val viewModel: StatsViewModel = viewModel()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    StatsContent(stats = stats, onBack = onBack, onUpdateNote = viewModel::updateSessionNote)
}

@Composable
private fun StatsContent(
    stats: StatsUiState,
    onBack: () -> Unit,
    onUpdateNote: (Long, String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(20.dp))

        if (stats.sessionCount == 0) {
            EmptyStats()
            return@Column
        }

        StreakCard(
            value = stats.currentStreak,
            label = stringResource(
                if (stats.currentStreak == 1) {
                    R.string.stats_day_in_a_row
                } else {
                    R.string.stats_days_in_a_row
                },
            ),
        )

        Spacer(Modifier.height(12.dp))
        StreakCard(
            value = stats.longestStreak,
            label = stringResource(R.string.stats_longest_streak),
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MinutesCard(
                label = stringResource(R.string.stats_this_week),
                minutes = stats.weekMinutes,
                modifier = Modifier.weight(1f),
            )
            MinutesCard(
                label = stringResource(R.string.stats_all_time),
                minutes = stats.totalMinutes,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))
        MonthCalendarCard(minutesByDay = stats.minutesByDay)

        if (stats.history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HistoryCard(items = stats.history, onUpdateNote = onUpdateNote)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyStats() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                AppIcons.Flame,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.stats_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stats_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StreakCard(value: Int, label: String) {
    val displayValue = com.chantingscheduler.app.ui.motion.rememberCountUp(value)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Flame,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    "$displayValue",
                    fontFamily = UrbanistFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Recent sessions: date, schedule label, duration, how they ended, and an
 * optional journal note (tap a row to add, edit, or clear it). */
@Composable
private fun HistoryCard(
    items: List<SessionHistoryItem>,
    onUpdateNote: (Long, String?) -> Unit,
) {
    val datePattern = stringResource(R.string.stats_history_date_format)
    val dateFormat = remember(datePattern) { DateTimeFormatter.ofPattern(datePattern) }
    val zone = remember { ZoneId.systemDefault() }
    var noteTarget by remember { mutableStateOf<SessionHistoryItem?>(null) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_history_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            items.forEach { item ->
                val date = Instant.ofEpochMilli(item.startedAtEpochMs).atZone(zone)
                    .toLocalDate().format(dateFormat)
                val reason = stringResource(
                    when (item.endReason) {
                        SessionEndReason.COMPLETED -> R.string.end_reason_completed
                        SessionEndReason.STOPPED -> R.string.end_reason_stopped
                        SessionEndReason.SNOOZED -> R.string.end_reason_snoozed
                    },
                )
                Surface(
                    onClick = { noteTarget = item },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
                        Text(
                            if (item.label != null) {
                                stringResource(
                                    R.string.stats_history_with_label,
                                    date,
                                    item.label,
                                    item.minutes,
                                    reason,
                                )
                            } else {
                                stringResource(R.string.stats_history_no_label, date, item.minutes, reason)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.note?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    noteTarget?.let { target ->
        HistoryNoteDialog(
            item = target,
            onSave = { newNote ->
                onUpdateNote(target.logId, newNote)
                noteTarget = null
            },
            onDismiss = { noteTarget = null },
        )
    }
}

/** Small quiet dialog: one optional line about a past session. */
@Composable
private fun HistoryNoteDialog(
    item: SessionHistoryItem,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(item.logId) { mutableStateOf(item.note.orEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_note_dialog_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= NOTE_MAX_LENGTH) text = it },
                placeholder = { Text(stringResource(R.string.session_note_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim().ifBlank { null }) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (!item.note.isNullOrBlank()) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

private const val NOTE_MAX_LENGTH = 200

@Composable
private fun MinutesCard(label: String, minutes: Int, modifier: Modifier = Modifier) {
    val displayMinutes = com.chantingscheduler.app.ui.motion.rememberCountUp(minutes)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$displayMinutes",
                fontFamily = UrbanistFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.stats_mindful_min),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Current-month grid; cells tinted by mindful minutes (accent intensity). */
@Composable
private fun MonthCalendarCard(minutesByDay: Map<LocalDate, Int>) {
    val month = remember { YearMonth.now() }
    val firstOffset = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
    val daysInMonth = month.lengthOfMonth()
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())
                    .replaceFirstChar { it.uppercase(Locale.getDefault()) },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                WEEKDAY_LETTERS.forEach { letter ->
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val cells = firstOffset + daysInMonth
            val rows = (cells + 6) / 7
            val level = com.chantingscheduler.app.ui.motion.LocalMotion.current
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(rows) { row ->
                    // Calendar week rows fade in, top row first.
                    val rowAlpha = if (level == com.chantingscheduler.app.ui.motion.MotionLevel.CALM) {
                        val a by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = com.chantingscheduler.app.ui.motion.MotionTokens.enterTween(
                                level,
                                com.chantingscheduler.app.ui.motion.MotionTokens.DurationShort,
                                row * 60,
                            ),
                            label = "weekRow",
                        )
                        a
                    } else {
                        1f
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = rowAlpha },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(7) { column ->
                            val day = row * 7 + column - firstOffset + 1
                            if (day < 1 || day > daysInMonth) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                DayCell(
                                    day = day,
                                    minutes = minutesByDay[month.atDay(day)],
                                    isToday = month.atDay(day) == LocalDate.now(),
                                    accent = accent,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    minutes: Int?,
    isToday: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val fill = when {
        minutes == null -> accent.copy(alpha = 0f)
        minutes < 10 -> accent.copy(alpha = 0.20f)
        minutes < 30 -> accent.copy(alpha = 0.40f)
        minutes < 60 -> accent.copy(alpha = 0.65f)
        else -> accent.copy(alpha = 0.90f)
    }
    val textColor = when {
        minutes == null || minutes < 30 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (isToday && minutes == null) {
                MaterialTheme.colorScheme.primary
            } else {
                textColor
            },
        )
    }
}
