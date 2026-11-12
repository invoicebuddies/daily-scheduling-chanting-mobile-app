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

    private val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val _previewingUri = MutableStateFlow<String?>(null)
    val previewingUri: StateFlow<String?> = _previewingUri.asStateFlow()

    private var player: MediaPlayer? = null

    /**
     * Plays [uri], or stops if [uri] is already previewing. [volumePercent]
     * (1–100) previews at that loudness; [autoStopMs] > 0 stops the preview
     * after that long (the edit screen's volume preview uses 5 s).
     */
    fun toggle(context: Context, uri: String, volumePercent: Int? = null, autoStopMs: Long = 0) {
        if (_previewingUri.value == uri && volumePercent == null) {
            stop()
            return
        }
        stop()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setDataSource(context.applicationContext, Uri.parse(uri))
            volumePercent?.let {
                val volume = it.coerceIn(0, 100) / 100f
                mediaPlayer.setVolume(volume, volume)
            }
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnCompletionListener { stop() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mediaPlayer.prepareAsync()
            _previewingUri.value = uri
            if (autoStopMs > 0) {
                previewHandler.postDelayed({ stop() }, autoStopMs)
            }
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        previewHandler.removeCallbacksAndMessages(null)
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
