package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preference for reducing ambient animation (breathing/pulsing loops,
 * header transitions). Independent of the system animator scale, which the UI
 * also respects (a scale of 0 forces reduced motion regardless of this flag).
 */
class MotionStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _reduceMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCE, false))

    /** Observable preference for the UI. */
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    fun setReduceMotion(value: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE, value).apply()
        _reduceMotion.value = value
    }

    companion object {
        private const val PREFS_NAME = "motion"
        private const val KEY_REDUCE = "reduce_motion"

        @Volatile
        private var instance: MotionStore? = null

        fun get(context: Context): MotionStore =
            instance ?: synchronized(this) {
                instance ?: MotionStore(context.applicationContext).also { instance = it }
            }
    }
}
