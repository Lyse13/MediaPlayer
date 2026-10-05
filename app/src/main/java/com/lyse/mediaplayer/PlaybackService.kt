package com.lyse.mediaplayer

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

private const val TAG = "PlayerEvents"

private fun createPlaybackListener() = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        val state = when (playbackState) {
            Player.STATE_IDLE -> "STATE_IDLE"
            Player.STATE_BUFFERING -> "STATE_BUFFERING"
            Player.STATE_READY -> "STATE_READY"
            Player.STATE_ENDED -> "STATE_ENDED"
            else -> "UNKNOWN_STATE"
        }
        Log.d(TAG, "changed state to $state")
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Log.d(TAG, "isPlaying = $isPlaying")
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "Player error: ${error.errorCodeName}", error)
    }
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val playbackListener = createPlaybackListener()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setMaxVideoSizeSd()
                .build()
            addListener(playbackListener)
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(playbackListener)
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}