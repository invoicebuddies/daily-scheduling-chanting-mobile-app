package com.chantingscheduler.app.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single source of truth for every animation in the app.
 *
 * Rules (applies app-wide):
 * - Animate opacity, scale (0.96–1.0 only), and translation of **containers** —
 *   never text size or letter spacing, never per character or per glyph
 *   (Thai, Burmese and Sinhala use combining marks; splitting text breaks
 *   shaping). Text containers may fade or slide as a unit; glyphs never move
 *   individually, and no ambient loop may translate or scale any text.
 * - Exits are faster than entrances; nothing over 500 ms for UI.
 * - Everything is interruptible: target-driven `animate*AsState` and springs,
 *   never one-shot keyframe-style restarts.
 * - No animation may block input; stagger is decorative and short.
 * - Motion levels: [MotionLevel.CALM] full gentle choreography,
 *   [MotionLevel.MINIMAL] short fades/slides only, no ambient loops,
 *   [MotionLevel.OFF] instant state changes, static artwork.
 */
object MotionTokens {

    // region Durations (ms)

    /** Press feedback, toggles. */
    const val DurationMicro = 120

    /** Chips, small reveals. */
    const val DurationShort = 200

    /** Cards, sheets, content swaps. */
    const val DurationMedium = 320

    /** Screen transitions, "Session complete" crossfade. */
    const val DurationLong = 480

    /** Stats count-up on first show. */
    const val DurationCountUp = 600

    /** Shared-axis slide distance. */
    const val SlideDistanceDp = 24

    /** Card entrance stagger: step and the max individually staggered items. */
    const val StaggerStepMs = 40
    const val StaggerMaxItems = 6

    /** Ringing-page breath cycle (one full inhale+exhale). */
    const val BreathCycleMs = 5500

    /** Ambient loop lengths (breath, light drift, pulses). */
    const val AmbientDriftMs = 7000
    const val AmbientPulseMs = 4000

    // endregion

    // region Easing

    /** Entrances and state reveals: strong decelerate, lands softly. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Exits: faster than the matching entrance. */
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Ambient loops and on-screen movement (sine ease-in-out approximation). */
    val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

    // endregion

    // region Springs (no visible bounce, interruptible)

    /** Toggles, switches, selections. */
    fun <T> springToggle() = androidx.compose.animation.core.spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Sliders, indicators, anything that slides between options. */
    fun <T> springSlide() = androidx.compose.animation.core.spring<T>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Press feedback depth. */
    const val PressScale = 0.96f

    // endregion

    // region Specs by level

    /** Entrance tween for [level]; instant at OFF. */
    fun <T> enterTween(level: MotionLevel, duration: Int = DurationMedium) =
        if (level == MotionLevel.OFF) snap<T>() else tween<T>(duration, easing = EmphasizedDecelerate)

    /** Exit tween for [level]; faster than its entrance; instant at OFF. */
    fun <T> exitTween(level: MotionLevel, duration: Int = DurationShort) =
        if (level == MotionLevel.OFF) snap<T>() else tween<T>(duration, easing = StandardAccelerate)

    /** Short fade for Minimal-level UI transitions. */
    fun <T> fadeTween(level: MotionLevel, duration: Int = DurationShort) =
        if (level == MotionLevel.OFF) snap<T>() else tween<T>(duration, easing = EaseInOutSine)

    /** Ambient loop tween (only ever called at CALM). */
    fun <T> ambientTween(duration: Int, reverse: Boolean = true) =
        tween<T>(duration, easing = EaseInOutSine)
}

/** User-selected motion intensity. */
enum class MotionLevel { CALM, MINIMAL, OFF }

/**
 * Pure motion policy: migration and the effective-level cap. Unit-tested.
 */
object MotionPolicy {

    /** Legacy boolean → level: true (reduce motion) becomes MINIMAL. */
    fun migrateReduceMotion(reduceMotion: Boolean): MotionLevel =
        if (reduceMotion) MotionLevel.MINIMAL else MotionLevel.CALM

    /**
     * Effective level = the user's choice, capped by the system. A system
     * animator duration scale of 0 (Android's "remove animations" /
     * accessibility path) forces OFF regardless of the user's choice.
     */
    fun effectiveLevel(user: MotionLevel, animatorDurationScale: Float): MotionLevel =
        if (animatorDurationScale == 0f) MotionLevel.OFF else user

    /** True when ambient (breathing, drifting) loops may run. */
    fun ambientAllowed(level: MotionLevel): Boolean = level == MotionLevel.CALM

    /** Parse a persisted store string; unknown values fall back to CALM. */
    fun levelFromStored(value: String?): MotionLevel = when (value) {
        "minimal" -> MotionLevel.MINIMAL
        "off" -> MotionLevel.OFF
        else -> MotionLevel.CALM
    }

    /** Inverse of [levelFromStored]. */
    fun levelToStored(level: MotionLevel): String = when (level) {
        MotionLevel.CALM -> "calm"
        MotionLevel.MINIMAL -> "minimal"
        MotionLevel.OFF -> "off"
    }
}

/**
 * Effective motion level for the composition tree. Provided once at the app
 * root; components read it instead of touching preferences directly.
 */
val LocalMotion: ProvidableCompositionLocal<MotionLevel> = compositionLocalOf { MotionLevel.CALM }

/**
 * True while a sheet/dialog covers most of the screen — ambient loops in the
 * layer behind pause so nothing burns battery where it can't be seen.
 */
val LocalAmbientPaused: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * True when ambient loops may actually run: CALM level, lifecycle at least
 * STARTED (app in foreground, screen on), and not covered by a sheet. All
 * breathing/drift loops read this; when false they render a static frame.
 */
@Composable
fun rememberAmbientActive(): Boolean {
    val level = LocalMotion.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    return MotionPolicy.ambientAllowed(level) &&
        state.isAtLeast(Lifecycle.State.STARTED) &&
        !LocalAmbientPaused.current
}

/**
 * Debug/test-only counter of infinite ambient transitions currently running.
 * [trackAmbient] maintains it; the "nothing moves at OFF" UI test asserts on
 * it. Zero cost in release behavior — it is only a counter.
 */
object MotionDebug {
    val runningInfiniteTransitions = AtomicInteger(0)
}

/** Registers one running ambient loop while [animate] is true. */
@Composable
fun trackAmbient(animate: Boolean, label: String = "ambient") {
    DisposableEffect(animate) {
        if (animate) MotionDebug.runningInfiniteTransitions.incrementAndGet()
        onDispose {
            if (animate) MotionDebug.runningInfiniteTransitions.decrementAndGet()
        }
    }
}

/**
 * Pure breath-cycle math: maps loop progress (0..1) to a smooth 0..1 value
 * that rises and falls like an inhale/exhale (raised-cosine). One
 * [MotionTokens.BreathCycleMs] loop = one full breath.
 */
object BreathCurve {
    fun valueAt(progress: Float): Double {
        val p = progress - progress.toInt()
        return 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * p)
    }
}
