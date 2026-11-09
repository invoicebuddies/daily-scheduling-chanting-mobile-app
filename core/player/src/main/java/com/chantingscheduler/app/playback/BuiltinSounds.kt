package com.chantingscheduler.app.playback

import android.content.Context

/**
 * Bundled chime sounds, shipped in `res/raw` so the app works out of the box.
 * Stored as `android.resource://` URIs so they flow through the exact same
 * playback pipeline as user-picked files (and survive backup/restore, since
 * they reference this package, not a fragile SAF grant).
 */
data class BuiltinSound(val uri: String, val displayName: String)

object BuiltinSounds {

    private val RES_NAMES = listOf(
        "builtin_morning_bell" to "Morning bell",
        "builtin_temple_bell" to "Temple bell",
        "builtin_singing_bowl" to "Singing bowl",
    )

    fun all(context: Context): List<BuiltinSound> =
        RES_NAMES.map { (resName, label) ->
            BuiltinSound(uri = "android.resource://${context.packageName}/raw/$resName", displayName = label)
        }

    fun isBuiltinUri(uri: String): Boolean = uri.startsWith("android.resource://")

    /** Short resource name for a bundled URI, or null for anything else. */
    fun resNameOf(uri: String): String? = uri.substringAfterLast("/raw/").takeIf { isBuiltinUri(uri) }
}
