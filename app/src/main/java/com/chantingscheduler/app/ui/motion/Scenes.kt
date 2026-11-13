package com.chantingscheduler.app.ui.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chantingscheduler.app.data.MotionStore
import com.chantingscheduler.app.R
import com.chantingscheduler.app.ui.MonkArt
import com.chantingscheduler.app.ui.isDarkThemeActive

/** User-selectable header / ringing-page artwork. */
enum class ArtScene(val storedName: String) {
    MONK("monk"),
    LOTUS("lotus"),
    TEMPLE("temple"),
    ABSTRACT("abstract"),
    ;

    companion object {
        fun fromStored(value: String?): ArtScene =
            entries.firstOrNull { it.storedName == value?.lowercase() } ?: MONK
    }
}

/** Sky colors for a [DayPhase], tinted for dark/light theme. */
@Composable
fun sceneBackdropColors(phase: DayPhase): List<Color> {
    val dark = isDarkThemeActive()
    return when (phase) {
        DayPhase.DAWN -> if (dark) {
            listOf(Color(0xFF3A2A3A), Color(0xFF7A4A3A), Color(0xFFB9764A))
        } else {
            listOf(Color(0xFFFFE3B3), Color(0xFFF7B77B), Color(0xFFE8946B))
        }
        DayPhase.DAY -> if (dark) {
            listOf(Color(0xFF1C3A42), Color(0xFF2E5A5E), Color(0xFF3F6E68))
        } else {
            listOf(Color(0xFFBFE0E4), Color(0xFF9CCDD4), Color(0xFF7FB9C4))
        }
        DayPhase.DUSK -> if (dark) {
            listOf(Color(0xFF241C3E), Color(0xFF4A2C4E), Color(0xFF7A4030))
        } else {
            listOf(Color(0xFFF2C49B), Color(0xFFE08E63), Color(0xFF9C5A50))
        }
        DayPhase.NIGHT -> if (dark) {
            listOf(Color(0xFF0B1030), Color(0xFF1E2A5A), Color(0xFF3A2A5E))
        } else {
            listOf(Color(0xFF3C4670), Color(0xFF5A5E8E), Color(0xFF7A6E9E))
        }
    }
}

/** Backdrop sky for a [DayPhase], tinted for dark/light theme. Drawn behind the
 * scene artwork; per-trigger ringing pages override with their own gradient. */
@Composable
fun sceneBackdropBrush(phase: DayPhase): Brush = Brush.verticalGradient(sceneBackdropColors(phase))

/** Ink (silhouette) and accent colors per phase; theme-aware. */
private data class ScenePalette(val ink: Color, val accent: Color, val soft: Color)

@Composable
private fun scenePalette(phase: DayPhase): ScenePalette {
    val dark = isDarkThemeActive()
    return when (phase) {
        DayPhase.DAWN -> ScenePalette(
            ink = if (dark) Color(0xFF2A1C2E) else Color(0xFF6B3B2A),
            accent = Color(0xFFFFD9A0),
            soft = Color(0xFFE8A35C),
        )
        DayPhase.DAY -> ScenePalette(
            ink = if (dark) Color(0xFF12262A) else Color(0xFF2E5E57),
            accent = Color(0xFFFFF3C4),
            soft = Color(0xFF7FB3C8),
        )
        DayPhase.DUSK -> ScenePalette(
            ink = if (dark) Color(0xFF1A1430) else Color(0xFF4A2C3E),
            accent = Color(0xFFFFC36B),
            soft = Color(0xFFB4556B),
        )
        DayPhase.NIGHT -> ScenePalette(
            ink = if (dark) Color(0xFF0A0E26) else Color(0xFF2A2E52),
            accent = Color(0xFFFFF3C4),
            soft = Color(0xFF8E9AC8),
        )
    }
}

/**
 * One scene of artwork drawn with Canvas only. [animate] should come from
 * [rememberAmbientActive] so loops pause off-screen, in the background, at
 * Minimal/Off, and under sheets. Decorative: TalkBack sees a single
 * [contentDescription]; callers may clear semantics when nested in a
 * described element.
 */
