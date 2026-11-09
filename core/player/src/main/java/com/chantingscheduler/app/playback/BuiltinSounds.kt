package com.chantingscheduler.app.playback

import android.content.Context

/**
 * Bundled chime sounds, shipped in `res/raw` so the app works out of the box.
 * Stored as `android.resource://` URIs so they flow through the exact same
 * playback pipeline as user-picked files (and survive backup/restore, since
 * they reference this package, not a fragile SAF grant). URIs are built from
 * `R.raw` ids (kept alive for the resource shrinker) but keep the historical
 * `/raw/<name>` path format so URIs stored in existing databases still
 * resolve.
 *
 * [nameKey] is the stable resource name ("builtin_morning_bell"); it is what
 * gets stored in the database and in backups. Labels are resolved at display
 * time from string resources so they follow the app locale — never store the
 * translated label.
 */
data class BuiltinSound(val uri: String, val nameKey: String, val displayName: String)

object BuiltinSounds {

    private data class BuiltinRes(val resId: Int, val nameKey: String, val labelRes: Int)

    private fun builtinRes(): List<BuiltinRes> = listOf(
        BuiltinRes(
            com.chantingscheduler.app.core.player.R.raw.builtin_morning_bell,
            "builtin_morning_bell",
            com.chantingscheduler.app.core.player.R.string.builtin_morning_bell,
        ),
        BuiltinRes(
            com.chantingscheduler.app.core.player.R.raw.builtin_temple_bell,
            "builtin_temple_bell",
            com.chantingscheduler.app.core.player.R.string.builtin_temple_bell,
        ),
        BuiltinRes(
            com.chantingscheduler.app.core.player.R.raw.builtin_singing_bowl,
            "builtin_singing_bowl",
            com.chantingscheduler.app.core.player.R.string.builtin_singing_bowl,
        ),
    )

    /** Stable keys stored in the database; also match stored URIs' res names. */
    val nameKeys: Set<String> = builtinRes().map { it.nameKey }.toSet()

    fun all(context: Context): List<BuiltinSound> =
        builtinRes().map { (resId, nameKey, labelRes) ->
            BuiltinSound(
                uri = uriFor(context, resId),
                nameKey = nameKey,
                displayName = context.getString(labelRes),
            )
        }

    /**
     * Localized label for a stored value: a [BuiltinSound.nameKey], or a
     * legacy stored English label from before localization. Anything else
     * (user file names) passes through untouched.
     */
    fun labelFor(context: Context, stored: String?): String {
        if (stored == null) return ""
        val byKey = builtinRes().firstOrNull { it.nameKey == stored }
        if (byKey != null) return context.getString(byKey.labelRes)
        val byLegacyLabel = builtinRes().firstOrNull { context.getString(it.labelRes) == stored }
        if (byLegacyLabel != null) return context.getString(byLegacyLabel.labelRes)
        return stored
    }

    fun isBuiltinUri(uri: String): Boolean = uri.startsWith("android.resource://")

    /** Short resource name for a bundled URI, or null for anything else. */
    fun resNameOf(uri: String): String? = uri.substringAfterLast("/raw/").takeIf { isBuiltinUri(uri) }

    private fun uriFor(context: Context, resId: Int): String =
        "android.resource://${context.packageName}/$resId"
}
