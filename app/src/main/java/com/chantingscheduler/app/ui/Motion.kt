package com.chantingscheduler.app.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chantingscheduler.app.data.MotionStore

/**
 * True when ambient animation should be suppressed: either the user enabled
 * "Reduce motion" in settings, or the system animator duration scale is 0
 * (accessibility / developer setting). Ambient loops (breathing, pulsing)
 * check this and render static art instead.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val pref = MotionStore.get(context).reduceMotion.collectAsStateWithLifecycle().value
    val animatorScale = rememberAnimatorScale()
    return pref || animatorScale == 0f
}

@Composable
private fun rememberAnimatorScale(): Float {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
}
