package com.lyse.mediaplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.CastPlayer
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

private const val TAG = "PlayerEvents"

const val ACTION_TOGGLE_FAVORITE = "com.lyse.mediaplayer.TOGGLE_FAVORITE"
private const val MAX_FAVORITES = 2
private const val PREFS = "playback_state"
private const val KEY_LAST_ID = "last_media_id"
private const val KEY_LAST_POSITION = "last_position_ms"

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
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var mediaCache: SimpleCache? = null
    private var castPlayer: CastPlayer? = null
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


    private fun savePlaybackState(player: Player) {
        val item = player.currentMediaItem ?: return
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_LAST_ID, item.mediaId)
            .putLong(KEY_LAST_POSITION, player.currentPosition)
            .apply()
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {

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

        // ---- Navigation dans la médiathèque (MediaBrowser, Android Auto, Assistant...) ----

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(MediaCatalog.rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = MediaCatalog.children(this@PlaybackService, parentId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            val pageItems = children.drop(page * pageSize).take(pageSize)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = MediaCatalog.find(this@PlaybackService, mediaId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        // Un contrôleur externe envoie souvent des MediaItem avec seulement un mediaId :
        // on les complète (uri, mimeType, métadonnées) depuis le catalogue.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { MediaCatalog.find(this@PlaybackService, it.mediaId) ?: it }
            return Futures.immediateFuture(resolved.toMutableList())
        }

        // Bouton "play" d'un casque Bluetooth / de la notification alors que l'app est morte :
        // on redonne au lecteur la dernière file et la dernière position.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val items = MediaCatalog.tracks(this@PlaybackService).map(MediaCatalog::mediaItem)
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val lastId = prefs.getString(KEY_LAST_ID, null)
            val index = items.indexOfFirst { it.mediaId == lastId }.coerceAtLeast(0)
            val position = if (lastId != null) prefs.getLong(KEY_LAST_POSITION, 0L) else 0L
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, index, position)
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
        // Resolve the manifest OptionsProvider before creating Cast UI or a CastPlayer.
        CastContext.getSharedInstance(this)
        val cache = SimpleCache(
            File(cacheDir, "media3_playback_cache"),
            LeastRecentlyUsedCacheEvictor(250L * 1024 * 1024),
            StandaloneDatabaseProvider(this)
        )
        mediaCache = cache
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))
        val playbackDataSourceFactory = OfflineDownloads.createPlaybackDataSourceFactory(
            this,
            cacheDataSourceFactory
        )
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(playbackDataSourceFactory)
            )
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(java.util.Locale.getDefault().language)
                    .setPreferredTextLanguage(java.util.Locale.getDefault().language)
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
                        savePlaybackState(this@apply)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying) savePlaybackState(this@apply)
                    }
                })
            }

        castPlayer = CastPlayer.Builder(this).setLocalPlayer(player).build()
        mediaSession = MediaLibrarySession.Builder(this, castPlayer!!, SessionCallback())
            .setMediaButtonPreferences(ImmutableList.of(favoriteButton(false), skipForward))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // L'utilisateur a retiré l'app des applications récentes :
    // si rien ne joue, on arrête le service ; sinon la musique continue.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) savePlaybackState(player)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
        // Variante : toujours arrêter la lecture -> pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(playbackListener)
            player.release()
            release()
            mediaSession = null
        }
        castPlayer = null
        mediaCache?.release()
        mediaCache = null
        super.onDestroy()
    }
}