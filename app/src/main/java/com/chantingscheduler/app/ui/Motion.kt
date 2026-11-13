package com.chantingscheduler.app.ui

import androidx.compose.runtime.Composable
import com.chantingscheduler.app.ui.motion.LocalMotion
import com.chantingscheduler.app.ui.motion.MotionLevel

/**
 * True when ambient animation should be suppressed: the effective motion
 * level is anything but CALM (user chose Minimal/Off, or the system animator
 * scale is 0 and forces OFF). Ambient loops (breathing, drifting) read the
 * level through [LocalMotion] and render static art instead.
 */
@Composable
fun rememberReduceMotion(): Boolean = LocalMotion.current != MotionLevel.CALM
