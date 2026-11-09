package com.chantingscheduler.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

private val RobeSaffron = Color(0xFFE8963C)
private val RobeDark = Color(0xFFC97B2A)
private val Skin = Color(0xFFF2C9A4)
private val SkinLine = Color(0xFF8A5A33)
private val ArcAmber = Color(0xFFFFD9A0)

/**
 * Cute young monk sitting cross-legged in meditation, drawn as flat vector
 * art. Breathes gently (scale/translate) while a soft sound-arc pulses above
 * the prayer hands. This is the single source of truth for the mascot; the
 * launcher icon mirrors the same shapes as a vector drawable.
 */
@Composable
fun MonkArt(modifier: Modifier = Modifier, animate: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "monk")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "monkBreath",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "monkPulse",
    )
    val b = if (animate) breath else 0.5f
    val arcAlpha = if (animate) pulse else 0.6f

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val scale = 1f + (b - 0.5f) * 0.03f
        val ty = (b - 0.5f) * h * 0.012f
        val handGap = (b - 0.5f) * w * 0.012f

        withTransform({
            scale(scale, scale, pivot = Offset(w / 2f, h * 0.8f))
            translate(0f, ty)
        }) {
            // Cross-legged base.
            drawOval(
                color = RobeDark,
                topLeft = Offset(w * 0.16f, h * 0.70f),
                size = Size(w * 0.68f, h * 0.18f),
            )
            // Robe body: shoulders narrowing up to the neck.
            val body = Path().apply {
                moveTo(w * 0.28f, h * 0.80f)
                quadraticBezierTo(w * 0.24f, h * 0.46f, w * 0.50f, h * 0.42f)
                quadraticBezierTo(w * 0.76f, h * 0.46f, w * 0.72f, h * 0.80f)
                close()
            }
            drawPath(body, RobeSaffron)
            // Robe fold.
            val fold = Path().apply {
                moveTo(w * 0.50f, h * 0.44f)
                lineTo(w * 0.58f, h * 0.80f)
                lineTo(w * 0.46f, h * 0.80f)
                close()
            }
            drawPath(fold, RobeDark.copy(alpha = 0.55f))
            // Prayer hands.
            drawCircle(Skin, radius = w * 0.040f, center = Offset(w * 0.462f - handGap, h * 0.585f))
            drawCircle(Skin, radius = w * 0.040f, center = Offset(w * 0.538f + handGap, h * 0.585f))
            // Head.
            drawCircle(Skin, radius = w * 0.135f, center = Offset(w * 0.5f, h * 0.295f))
            // Closed happy eyes.
            val eyeStroke = Stroke(width = w * 0.011f, cap = StrokeCap.Round)
            drawArc(
                color = SkinLine,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(w * 0.415f, h * 0.278f),
                size = Size(w * 0.045f, h * 0.030f),
                style = eyeStroke,
            )
            drawArc(
                color = SkinLine,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(w * 0.540f, h * 0.278f),
                size = Size(w * 0.045f, h * 0.030f),
                style = eyeStroke,
            )
            // Smile.
            drawArc(
                color = SkinLine,
                startAngle = 30f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(w * 0.475f, h * 0.320f),
                size = Size(w * 0.050f, h * 0.030f),
                style = eyeStroke,
            )
        }

        // Pulsing sound arcs above the prayer hands.
        val arcStroke = Stroke(width = w * 0.014f, cap = StrokeCap.Round)
        drawArc(
            color = ArcAmber.copy(alpha = arcAlpha),
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(w * 0.44f, h * 0.475f),
            size = Size(w * 0.12f, h * 0.12f * 0.7f),
            style = arcStroke,
        )
        drawArc(
            color = ArcAmber.copy(alpha = arcAlpha * 0.6f),
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(w * 0.405f, h * 0.445f),
            size = Size(w * 0.19f, h * 0.19f * 0.7f),
            style = arcStroke,
        )
    }
}
