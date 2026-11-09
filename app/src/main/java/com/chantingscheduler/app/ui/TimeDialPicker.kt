package com.chantingscheduler.app.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chantingscheduler.app.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DIAL_HEIGHT = 116.dp
private const val STEP_ANGLE_DEG = 15f
private const val VISIBLE_ANGLE_DEG = 72f
private val DRAG_PX_PER_STEP = 30.dp
private val SLOT_WIDTH = 64.dp

/**
 * Rotating-dial time picker: two horizontally-draggable arc dials (hours on
 * top, minutes below). Numbers ride a semicircular arc; the value at the arc's
 * center is the selection, rendered large and bright while siblings shrink
 * and fade along the curve. Dragging rotates the dial; on release it snaps to
 * the nearest value with a spring and a haptic tick.
 *
 * Accessibility: each dial is exposed as an adjustable progress control
 * ("Hour, 7"), and a "Type time" button opens the Material time-picker input
 * dialog for users who can't (or prefer not to) drag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialPicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val isPm = hour >= 12
    var showKeyboardPicker by remember { mutableStateOf(false) }

    if (showKeyboardPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour.coerceIn(0, 23),
            initialMinute = minute.coerceIn(0, 59),
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = { showKeyboardPicker = false },
            title = { Text(stringResource(R.string.enter_time_title)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showKeyboardPicker = false
                    onTimeChange(pickerState.hour, pickerState.minute)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showKeyboardPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Column(modifier.fillMaxWidth()) {
        if (!is24Hour) {
            Row(
                Modifier.align(Alignment.CenterHorizontally),
            ) {
                MeridiemPill(
                    text = stringResource(R.string.am),
                    selected = !isPm,
                    onClick = { if (isPm) onTimeChange(hour - 12, minute) },
                )
                Spacer(Modifier.width(8.dp))
                MeridiemPill(
                    text = stringResource(R.string.pm),
                    selected = isPm,
                    onClick = { if (!isPm) onTimeChange(hour + 12, minute) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        DialOverline(stringResource(R.string.hours_label))
        if (is24Hour) {
            ArcDial(
                count = 24,
                selected = hour.coerceIn(0, 23),
                itemText = { "%02d".format(it) },
                label = stringResource(R.string.a11y_hour),
                onSelectedChange = { onTimeChange(it, minute) },
            )
        } else {
            ArcDial(
                count = 12,
                selected = (hour % 12 + 11) % 12,
                itemText = { "${it + 1}" },
                label = stringResource(R.string.a11y_hour),
                onSelectedChange = { index ->
                    val hour12 = index + 1
                    val newHour = if (isPm) hour12 % 12 + 12 else hour12 % 12
                    onTimeChange(newHour, minute)
                },
            )
        }

        Spacer(Modifier.height(4.dp))
        DialOverline(stringResource(R.string.minutes_label))
        ArcDial(
            count = 60,
            selected = minute.coerceIn(0, 59),
            itemText = { "%02d".format(it) },
            label = stringResource(R.string.a11y_minute),
            onSelectedChange = { onTimeChange(hour, it) },
        )
        TextButton(
            onClick = { showKeyboardPicker = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.type_time))
        }
    }
}

@Composable
private fun DialOverline(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ArcDial(
    count: Int,
    selected: Int,
    itemText: (Int) -> String,
    label: String,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val position = remember { Animatable(selected.toFloat()) }

    // The index at the arc's center right now (wraps around the dial).
    val centerIndex by remember {
        derivedStateOf {
            ((position.value.roundToInt() % count) + count) % count
        }
    }

    // Commit + haptic whenever the center value changes under the pointer.
    LaunchedEffect(centerIndex) {
        if (centerIndex != selected) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectedChange(centerIndex)
        }
    }

    // Follow external selection changes (e.g. AM/PM flip, initial load) by
    // snapping along the shortest path around the dial.
    LaunchedEffect(selected) {
        if (centerIndex != selected) {
            val delta = shortestDelta(centerIndex, selected, count)
            position.snapTo(position.value + delta)
        }
    }

    val pxPerStep = with(density) { DRAG_PX_PER_STEP.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(DIAL_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .semantics {
                // Expose the dial as an adjustable progress control so it can
                // be changed with TalkBack swipe gestures without dragging.
                val valueText = itemText(((selected % count) + count) % count)
                contentDescription = "$label, $valueText"
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = selected.coerceIn(0, count - 1).toFloat(),
                    range = 0f..(count - 1).toFloat(),
                )
                setProgress { target ->
                    onSelectedChange(target.roundToInt().mod(count))
                    true
                }
            }
            .pointerInputBounds(
                onDrag = { dx ->
                    scope.launch { position.snapTo(position.value - dx / pxPerStep) }
                },
                onDragEnd = {
                    scope.launch {
                        position.animateTo(
                            targetValue = position.value.roundToInt().toFloat(),
                            animationSpec = spring(),
                        )
                    }
                },
                onDragStart = { scope.launch { position.stop() } },
            ),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val cx = widthPx / 2f
        val arcOriginY = heightPx * 2.6f
        val radius = arcOriginY - with(density) { 20.dp.toPx() }
        val slotPx = with(density) { SLOT_WIDTH.toPx() }

        for (i in 0 until count) {
            val delta = (i - position.value).wrappedDelta(count)
            val angleDeg = delta * STEP_ANGLE_DEG
            if (kotlin.math.abs(angleDeg) > VISIBLE_ANGLE_DEG) continue
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val x = (radius * sin(angleRad)).toFloat()
            if (kotlin.math.abs(x) > widthPx / 2f) continue
            val y = (arcOriginY - radius * cos(angleRad)).toFloat()
            val t = (kotlin.math.abs(angleDeg) / VISIBLE_ANGLE_DEG).coerceIn(0f, 1f)
            val isCenter = i == centerIndex

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = cx + x - slotPx / 2f
                        translationY = y - with(density) { 22.dp.toPx() }
                        alpha = if (isCenter) 1f else 1f - t * 0.85f
                    }
                    .width(SLOT_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    itemText(i),
                    fontSize = if (isCenter) 44.sp else (26f - t * 12f).sp,
                    fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isCenter) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** Shortest signed step distance from [from] to [to] on a ring of [count]. */
private fun shortestDelta(from: Int, to: Int, count: Int): Int {
    var d = (to - from) % count
    if (d > count / 2) d -= count
    if (d < -count / 2) d += count
    return d
}

/** Signed distance of [value] to the nearest integer on a ring of [count]. */
private fun Float.wrappedDelta(count: Int): Float {
    var d = this % count
    if (d > count / 2f) d -= count
    if (d < -count / 2f) d += count
    return d
}

private fun Modifier.pointerInputBounds(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragStart: () -> Unit,
): Modifier = pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { onDragStart() },
        onDragEnd = { onDragEnd() },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            onDrag(dragAmount)
        },
    )
}

@Composable
private fun MeridiemPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
