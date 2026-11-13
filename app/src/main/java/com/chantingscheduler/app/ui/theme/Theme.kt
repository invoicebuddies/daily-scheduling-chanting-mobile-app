package com.chantingscheduler.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chantingscheduler.app.R

// Summit-inspired palette.
// Light: warm off-white, white cards, deep teal accent.
// Dark: deep teal-green, elevated teal cards, muted sage accent, white text.

val UrbanistFamily = FontFamily(
    Font(R.font.urbanist, FontWeight.Normal),
    Font(R.font.urbanist, FontWeight.Medium),
    Font(R.font.urbanist, FontWeight.SemiBold),
    Font(R.font.urbanist, FontWeight.Bold),
    Font(R.font.urbanist, FontWeight.ExtraBold),
    Font(R.font.urbanist, FontWeight.Black),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5E57),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E6E1),
    onPrimaryContainer = Color(0xFF0E2A25),
    secondary = Color(0xFF3D6B63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8EAE5),
    onSecondaryContainer = Color(0xFF10201D),
    background = Color(0xFFF4F3EF),
    onBackground = Color(0xFF17171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17171C),
    surfaceVariant = Color(0xFFECEAE3),
    onSurfaceVariant = Color(0xFF5F6B67),
    outline = Color(0xFFD8D6CE),
    outlineVariant = Color(0xFFE7E5DD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6E9A92),
    onPrimary = Color(0xFF0B1514),
    primaryContainer = Color(0xFF26433E),
    onPrimaryContainer = Color(0xFFD8E8E4),
    secondary = Color(0xFF8FB3AC),
    onSecondary = Color(0xFF0B1514),
    secondaryContainer = Color(0xFF223936),
    onSecondaryContainer = Color(0xFFD8E8E4),
    background = Color(0xFF0E1B1A),
    onBackground = Color(0xFFF4F8F7),
    surface = Color(0xFF182826),
    onSurface = Color(0xFFF4F8F7),
    surfaceVariant = Color(0xFF20322F),
    onSurfaceVariant = Color(0xFFA4BCB6),
    outline = Color(0xFF33504A),
    outlineVariant = Color(0xFF24403A),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp,
        letterSpacing = (-1).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = UrbanistFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
    ),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/**
 * Crossfades every color role when the scheme changes (theme toggle), so the
 * whole UI fades smoothly between light and dark instead of snapping.
 */
@Composable
private fun ColorScheme.withAnimatedColors(): ColorScheme {
    val level = com.chantingscheduler.app.ui.motion.LocalMotion.current

    @Composable
    fun anim(color: Color): Color =
        animateColorAsState(
            targetValue = color,
            animationSpec = com.chantingscheduler.app.ui.motion.MotionTokens.enterTween(level, 440),
            label = "themeColor",
        ).value

    return copy(
        primary = anim(primary),
        onPrimary = anim(onPrimary),
        primaryContainer = anim(primaryContainer),
        onPrimaryContainer = anim(onPrimaryContainer),
        secondary = anim(secondary),
        onSecondary = anim(onSecondary),
        secondaryContainer = anim(secondaryContainer),
        onSecondaryContainer = anim(onSecondaryContainer),
        background = anim(background),
        onBackground = anim(onBackground),
        surface = anim(surface),
        onSurface = anim(onSurface),
        surfaceVariant = anim(surfaceVariant),
        onSurfaceVariant = anim(onSurfaceVariant),
        outline = anim(outline),
        outlineVariant = anim(outlineVariant),
        error = anim(error),
        onError = anim(onError),
        errorContainer = anim(errorContainer),
        onErrorContainer = anim(onErrorContainer),
    )
}

@Composable
fun ChantingSchedulerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = (if (darkTheme) DarkColors else LightColors).withAnimatedColors(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
