package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences for motion and scenery.
 *
 * - **Motion level** (`calm` / `minimal` / `off`) replaces the old boolean
 *   `reduce_motion` flag on first read (migration: true → minimal,
 *   false → calm; see `MotionPolicy` in the app module for the mapping).
 * - **Scene** (`monk` / `lotus` / `temple` / `abstract`) selects the header
 *   and ringing-page artwork.
 *
 * Both are plain strings here; the UI layer maps them to typed enums so this
 * store stays dependency-free. The system animator scale is read by the UI
 * and can force the effective level lower.
 */
class MotionStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _motionLevel = MutableStateFlow(readMotionLevelWithMigration())

    /** Stored motion level: "calm" (default), "minimal", or "off". */
    val motionLevel: StateFlow<String> = _motionLevel.asStateFlow()

    private val _scene = MutableStateFlow(prefs.getString(KEY_SCENE, null) ?: SCENE_MONK)

    /** Stored scene id: "monk" (default), "lotus", "temple", or "abstract". */
    val scene: StateFlow<String> = _scene.asStateFlow()

    fun setMotionLevel(level: String) {
        val normalized = level.lowercase()
        prefs.edit().putString(KEY_LEVEL, normalized).apply()
        _motionLevel.value = normalized
    }

    fun setScene(scene: String) {
        val normalized = scene.lowercase()
        prefs.edit().putString(KEY_SCENE, normalized).apply()
        _scene.value = normalized
    }

    /**
     * Old installs only have the boolean [KEY_REDUCE]. Map it once to the new
     * level scale and drop the legacy key so it can't drift out of sync.
     */
    private fun readMotionLevelWithMigration(): String {
        prefs.getString(KEY_LEVEL, null)?.let { return it }
        val legacy = prefs.getBoolean(KEY_REDUCE, false)
        val migrated = if (legacy) LEVEL_MINIMAL else LEVEL_CALM
        prefs.edit()
            .putString(KEY_LEVEL, migrated)
            .remove(KEY_REDUCE)
            .apply()
        return migrated
    }

    companion object {
        private const val PREFS_NAME = "motion"
        private const val KEY_LEVEL = "motion_level"
        private const val KEY_SCENE = "art_scene"
        private const val KEY_REDUCE = "reduce_motion"

        const val LEVEL_CALM = "calm"
        const val LEVEL_MINIMAL = "minimal"
        const val LEVEL_OFF = "off"

        const val SCENE_MONK = "monk"
        const val SCENE_LOTUS = "lotus"
        const val SCENE_TEMPLE = "temple"
        const val SCENE_ABSTRACT = "abstract"

        @Volatile
        private var instance: MotionStore? = null

        fun get(context: Context): MotionStore =
            instance ?: synchronized(this) {
                instance ?: MotionStore(context.applicationContext).also { instance = it }
            }
    }
}
