package com.chantingscheduler.app.ui

import com.chantingscheduler.app.R
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.scheduling.SunTimesCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

// Morning (dawn) backdrop colors.
private val DawnTop = Color(0xFFFFE3B3)
private val DawnBottom = Color(0xFFF4A76B)
private val DawnInk = Color(0xFF4A2C1A)

// Evening (dusk) backdrop colors.
private val DuskTop = Color(0xFF131A3A)
private val DuskBottom = Color(0xFF2A2C55)
private val DuskInk = Color(0xFFF1EFFF)

/**
 * Living overview header: greeting over a time-of-day backdrop (dawn vs dusk,
 * computed from sunrise/sunset when a location is set) with the breathing
 * hand-drawn monk. Decorative only — list content stays below it.
 */
@Composable
fun LivingHeader(
    location: LatLng?,
    isChanting: Boolean,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSupport: () -> Unit = {},
) {
    val morning = remember(location) { isMorningNow(location) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(290.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Crossfade(targetState = morning, label = "daypart") { isMorning ->
            if (isMorning) {
                DawnBackdrop()
            } else {
                DuskBackdrop()
            }
        }

        val ink = if (morning) DawnInk else DuskInk
        Column(Modifier.padding(20.dp).padding(end = 48.dp)) {
            Text(
                greeting(),
                style = MaterialTheme.typography.headlineMedium,
                color = ink,
            )
            Text(
                stringResource(R.string.header_subtitle, LocalDate.now().format(FULL_DATE)),
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = onOpenSupport) {
                Icon(
                    AppIcons.Heart,
                    contentDescription = stringResource(R.string.content_desc_support),
                    tint = ink.copy(alpha = 0.8f),
                )
            }
            IconButton(onClick = onOpenStats) {
                Icon(
                    AppIcons.Chart,
                    contentDescription = stringResource(R.string.content_desc_stats),
                    tint = ink.copy(alpha = 0.8f),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    AppIcons.SettingsGear,
                    contentDescription = stringResource(R.string.content_desc_settings),
                    tint = ink.copy(alpha = 0.8f),
                )
            }
        }
        val mascotModifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp)
            .size(150.dp)
        MonkArt(modifier = mascotModifier, animate = !rememberReduceMotion())
    }
}

/** Summit-style greeting by device hour. */
@Composable
private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> stringResource(R.string.good_morning)
    in 12..17 -> stringResource(R.string.good_afternoon)
    else -> stringResource(R.string.good_evening)
}

/**
 * True when it is "daytime" right now: between today's sunrise and sunset
 * when a location is set (preferred), else 05:00–17:00 device time.
 */
private fun isMorningNow(location: LatLng?): Boolean {
    if (location != null) {
        val today = LocalDate.now()
        val sunrise = SunTimesCalculator.sunrise(today, location)
        val sunset = SunTimesCalculator.sunset(today, location)
        if (sunrise != null && sunset != null) {
            val now = ZonedDateTime.now(sunrise.zone)
            return if (sunrise.isBefore(sunset)) {
                !now.isBefore(sunrise) && now.isBefore(sunset)
            } else {
                // Polar day/night edge: sun times wrapped past midnight.
                !now.isBefore(sunrise) || now.isBefore(sunset)
            }
        }
    }
    return LocalTime.now().hour in 5..16
}

@Composable
private fun DawnBackdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DawnTop, DawnBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Rising-sun glow, top-left.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFF1D6).copy(alpha = 0.95f), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.22f),
                    radius = size.width * 0.45f,
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.78f, size.height * 0.22f),
            )
            drawCircle(
                Color(0xFFFFF6E0),
                radius = size.width * 0.09f,
                center = Offset(size.width * 0.78f, size.height * 0.22f),
            )
            // Light haze bands.
            val haze = Color.White.copy(alpha = 0.18f)
            drawOval(
                haze,
                topLeft = Offset(size.width * 0.05f, size.height * 0.42f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.07f),
            )
            drawOval(
                haze,
                topLeft = Offset(size.width * 0.3f, size.height * 0.58f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.75f, size.height * 0.06f),
            )
        }
    }
}

@Composable
private fun DuskBackdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DuskTop, DuskBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Stars.
            val star = Color.White.copy(alpha = 0.8f)
            listOf(
                Offset(0.12f, 0.14f), Offset(0.30f, 0.30f), Offset(0.22f, 0.52f),
                Offset(0.52f, 0.12f), Offset(0.62f, 0.38f), Offset(0.42f, 0.60f),
                Offset(0.85f, 0.48f), Offset(0.06f, 0.70f),
            ).forEach { p ->
                drawCircle(star, radius = 1.8.dp.toPx(), center = Offset(p.x * size.width, p.y * size.height))
            }
            // Crescent moon, top-right.
            val moon = Offset(size.width * 0.78f, size.height * 0.24f)
            val r = size.width * 0.085f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFF3C4).copy(alpha = 0.3f), Color.Transparent),
                    center = moon,
                    radius = r * 2.4f,
                ),
                radius = r * 2.4f,
                center = moon,
            )
            drawCircle(Color(0xFFFFF3C4), radius = r, center = moon)
            drawCircle(
                DuskTop,
                radius = r * 0.85f,
                center = Offset(moon.x + r * 0.45f, moon.y - r * 0.2f),
            )
        }
    }
}
