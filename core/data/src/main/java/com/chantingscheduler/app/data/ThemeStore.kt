package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-selected app theme. */
enum class AppTheme { AUTO, LIGHT, DARK }

/**
 * App-wide theme preference. SharedPreferences (same pattern as
 * [LocationStore]) so the value is readable synchronously at startup.
 */
class ThemeStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(readTheme())

    /** Observable theme for the UI. */
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    /** Current theme, readable synchronously. */
    val current: AppTheme get() = _theme.value

    fun set(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    private fun readTheme(): AppTheme =
        prefs.getString(KEY_THEME, null)
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
            ?: AppTheme.AUTO

    companion object {
        private const val PREFS_NAME = "theme"
        private const val KEY_THEME = "mode"

        @Volatile
        private var instance: ThemeStore? = null

        fun get(context: Context): ThemeStore =
            instance ?: synchronized(this) {
                instance ?: ThemeStore(context).also { instance = it }
            }
    }
}
