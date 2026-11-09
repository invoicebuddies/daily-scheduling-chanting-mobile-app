package com.chantingscheduler.app.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private val LineColor = SolidColor(androidx.compose.ui.graphics.Color.Black)

/**
 * Cohesive line-art icon set (2dp stroke, rounded caps/joins) plus the few
 * filled glyphs the app needs. Icons tinted via `tint` use the current color;
 * the stroke color here is a placeholder that `tint` overrides.
 */
object AppIcons {

    private var _stop: ImageVector? = null

    /** Filled square "stop" glyph matching the Material icon grid. */
    val Stop: ImageVector
        get() = _stop ?: materialIcon(name = "Filled.Stop") {
            materialPath {
                moveTo(6f, 6f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(-12f)
                close()
            }
        }.also { _stop = it }

    // region Line-art icons (stroke only)

    private var _sunrise: ImageVector? = null

    /** Line-art half sun over a horizon with rays and an upward arrow. */
    val Sunrise: ImageVector
        get() = _sunrise ?: lineIcon("Line.Sunrise") {
            // horizon
            moveTo(2f, 18f)
            horizontalLineToRelative(20f)
            // half sun
            moveTo(6.5f, 18f)
            arcToRelative(5.5f, 5.5f, 0f, false, true, 11f, 0f)
            // rays
            moveTo(12f, 8.5f)
            verticalLineToRelative(-2.5f)
            moveTo(5.6f, 11f)
            lineToRelative(-1.8f, -1.8f)
            moveTo(18.4f, 11f)
            lineToRelative(1.8f, -1.8f)
            // upward arrow
            moveTo(12f, 4f)
            moveTo(12f, 4.2f)
            lineToRelative(-2f, 2f)
            moveTo(12f, 4.2f)
            lineToRelative(2f, 2f)
        }.also { _sunrise = it }

    private var _sunset: ImageVector? = null

    /** Line-art half sun over a horizon with rays and a downward arrow. */
    val Sunset: ImageVector
        get() = _sunset ?: lineIcon("Line.Sunset") {
            moveTo(2f, 18f)
            horizontalLineToRelative(20f)
            moveTo(6.5f, 18f)
            arcToRelative(5.5f, 5.5f, 0f, false, true, 11f, 0f)
            moveTo(12f, 4f)
            verticalLineToRelative(4f)
            moveTo(5.6f, 11f)
            lineToRelative(-1.8f, -1.8f)
            moveTo(18.4f, 11f)
            lineToRelative(1.8f, -1.8f)
            // downward arrow
            moveTo(12f, 9.8f)
            lineToRelative(-2f, -2f)
            moveTo(12f, 9.8f)
            lineToRelative(2f, -2f)
        }.also { _sunset = it }

    private var _settingsGear: ImageVector? = null

    val SettingsGear: ImageVector
        get() = _settingsGear ?: lineIcon("Line.SettingsGear") {
            // hub
            moveTo(12f, 9f)
            arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
            arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
            // 8 spokes
            val r1 = 6.5f
            val r2 = 9f
            for (a in 0 until 8) {
                val rad = Math.toRadians(a * 45.0)
                val cos = kotlin.math.cos(rad).toFloat()
                val sin = kotlin.math.sin(rad).toFloat()
                moveTo(12f + r1 * cos, 12f + r1 * sin)
                lineTo(12f + r2 * cos, 12f + r2 * sin)
            }
        }.also { _settingsGear = it }

    private var _repeat: ImageVector? = null

    /** Circular-arrows repeat symbol. */
    val Repeat: ImageVector
        get() = _repeat ?: lineIcon("Line.Repeat") {
            // top arc with arrow head pointing right
            moveTo(17f, 4f)
            lineToRelative(3f, 3f)
            lineToRelative(-3f, 3f)
            moveTo(20f, 7f)
            horizontalLineToRelative(-13f)
            arcToRelative(4f, 4f, 0f, false, false, -4f, 4f)
            // bottom arc with arrow head pointing left
            moveTo(7f, 20f)
            lineToRelative(-3f, -3f)
            lineToRelative(3f, -3f)
            moveTo(4f, 17f)
            horizontalLineToRelative(13f)
            arcToRelative(4f, 4f, 0f, false, false, 4f, -4f)
        }.also { _repeat = it }

    private var _musicNote: ImageVector? = null

    val MusicNote: ImageVector
        get() = _musicNote ?: lineIcon("Line.MusicNote") {
            // stem + beam
            moveTo(9f, 18f)
            verticalLineTo(6f)
            lineToRelative(9f, -2f)
            verticalLineToRelative(11.5f)
            // note heads
            moveTo(6.5f, 18f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, -5f, 0f)
            moveTo(15.5f, 15.5f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, -5f, 0f)
        }.also { _musicNote = it }

    private var _tag: ImageVector? = null

    val Tag: ImageVector
        get() = _tag ?: lineIcon("Line.Tag") {
            moveTo(3f, 11f)
            lineTo(3f, 11f)
            lineTo(3f, 5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineToRelative(6f)
            lineToRelative(9f, 9f)
            arcToRelative(2f, 2f, 0f, false, true, 0f, 3f)
            lineToRelative(-5f, 5f)
            arcToRelative(2f, 2f, 0f, false, true, -3f, 0f)
            close()
            // hole
            moveTo(7.5f, 7.5f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, 1f, 0f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, -1f, 0f)
        }.also { _tag = it }

    private var _sliders: ImageVector? = null

    val Sliders: ImageVector
        get() = _sliders ?: lineIcon("Line.Sliders") {
            // three tracks with knobs at different positions
            moveTo(4f, 6f)
            horizontalLineToRelative(8f)
            moveTo(16f, 6f)
            horizontalLineToRelative(4f)
            moveTo(14f, 4f)
            verticalLineToRelative(4f)
            moveTo(4f, 12f)
            horizontalLineToRelative(2f)
            moveTo(10f, 12f)
            horizontalLineToRelative(10f)
            moveTo(8f, 10f)
            verticalLineToRelative(4f)
            moveTo(4f, 18f)
            horizontalLineToRelative(10f)
            moveTo(18f, 18f)
            horizontalLineToRelative(2f)
            moveTo(16f, 16f)
            verticalLineToRelative(4f)
        }.also { _sliders = it }

    private var _palette: ImageVector? = null

    val Palette: ImageVector
        get() = _palette ?: lineIcon("Line.Palette") {
            moveTo(12f, 3f)
            arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
            arcToRelative(1.8f, 1.8f, 0f, false, false, 1.5f, -2.8f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.5f, -2.7f)
            horizontalLineToRelative(2f)
            arcToRelative(4f, 4f, 0f, false, false, 4f, -4f)
            arcToRelative(9.5f, 9.5f, 0f, false, false, -9f, -8.5f)
            close()
            // paint dots
            moveTo(7.5f, 10.5f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, 1f, 0f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, -1f, 0f)
            moveTo(11f, 7f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, 1f, 0f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, -1f, 0f)
            moveTo(15.5f, 8.5f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, 1f, 0f)
            arcToRelative(0.5f, 0.5f, 0f, true, true, -1f, 0f)
        }.also { _palette = it }

    private var _pin: ImageVector? = null

    val Pin: ImageVector
        get() = _pin ?: lineIcon("Line.Pin") {
            moveTo(12f, 21f)
            curveTo(12f, 21f, 5.5f, 14.5f, 5.5f, 9.5f)
            arcToRelative(6.5f, 6.5f, 0f, true, true, 13f, 0f)
            curveTo(18.5f, 14.5f, 12f, 21f, 12f, 21f)
            close()
            moveTo(12f, 8f)
            arcToRelative(1.8f, 1.8f, 0f, true, true, 0f, 3.6f)
            arcToRelative(1.8f, 1.8f, 0f, true, true, 0f, -3.6f)
        }.also { _pin = it }

    private var _bell: ImageVector? = null

    val Bell: ImageVector
        get() = _bell ?: lineIcon("Line.Bell") {
            moveTo(6f, 16f)
            verticalLineToRelative(-5.5f)
            arcToRelative(6f, 6f, 0f, true, true, 12f, 0f)
            verticalLineToRelative(5.5f)
            lineToRelative(2f, 3f)
            horizontalLineTo(4f)
            lineToRelative(2f, -3f)
            close()
            moveTo(10f, 19f)
            arcToRelative(2f, 2f, 0f, false, false, 4f, 0f)
        }.also { _bell = it }

    private var _batteryShield: ImageVector? = null

    /** Shield with a lightning bolt — battery reliability. */
    val BatteryShield: ImageVector
        get() = _batteryShield ?: lineIcon("Line.BatteryShield") {
            moveTo(12f, 3f)
            lineToRelative(7f, 2.5f)
            verticalLineToRelative(5f)
            curveTo(19f, 16f, 16f, 19.5f, 12f, 21f)
            curveTo(8f, 19.5f, 5f, 16f, 5f, 10.5f)
            verticalLineToRelative(-5f)
            close()
            // bolt
            moveTo(12.8f, 7.5f)
            lineToRelative(-3.3f, 4.5f)
            horizontalLineToRelative(2.3f)
            lineToRelative(-1f, 4.5f)
            lineToRelative(3.4f, -4.5f)
            horizontalLineToRelative(-2.3f)
            close()
        }.also { _batteryShield = it }

    private var _info: ImageVector? = null

    val Info: ImageVector
        get() = _info ?: lineIcon("Line.Info") {
            moveTo(12f, 3f)
            arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
            arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
            moveTo(12f, 11f)
            verticalLineToRelative(5f)
            moveTo(12f, 7.5f)
            lineTo(12f, 7.6f)
        }.also { _info = it }

    private var _search: ImageVector? = null

    val Search: ImageVector
        get() = _search ?: lineIcon("Line.Search") {
            moveTo(10.5f, 4f)
            arcToRelative(6.5f, 6.5f, 0f, true, true, 0f, 13f)
            arcToRelative(6.5f, 6.5f, 0f, true, true, 0f, -13f)
            moveTo(15.5f, 15.5f)
            lineTo(20f, 20f)
        }.also { _search = it }

    private var _alert: ImageVector? = null

    val Alert: ImageVector
        get() = _alert ?: lineIcon("Line.Alert") {
            moveTo(12f, 4f)
            lineToRelative(9f, 15f)
            horizontalLineToRelative(-18f)
            close()
            moveTo(12f, 10f)
            verticalLineToRelative(4f)
            moveTo(12f, 16.5f)
            lineTo(12f, 16.6f)
        }.also { _alert = it }

    private var _pencil: ImageVector? = null

    val Pencil: ImageVector
        get() = _pencil ?: lineIcon("Line.Pencil") {
            moveTo(4f, 20f)
            lineToRelative(1f, -4f)
            lineTo(16.5f, 4.5f)
            arcToRelative(2.1f, 2.1f, 0f, true, true, 3f, 3f)
            lineTo(8f, 19f)
            close()
            moveTo(14.5f, 6.5f)
            lineToRelative(3f, 3f)
        }.also { _pencil = it }

    private var _chart: ImageVector? = null

    /** Line-art bar chart on an L-axis — stats entry point. */
    val Chart: ImageVector
        get() = _chart ?: lineIcon("Line.Chart") {
            // axes
            moveTo(4f, 4f)
            verticalLineTo(20f)
            horizontalLineTo(20f)
            // bars
            moveTo(8.5f, 16.5f)
            verticalLineTo(11.5f)
            moveTo(12.5f, 16.5f)
            verticalLineTo(7.5f)
            moveTo(16.5f, 16.5f)
            verticalLineTo(13.5f)
        }.also { _chart = it }

    private var _flame: ImageVector? = null

    /** Line-art flame — practice streak. */
    val Flame: ImageVector
        get() = _flame ?: lineIcon("Line.Flame") {
            // outer teardrop
            moveTo(12f, 3f)
            curveTo(8f, 7f, 6f, 10f, 6f, 14f)
            arcToRelative(6f, 6f, 0f, false, false, 12f, 0f)
            curveTo(18f, 10f, 16f, 7f, 12f, 3f)
            close()
            // inner ember
            moveTo(12f, 13.5f)
            curveTo(10.3f, 15.3f, 9.5f, 16.4f, 9.5f, 17.8f)
            arcToRelative(2.5f, 2.5f, 0f, false, false, 5f, 0f)
            curveTo(14.5f, 16.4f, 13.7f, 15.3f, 12f, 13.5f)
            close()
        }.also { _flame = it }

    // endregion
}

/** Builds a 24dp line-art icon: 2dp round-capped stroke, no fill. */
private fun lineIcon(name: String, pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    materialIcon(name = name) {
        path(
            fill = null,
            stroke = LineColor,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }
