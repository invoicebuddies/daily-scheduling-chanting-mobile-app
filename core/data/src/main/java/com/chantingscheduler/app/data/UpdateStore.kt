package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Throttle / preference store for the GitHub update check (item 10): when the
 * last check ran, which version the user skipped, and whether automatic
 * checks are enabled. SharedPreferences, same pattern as [ThemeStore], so it
 * is readable synchronously and needs no Room schema change.
 */
class UpdateStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Epoch millis of the last check attempt (success or failure). */
    var lastCheckMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK_MS, value).apply()

    /** Version tag the user chose "Skip this version" for, or null. */
    var skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED_VERSION, null)
        set(value) = prefs.edit().putString(KEY_SKIPPED_VERSION, value).apply()

    /** Master toggle for automatic checks (Settings → About). Default on. */
    var autoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, DEFAULT_AUTO_CHECK)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()

    companion object {
        private const val PREFS_NAME = "update_check"
        private const val KEY_LAST_CHECK_MS = "last_check_ms"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"

        /** Minimum interval between automatic checks. */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        const val DEFAULT_AUTO_CHECK = true

        @Volatile
        private var instance: UpdateStore? = null

        fun get(context: Context): UpdateStore =
            instance ?: synchronized(this) {
                instance ?: UpdateStore(context).also { instance = it }
            }
    }
}