@Composable
fun SceneArt(
    scene: ArtScene,
    phase: DayPhase,
    modifier: Modifier = Modifier,
    animate: Boolean = rememberAmbientActive(),
    contentDescription: String? = null,
) {
    val palette = scenePalette(phase)

    // One infinite transition for the whole scene, only composed while
    // animating — at Minimal/Off no infinite transition ever exists.
    val breath: Float
    val drift: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "scene")
        val b by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = MotionTokens.ambientTween(MotionTokens.BreathCycleMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sceneBreath",
        )
        val d by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = MotionTokens.ambientTween(MotionTokens.AmbientDriftMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sceneDrift",
        )
        breath = b
        drift = d
    } else {
        breath = 0.5f
        drift = 0.3f
    }
    trackAmbient(animate, label = "scene:${scene.name}")

    // Unit-space paths, built once per scene — no per-frame allocation.
    val lotus = remember { LotusPaths() }
    val temple = remember { TemplePaths() }

    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }

    if (scene == ArtScene.MONK) {
        MonkArt(modifier.then(semantics), animate = animate)
        return
    }
    // Lotus petals keep their own hue (a soft pink) so they read on any
    // backdrop; everything else tints from the phase palette.
    val petal = if (isDarkThemeActive()) Color(0xFFF2B8CC) else Color(0xFFE79FBE)
    val petalDeep = if (isDarkThemeActive()) Color(0xFFE79FBE) else Color(0xFFD983A8)
    Canvas(modifier.then(semantics)) {
        when (scene) {
            ArtScene.LOTUS -> drawLotus(lotus, palette, petal, petalDeep, breath, drift)
            ArtScene.TEMPLE -> drawTemple(temple, palette, phase, breath, drift)
            ArtScene.ABSTRACT -> drawOrbs(palette, breath, drift)
            ArtScene.MONK -> Unit
        }
    }
}

/** The user's scene choice, as a composable-read flow (defaults to MONK). */
@Composable
fun rememberArtScene(): ArtScene {
    val context = LocalContext.current
    val stored by remember { MotionStore.get(context) }.scene.collectAsStateWithLifecycle()
    return remember(stored) { ArtScene.fromStored(stored) }
}

/** Accessible description for a scene, in the current locale. */
@Composable
fun artSceneContentDescription(scene: ArtScene): String = stringResource(
    when (scene) {
        ArtScene.MONK -> R.string.scene_desc_monk
        ArtScene.LOTUS -> R.string.scene_desc_lotus
        ArtScene.TEMPLE -> R.string.scene_desc_temple
        ArtScene.ABSTRACT -> R.string.scene_desc_abstract
    },
)

// region Lotus

private class LotusPaths {
    /** Petals fan out from the bud; drawn scaled by openness. */
    val petal: Path = Path().apply {
        moveTo(0.5f, 0.66f)
        quadraticBezierTo(0.44f, 0.50f, 0.5f, 0.42f)
        quadraticBezierTo(0.56f, 0.50f, 0.5f, 0.66f)
        close()
    }
    val petalLeft: Path = Path().apply {
        moveTo(0.5f, 0.66f)
        quadraticBezierTo(0.38f, 0.54f, 0.36f, 0.47f)
        quadraticBezierTo(0.47f, 0.52f, 0.5f, 0.66f)
        close()
    }
    val petalRight: Path = Path().apply {
        moveTo(0.5f, 0.66f)
        quadraticBezierTo(0.62f, 0.54f, 0.64f, 0.47f)
        quadraticBezierTo(0.53f, 0.52f, 0.5f, 0.66f)
        close()
    }
    val leaf: Path = Path().apply {
        moveTo(0.2f, 0.74f)
        quadraticBezierTo(0.3f, 0.70f, 0.4f, 0.74f)
        quadraticBezierTo(0.3f, 0.77f, 0.2f, 0.74f)
        close()
    }
}

private fun DrawScope.drawLotus(
    paths: LotusPaths,
    palette: ScenePalette,
    petal: Color,
    petalDeep: Color,
    breath: Float,
    drift: Float,
) {
    val ink = palette.ink
    // Water band.
    drawRect(
        palette.soft.copy(alpha = 0.30f),
        topLeft = Offset(0f, 0.72f * size.height),
        size = Size(size.width, size.height * 0.28f),
    )
    // Ripple rings spread and fade with the drift cycle.
    val rippleBase = (drift * 1.4f) % 1f
    for (i in 0 until 3) {
        val r = (rippleBase + i / 3f) % 1f
        drawOval(
            color = palette.accent.copy(alpha = (1f - r) * 0.25f),
            topLeft = Offset(
                size.width * (0.5f - 0.10f - 0.16f * r),
                size.height * (0.70f - 0.02f * r),
            ),
            size = Size(size.width * (0.20f + 0.32f * r), size.height * (0.045f + 0.05f * r)),
            style = Stroke(width = size.width * 0.004f),
        )
    }
    // Lotus reflection, faintly under the flower.
    drawOval(
        palette.accent.copy(alpha = 0.12f),
        topLeft = Offset(size.width * 0.42f, size.height * 0.70f),
        size = Size(size.width * 0.16f, size.height * 0.03f),
    )
    // Unit-space paths: origin-pivot scale maps 0..1 onto the canvas. The
    // vertical scale is the breath: petals close slightly on the inhale.
    val open = 1f - (breath - 0.5f) * 0.08f
    scale(size.width, size.height * open, pivot = Offset.Zero) {
        drawPath(paths.leaf, ink.copy(alpha = 0.5f))
        drawPath(paths.petalLeft, petalDeep)
        drawPath(paths.petalRight, petalDeep)
        drawPath(paths.petal, petal)
        // Bud heart.
        drawCircle(petalDeep, radius = 0.016f, center = Offset(0.5f, 0.62f))
    }
}

