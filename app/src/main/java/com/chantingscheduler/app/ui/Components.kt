package com.chantingscheduler.app.ui

import com.chantingscheduler.app.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import com.chantingscheduler.app.ui.motion.LocalMotion
import com.chantingscheduler.app.ui.motion.MotionTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private val DAY_SHORT_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Compact repeat summary for a days-of-week bitmask (Monday = bit 0):
 * "Every day", "Mon–Fri", "Weekends", "Mon, Wed, Fri", or "Never".
 */
@Composable
fun repeatSummaryText(daysOfWeek: Int): String = when {
    daysOfWeek == 0 -> stringResource(R.string.repeat_never)
    daysOfWeek == 0b1111111 -> stringResource(R.string.repeat_every_day)
    daysOfWeek == 0b0011111 -> stringResource(R.string.repeat_mon_fri)
    daysOfWeek == 0b1100000 -> stringResource(R.string.repeat_weekends)
    else -> DAY_SHORT_NAMES.filterIndexed { i, _ -> daysOfWeek and (1 shl i) != 0 }
        .joinToString(", ")
}

/** True when the effective (possibly user-overridden) theme is dark. */
@Composable
fun isDarkThemeActive(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Soft accent halo behind a component in dark theme (Behance-style glow).
 * No-op in light theme or when [enabled] is false.
 */
fun Modifier.accentGlow(shape: Shape, enabled: Boolean = true): Modifier =
    composed {
        val glow = isDarkThemeActive() && enabled
        val accent = MaterialTheme.colorScheme.primary
        if (glow) {
            this.shadow(
                elevation = 14.dp,
                shape = shape,
                clip = false,
                ambientColor = accent,
                spotColor = accent,
            )
        } else {
            this
        }
    }

/**
 * Shared press feedback: subtle scale-down while pressed, graphicsLayer-only
 * (no layout passes). The one place the app defines press depth
 * ([MotionTokens.PressScale]). Pass the same [interactionSource] to the
 * clickable.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MotionTokens.PressScale else 1f,
        animationSpec = MotionTokens.springToggle(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Small-caps section header, 12sp tracked, muted. */
@Composable
fun SectionOverline(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Circular day chip. Active: tinted accent background + accent text.
 * Inactive: transparent + muted text. Colors and scale animate on selection.
 * Sized by caller (44dp interactive, 26dp display-only).
 */
@Composable
fun DayChip(
    name: String,
    active: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = MotionTokens.enterTween(LocalMotion.current, MotionTokens.DurationShort),
        label = "dayChipBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MotionTokens.enterTween(LocalMotion.current, MotionTokens.DurationShort),
        label = "dayChipContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = MotionTokens.springToggle(),
        label = "dayChipScale",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
            color = contentColor,
        )
    }
}
