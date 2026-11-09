package com.chantingscheduler.app.playback

/**
 * Decides what happens when a playlist track fails to play.
 *
 * Failures are counted per **unique URI**, not per playlist position: with
 * repeat passes or an auto-stop loop the same broken file would otherwise be
 * counted once per pass and eventually trip the fallback chime even while
 * good audio was playing. The fallback only fires when every distinct URI in
 * the playlist has failed; anything less just skips the broken item.
 */
class PlaylistFailurePolicy {

    sealed interface Decision {
        /** Remove the failed item and keep playing the rest. */
        data object Skip : Decision

        /** Every unique URI failed — ring the fallback chime instead. */
        data object Fallback : Decision
    }

    private val failedUris = mutableSetOf<String>()

    val failedCount: Int get() = failedUris.size

    /**
     * Records a failure of [uri] and returns the action to take.
     * [uniqueUris] is the set of distinct URIs in the current playlist.
     */
    fun onTrackFailed(uri: String, uniqueUris: Set<String>): Decision {
        failedUris.add(uri)
        return if (failedUris.containsAll(uniqueUris)) Decision.Fallback else Decision.Skip
    }

    fun reset() = failedUris.clear()
}
