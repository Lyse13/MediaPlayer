package com.lyse.mediaplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val TAG = "PlayerEvents"

const val ACTION_TOGGLE_FAVORITE = "com.lyse.mediaplayer.TOGGLE_FAVORITE"
private const val MAX_FAVORITES = 2

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

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val playbackListener = createPlaybackListener()

    private val favorites = mutableSetOf<String>()
    private val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

    private val skipForward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
        .setDisplayName("Avancer de 15 s")
        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
        .setSlots(CommandButton.SLOT_FORWARD)
        .build()

    private fun favoriteButton(isFavorite: Boolean) =
        CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris")
            .setSessionCommand(toggleFavoriteCommand)
            .build()

    private fun updateButtons() {
        val session = mediaSession ?: return
        val currentId = session.player.currentMediaItem?.mediaId
        session.setMediaButtonPreferences(
            ImmutableList.of(favoriteButton(currentId in favorites), skipForward)
        )
    }

    private inner class SessionCallback : MediaSession.Callback {

        @OptIn(UnstableApi::class)
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<ConnectionResult> {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(toggleFavoriteCommand)
                .build()
            return Futures.immediateFuture(
                AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                val item = session.player.currentMediaItem
                if (item != null) {
                    val isAdding = item.mediaId !in favorites
                    if (isAdding && favorites.size >= MAX_FAVORITES) {
                        session.sendError(
                            SessionError(
                                SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED,
                                "Version gratuite : $MAX_FAVORITES favoris maximum"
                            )
                        )
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))
                    }
                    val nowFavorite = if (favorites.remove(item.mediaId)) {
                        false
                    } else {
                        favorites.add(item.mediaId)
                        true
                    }
                    Log.d(TAG, "favorite = $nowFavorite for ${item.mediaMetadata.title} (from ${controller.packageName})")
                    updateButtons()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setMaxVideoSizeSd()
                .build()
            addListener(playbackListener)
            addAnalyticsListener(EventLogger())
            addAnalyticsListener(
                PlaybackStatsListener(/* keepHistory= */ false) { eventTime, stats ->
                    val title = eventTime.timeline
                        .getWindow(eventTime.windowIndex, Timeline.Window())
                        .mediaItem.mediaMetadata.title
                    Log.d(
                        TAG,
                        "Playback summary for $title: play time = ${stats.totalPlayTimeMs} ms, " +
                                "rebuffers = ${stats.totalRebufferCount}, " +
                                "mean video bitrate = ${stats.meanVideoFormatBitrate}"
                    )
                }
            )
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateButtons()
                }
            })
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(ImmutableList.of(favoriteButton(false), skipForward))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

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