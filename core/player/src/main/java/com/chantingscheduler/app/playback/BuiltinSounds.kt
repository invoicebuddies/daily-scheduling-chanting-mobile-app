package com.chantingscheduler.app.playback

import android.content.Context

/**
 * Bundled chime sounds, shipped in `res/raw` so the app works out of the box.
 * Stored as `android.resource://` URIs so they flow through the exact same
 * playback pipeline as user-picked files (and survive backup/restore, since
 * they reference this package, not a fragile SAF grant). URIs are built from
 * `R.raw` ids (kept alive for the resource shrinker) but keep the historical
 * `/raw/<name>` path format so URIs stored in existing databases keep working.
 */
data class BuiltinSound(val uri: String, val displayName: String)

object BuiltinSounds {

    private data class BuiltinRes(val resId: Int, val label: String)

    private fun builtinRes(): List<BuiltinRes> = listOf(
        BuiltinRes(com.chantingscheduler.app.core.player.R.raw.builtin_morning_bell, "Morning bell"),
        BuiltinRes(com.chantingscheduler.app.core.player.R.raw.builtin_temple_bell, "Temple bell"),
        BuiltinRes(com.chantingscheduler.app.core.player.R.raw.builtin_singing_bowl, "Singing bowl"),
    )

    fun all(context: Context): List<BuiltinSound> =
        builtinRes().map { (resId, label) ->
            BuiltinSound(uri = uriFor(context, resId), displayName = label)
        }

    fun isBuiltinUri(uri: String): Boolean = uri.startsWith("android.resource://")

    /** Short resource name for a bundled URI, or null for anything else. */
    fun resNameOf(uri: String): String? = uri.substringAfterLast("/raw/").takeIf { isBuiltinUri(uri) }

    private fun uriFor(context: Context, resId: Int): String =
        "android.resource://${context.packageName}/$resId"
}