// endregion

// region Temple dawn

private class TemplePaths {
    /** Distant hills. */
    val hillsFar: Path = Path().apply {
        moveTo(0f, 0.66f)
        quadraticBezierTo(0.22f, 0.56f, 0.45f, 0.64f)
        quadraticBezierTo(0.7f, 0.71f, 1f, 0.60f)
        lineTo(1f, 1f)
        lineTo(0f, 1f)
        close()
    }
    val hillsNear: Path = Path().apply {
        moveTo(0f, 0.76f)
        quadraticBezierTo(0.3f, 0.68f, 0.6f, 0.75f)
        quadraticBezierTo(0.8f, 0.79f, 1f, 0.72f)
        lineTo(1f, 1f)
        lineTo(0f, 1f)
        close()
    }
    /** Generic stupa: tiered base, bell dome, spire. */
    val stupa: Path = Path().apply {
        moveTo(0.30f, 0.78f)
        lineTo(0.70f, 0.78f)
        lineTo(0.66f, 0.72f)
        lineTo(0.34f, 0.72f)
        close()
        moveTo(0.35f, 0.72f)
        lineTo(0.65f, 0.72f)
        lineTo(0.62f, 0.67f)
        lineTo(0.38f, 0.67f)
        close()
        moveTo(0.38f, 0.67f)
        quadraticBezierTo(0.40f, 0.58f, 0.5f, 0.57f)
        quadraticBezierTo(0.60f, 0.58f, 0.62f, 0.67f)
        close()
        moveTo(0.485f, 0.57f)
        lineTo(0.515f, 0.57f)
        lineTo(0.505f, 0.44f)
        lineTo(0.495f, 0.44f)
        close()
    }
}

private fun DrawScope.drawTemple(
    paths: TemplePaths,
    palette: ScenePalette,
    phase: DayPhase,
    breath: Float,
    drift: Float,
) {
    // Slow parallax light: a soft glow drifting across the sky.
    val glowX = size.width * (0.15f + drift * 0.5f)
    val glowY = size.height * 0.30f
    drawCircle(
        Brush.radialGradient(
            listOf(palette.accent.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(glowX, glowY),
            radius = size.width * 0.35f,
        ),
        radius = size.width * 0.35f,
        center = Offset(glowX, glowY),
    )
    // Stars at dusk/night, gently twinkling with the breath.
    if (phase == DayPhase.DUSK || phase == DayPhase.NIGHT) {
        val starAlpha = 0.35f + (breath * 0.4f).toFloat()
        val star = Color.White.copy(alpha = starAlpha)
        listOf(
            Offset(0.12f, 0.16f), Offset(0.32f, 0.09f), Offset(0.58f, 0.14f),
            Offset(0.78f, 0.22f), Offset(0.9f, 0.10f),
        ).forEach { p ->
            drawCircle(star, radius = size.width * 0.006f, center = Offset(p.x * size.width, p.y * size.height))
        }
    }
    scale(size.width, size.height, pivot = Offset.Zero) {
        drawPath(paths.hillsFar, palette.ink.copy(alpha = 0.55f))
        drawPath(paths.stupa, palette.ink)
        drawPath(paths.hillsNear, palette.ink.copy(alpha = 0.85f))
    }
    // One lit window at dusk/night.
    if (phase == DayPhase.DUSK || phase == DayPhase.NIGHT) {
        drawRect(
            palette.accent.copy(alpha = 0.8f),
            topLeft = Offset(size.width * 0.487f, size.height * 0.755f),
            size = Size(size.width * 0.026f, size.height * 0.035f),
        )
    }
}

// endregion

// region Abstract light

private fun DrawScope.drawOrbs(palette: ScenePalette, breath: Float, drift: Float) {
    val orbs = listOf(
        Triple(0.30f, 0.38f, 0.20f),
        Triple(0.66f, 0.30f, 0.15f),
        Triple(0.52f, 0.62f, 0.24f),
    )
    orbs.forEachIndexed { i, (cx, cy, r) ->
        val wobble = (breath - 0.5f) * (0.10f + i * 0.03f)
        val dx = (drift - 0.5f) * size.width * 0.03f * (i + 1)
        val radius = size.width * (r + wobble)
        drawCircle(
            Brush.radialGradient(
                listOf(
                    palette.accent.copy(alpha = 0.35f),
                    palette.soft.copy(alpha = 0.15f),
                    Color.Transparent,
                ),
                center = Offset(size.width * cx + dx, size.height * cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(size.width * cx + dx, size.height * cy),
        )
    }
}

// endregion
