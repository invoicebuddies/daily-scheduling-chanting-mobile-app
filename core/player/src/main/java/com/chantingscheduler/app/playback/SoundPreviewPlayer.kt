package com.chantingscheduler.app.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight shared preview player for auditioning sounds in the edit screen.
 * Plays one URI at a time; independent of [PlaybackService]. When the service
 * starts a session it calls [stop] so the two never play simultaneously.
 */
object SoundPreviewPlayer {

    private val _previewingUri = MutableStateFlow<String?>(null)
    val previewingUri: StateFlow<String?> = _previewingUri.asStateFlow()

    private var player: MediaPlayer? = null

    /** Plays [uri], or stops if [uri] is already previewing. */
    fun toggle(context: Context, uri: String) {
        if (_previewingUri.value == uri) {
            stop()
            return
        }
        stop()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setDataSource(context.applicationContext, Uri.parse(uri))
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnCompletionListener { stop() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mediaPlayer.prepareAsync()
            _previewingUri.value = uri
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        player?.let {
            it.setOnPreparedListener(null)
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            it.release()
        }
        player = null
        _previewingUri.value = null
    }
}
