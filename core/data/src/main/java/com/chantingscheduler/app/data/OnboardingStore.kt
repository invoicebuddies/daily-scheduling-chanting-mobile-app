package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * First-launch onboarding flag. SharedPreferences (same pattern as
 * [LocationStore]/[ThemeStore]) so it is readable synchronously at startup.
 */
class OnboardingStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _seen = MutableStateFlow(prefs.getBoolean(KEY_SEEN, false))

    /** Whether the user has completed (or skipped) the introduction. */
    val seen: StateFlow<Boolean> = _seen.asStateFlow()

    fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
        _seen.value = true
    }

    companion object {
        private const val PREFS_NAME = "onboarding"
        private const val KEY_SEEN = "seen"

        @Volatile
        private var instance: OnboardingStore? = null

        fun get(context: Context): OnboardingStore =
            instance ?: synchronized(this) {
                instance ?: OnboardingStore(context).also { instance = it }
            }
    }
}
