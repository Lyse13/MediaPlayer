package com.lyse.mediaplayer

import android.Manifest
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.lyse.mediaplayer.ui.theme.MediaPlayerTheme
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.source.TrackGroupArray
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors


private const val DASH_URL =
    "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
private const val HLS_VOD_URL =
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
private const val HLS_LIVE_URL =
    "https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8"
data class Track(
    val title: String,
    val url: String,
    val mimeType: String? = null
)

private enum class VideoQuality(val label: String) {
    AUTO("Auto"),
    SD("SD"),
    HD("HD · 720p max")
}

private data class TrackChoice(
    val label: String,
    val mediaTrackGroup: TrackGroup,
    val trackIndices: List<Int>,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    private val tracks by lazy {
        listOf(
            Track("Big Buck Bunny (video)", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"),
            Track("Jazz in Paris (audio)", "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"),
            Track("Local sample (audio)", "android.resource://$packageName/${R.raw.sample_audio}"),
            Track("Streaming adaptatif DASH (vidéo)", DASH_URL, MimeTypes.APPLICATION_MPD),
            Track("HLS adaptatif (vidéo à la demande)", HLS_VOD_URL, MimeTypes.APPLICATION_M3U8),
            Track("HLS en direct (test)", HLS_LIVE_URL, MimeTypes.APPLICATION_M3U8),
        )
    }

    private var player by mutableStateOf<Player?>(null)
    private var currentIndex by mutableIntStateOf(0)
    private var isFavorite by mutableStateOf(false)
    private var playbackState by mutableIntStateOf(Player.STATE_IDLE)
    private var isPlaying by mutableStateOf(false)
    private var playbackError by mutableStateOf<String?>(null)
    private var playlistItems by mutableStateOf<List<MediaItem>>(emptyList())
    private var canChangePlaylist by mutableStateOf(false)
    private var canSkipPrevious by mutableStateOf(false)
    private var canSkipNext by mutableStateOf(false)
    private var canSetRepeatMode by mutableStateOf(false)
    private var canSetShuffleMode by mutableStateOf(false)
    private var canSetTrackSelectionParameters by mutableStateOf(false)
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var shuffleModeEnabled by mutableStateOf(false)
    private var videoQuality by mutableStateOf(VideoQuality.AUTO)
    private var hasVideoTracks by mutableStateOf(false)
    private var audioTrackChoices by mutableStateOf<List<TrackChoice>>(emptyList())
    private var textTrackChoices by mutableStateOf<List<TrackChoice>>(emptyList())
    private var subtitlesDisabled by mutableStateOf(false)
    private var metadataMessage by mutableStateOf<String?>(null)
    private var metadataRequestId = 0
    private var downloads by mutableStateOf<Map<String, Download>>(emptyMap())
    private var downloadManager: DownloadManager? = null
    private val downloadLookupExecutor = Executors.newSingleThreadExecutor()

    private val downloadManagerListener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            refreshDownloadStates(downloadManager)
        }

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            downloads = downloads.toMutableMap().apply {
                put(download.request.id, download)
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            downloads = downloads - download.request.id
        }
    }

    private fun createMediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.url)
            .setUri(track.url)
            .setMimeType(track.mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()

    @OptIn(UnstableApi::class)
    private fun inspectMetadata(track: Track) {
        val requestId = ++metadataRequestId
        val retriever = try {
            MetadataRetriever.Builder(this, createMediaItem(track)).build()
        } catch (exception: Exception) {
            metadataMessage = "Analyse impossible : ${exception.localizedMessage ?: "erreur inconnue"}"
            return
        }
        metadataMessage = "Analyse de « ${track.title} » sans démarrer la lecture…"

        val trackGroupsFuture = retriever.retrieveTrackGroups()
        val durationFuture = retriever.retrieveDurationUs()
        val metadataFuture = Futures.allAsList(trackGroupsFuture, durationFuture)
        Futures.addCallback(
            metadataFuture,
            object : FutureCallback<List<Any>> {
                override fun onSuccess(result: List<Any>?) {
                    retriever.close()
                    if (requestId != metadataRequestId || result == null) return
                    val trackGroups = result[0] as TrackGroupArray
                    val durationUs = result[1] as Long
                    val trackCounts = (0 until trackGroups.length)
                        .map { trackGroups.get(it).type }
                        .groupingBy { it }
                        .eachCount()
                    val duration = if (durationUs == C.TIME_UNSET) {
                        "durée inconnue"
                    } else {
                        formatMetadataDuration(TimeUnit.MICROSECONDS.toMillis(durationUs))
                    }
                    val trackSummary = listOf(
                        C.TRACK_TYPE_AUDIO to "audio",
                        C.TRACK_TYPE_VIDEO to "vidéo",
                        C.TRACK_TYPE_TEXT to "sous-titres"
                    ).mapNotNull { (type, label) ->
                        trackCounts[type]?.let { "$it $label" }
                    }.ifEmpty { listOf("aucune piste détectée") }
                    metadataMessage = "${track.title} · $duration · ${trackSummary.joinToString()}"
                }

                override fun onFailure(error: Throwable) {
                    retriever.close()
                    if (requestId == metadataRequestId) {
                        metadataMessage = "Analyse impossible : ${error.localizedMessage ?: "média inaccessible"}"
                    }
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun formatMetadataDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1_000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    @OptIn(UnstableApi::class)
    private fun refreshDownloadStates(manager: DownloadManager) {
        downloadLookupExecutor.execute {
            val restoredDownloads = tracks.mapNotNull { track ->
                try {
                    manager.downloadIndex.getDownload(track.url)
                } catch (_: Exception) {
                    null
                }
            }.associateBy { it.request.id }
            runOnUiThread {
                if (!isDestroyed) downloads = restoredDownloads
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun addOfflineDownload(track: Track) {
        val requestBuilder = DownloadRequest.Builder(track.url, Uri.parse(track.url))
        track.mimeType?.let(requestBuilder::setMimeType)
        DownloadService.sendAddDownload(
            this,
            OfflineDownloadService::class.java,
            requestBuilder.build(),
            true
        )
    }

    @OptIn(UnstableApi::class)
    private fun removeOfflineDownload(id: String) {
        DownloadService.sendRemoveDownload(
            this,
            OfflineDownloadService::class.java,
            id,
            true
        )
    }

    private fun downloadStatus(download: Download): String = when (download.state) {
        Download.STATE_QUEUED -> "En attente"
        Download.STATE_DOWNLOADING -> {
            val percent = download.percentDownloaded
            if (percent >= 0f) "Téléchargement · ${percent.toInt()}%" else "Téléchargement…"
        }
        Download.STATE_COMPLETED -> "Disponible hors ligne"
        Download.STATE_FAILED -> "Échec · appuyer pour réessayer"
        Download.STATE_REMOVING, Download.STATE_RESTARTING -> "Mise à jour…"
        Download.STATE_STOPPED -> "En pause"
        else -> ""
    }

    private fun updatePlaylistState(source: Player) {
        playlistItems = List(source.mediaItemCount) { index -> source.getMediaItemAt(index) }
        currentIndex = source.currentMediaItemIndex
        repeatMode = source.repeatMode
        shuffleModeEnabled = source.shuffleModeEnabled
        canChangePlaylist = source.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        canSkipPrevious = source.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
            source.hasPreviousMediaItem()
        canSkipNext = source.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
            source.hasNextMediaItem()
        canSetRepeatMode = source.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)
        canSetShuffleMode = source.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)
        canSetTrackSelectionParameters =
            source.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)
    }

    private fun updateTrackChoices(tracks: Tracks) {
        hasVideoTracks = tracks.containsType(C.TRACK_TYPE_VIDEO) &&
            tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)
        audioTrackChoices = createTrackChoices(tracks, C.TRACK_TYPE_AUDIO)
        textTrackChoices = createTrackChoices(tracks, C.TRACK_TYPE_TEXT)
        if (textTrackChoices.any(TrackChoice::isSelected)) subtitlesDisabled = false
    }

    private fun createTrackChoices(tracks: Tracks, trackType: Int): List<TrackChoice> =
        tracks.groups.mapIndexedNotNull { groupIndex, group ->
            if (group.type != trackType || !group.isSupported) return@mapIndexedNotNull null
            val trackIndices = (0 until group.length).filter(group::isTrackSupported)
            if (trackIndices.isEmpty()) return@mapIndexedNotNull null

            val format = group.getTrackFormat(trackIndices.first())
            val label = format.label?.takeIf(String::isNotBlank)
                ?: format.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: "Piste ${groupIndex + 1}"
            TrackChoice(label, group.mediaTrackGroup, trackIndices, group.isSelected)
        }

    private fun applyVideoQuality(quality: VideoQuality) {
        val activePlayer = player ?: return
        if (!canSetTrackSelectionParameters) return
        val parameters = activePlayer.trackSelectionParameters.buildUpon()
            .clearVideoSizeConstraints()
            .apply {
                when (quality) {
                    VideoQuality.AUTO -> Unit
                    VideoQuality.SD -> setMaxVideoSizeSd()
                    VideoQuality.HD -> setMaxVideoSize(1280, 720)
                }
            }
            .build()
        activePlayer.trackSelectionParameters = parameters
        videoQuality = quality
    }

    private fun selectTrack(trackType: Int, choice: TrackChoice? = null, disabled: Boolean = false) {
        val activePlayer = player ?: return
        if (!canSetTrackSelectionParameters) return
        val builder = activePlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, disabled)
        if (choice != null) {
            builder.setOverrideForType(
                TrackSelectionOverride(choice.mediaTrackGroup, choice.trackIndices)
            )
        }
        activePlayer.trackSelectionParameters = builder.build()
        if (trackType == C.TRACK_TYPE_TEXT) subtitlesDisabled = disabled
    }

    private val playbackUiListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            this@MainActivity.playbackState = playbackState
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@MainActivity.isPlaying = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackError = error.localizedMessage
                ?: error.cause?.localizedMessage
                ?: error.errorCodeName
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            player?.let(::updatePlaylistState)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            player?.let(::updatePlaylistState)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@MainActivity.repeatMode = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            this@MainActivity.shuffleModeEnabled = shuffleModeEnabled
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            player?.let(::updatePlaylistState)
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTrackChoices(tracks)
        }
    }

    @OptIn(UnstableApi::class)
    private fun readFavorite(buttons: List<CommandButton>) {
        isFavorite = buttons.any {
            it.sessionCommand?.customAction == ACTION_TOGGLE_FAVORITE &&
                    it.icon == CommandButton.ICON_HEART_FILLED
        }
    }
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            var tab by remember { mutableIntStateOf(0) }
            var qualityMenuExpanded by remember { mutableStateOf(false) }
            var audioMenuExpanded by remember { mutableStateOf(false) }
            var textMenuExpanded by remember { mutableStateOf(false) }
            MediaPlayerTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        Surface(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(
                                    "MEDIA PLAYER",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    when (tab) {
                                        0 -> "Votre lecteur"
                                        1 -> "Éditeur vidéo"
                                        else -> "Caméra"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            listOf("Lecteur", "Éditeur", "Caméra").forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = {
                                        Text(listOf("▶", "✂", "●")[index])
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { contentPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                    ) {
                        when (tab) {
                    0 -> {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            factory = { context -> PlayerView(context) },
                            update = { view -> view.player = player }
                        )
                        Text(
                            text = when {
                                playbackError != null -> "Erreur de lecture"
                                isPlaying -> "Lecture en cours"
                                playbackState == Player.STATE_BUFFERING -> "Chargement…"
                                playbackState == Player.STATE_READY -> "En pause"
                                playbackState == Player.STATE_ENDED -> "Lecture terminée"
                                else -> "Lecteur prêt"
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        playbackError?.let { message ->
                            Text(
                                text = message,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Button(
                                onClick = {
                                    val activePlayer = player ?: return@Button
                                    playbackError = null
                                    activePlayer.prepare()
                                    activePlayer.play()
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text("Réessayer")
                            }
                        }
                        if (player != null) PlaybackProgress(player)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasVideoTracks) {
                                Box {
                                    Button(
                                        enabled = canSetTrackSelectionParameters,
                                        onClick = { qualityMenuExpanded = true }
                                    ) {
                                        Text("Qualité : ${videoQuality.label}")
                                    }
                                    DropdownMenu(
                                        expanded = qualityMenuExpanded,
                                        onDismissRequest = { qualityMenuExpanded = false }
                                    ) {
                                        VideoQuality.values().forEach { quality ->
                                            DropdownMenuItem(
                                                text = { Text(quality.label) },
                                                onClick = {
                                                    applyVideoQuality(quality)
                                                    qualityMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (audioTrackChoices.size > 1) {
                                Box {
                                    Button(
                                        enabled = canSetTrackSelectionParameters,
                                        onClick = { audioMenuExpanded = true }
                                    ) {
                                        val selectedAudio = audioTrackChoices
                                            .firstOrNull(TrackChoice::isSelected)?.label ?: "Auto"
                                        Text("Audio : $selectedAudio")
                                    }
                                    DropdownMenu(
                                        expanded = audioMenuExpanded,
                                        onDismissRequest = { audioMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Auto") },
                                            onClick = {
                                                selectTrack(C.TRACK_TYPE_AUDIO)
                                                audioMenuExpanded = false
                                            }
                                        )
                                        audioTrackChoices.forEach { choice ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(if (choice.isSelected) "✓ ${choice.label}" else choice.label)
                                                },
                                                onClick = {
                                                    selectTrack(C.TRACK_TYPE_AUDIO, choice)
                                                    audioMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (textTrackChoices.isNotEmpty()) {
                            Box(Modifier.padding(horizontal = 8.dp)) {
                                Button(
                                    enabled = canSetTrackSelectionParameters,
                                    onClick = { textMenuExpanded = true }
                                ) {
                                    val selectedText = textTrackChoices
                                        .firstOrNull(TrackChoice::isSelected)?.label
                                        ?: if (subtitlesDisabled) "Désactivés" else "Auto"
                                    Text("Sous-titres : $selectedText")
                                }
                                DropdownMenu(
                                    expanded = textMenuExpanded,
                                    onDismissRequest = { textMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Désactivés") },
                                        onClick = {
                                            selectTrack(C.TRACK_TYPE_TEXT, disabled = true)
                                            textMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Auto") },
                                        onClick = {
                                            selectTrack(C.TRACK_TYPE_TEXT)
                                            textMenuExpanded = false
                                        }
                                    )
                                    textTrackChoices.forEach { choice ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(if (choice.isSelected) "✓ ${choice.label}" else choice.label)
                                            },
                                            onClick = {
                                                selectTrack(C.TRACK_TYPE_TEXT, choice)
                                                textMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                enabled = canSkipPrevious,
                                onClick = { player?.seekToPreviousMediaItem() }
                            ) {
                                Text("← Précédent")
                            }
                            Button(
                                enabled = canSkipNext,
                                onClick = { player?.seekToNextMediaItem() }
                            ) {
                                Text("Suivant →")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                enabled = canSetShuffleMode,
                                onClick = {
                                    player?.shuffleModeEnabled = !shuffleModeEnabled
                                }
                            ) {
                                Text("Aléatoire")
                            }
                            Button(
                                enabled = canSetRepeatMode,
                                onClick = {
                                    val nextRepeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    player?.repeatMode = nextRepeatMode
                                }
                            ) {
                                Text("Répéter")
                            }
                        }
                        Text(
                            text = buildList {
                                add(if (shuffleModeEnabled) "Aléatoire activé" else "Aléatoire désactivé")
                                add(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> "Répétition du titre"
                                        Player.REPEAT_MODE_ALL -> "Répétition de la playlist"
                                        else -> "Répétition désactivée"
                                    }
                                )
                            }.joinToString(" • "),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        Button(
                            onClick = {
                                (player as? MediaController)?.sendCustomCommand(
                                    SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY),
                                    Bundle.EMPTY
                                )
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris")
                        }
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            item {
                                Text(
                                    "Playlist (${playlistItems.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            itemsIndexed(playlistItems) { index, item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (index == currentIndex) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.mediaMetadata.title?.toString() ?: item.mediaId,
                                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                            color = if (index == currentIndex) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    player?.seekToDefaultPosition(index)
                                                    player?.play()
                                                }
                                                .padding(8.dp)
                                        )
                                        TextButton(
                                            enabled = canChangePlaylist && index > 0,
                                            onClick = { player?.moveMediaItem(index, index - 1) }
                                        ) {
                                            Text("↑")
                                        }
                                        TextButton(
                                            enabled = canChangePlaylist && index < playlistItems.lastIndex,
                                            onClick = { player?.moveMediaItem(index, index + 1) }
                                        ) {
                                            Text("↓")
                                        }
                                        TextButton(
                                            enabled = canChangePlaylist,
                                            onClick = { player?.removeMediaItem(index) }
                                        ) {
                                            Text("Retirer")
                                        }
                                    }
                                }
                            }
                            item {
                                Text(
                                    "Ajouter à la playlist",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            metadataMessage?.let { message ->
                                item {
                                    Text(
                                        message,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(tracks) { track ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp)) {
                                        Text(
                                            track.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(top = 10.dp, end = 8.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Button(
                                                enabled = canChangePlaylist,
                                                onClick = { player?.addMediaItem(createMediaItem(track)) }
                                            ) {
                                                Text("Ajouter")
                                            }
                                            TextButton(onClick = { inspectMetadata(track) }) {
                                                Text("Infos")
                                            }
                                            val download = downloads[track.url]
                                            TextButton(
                                                enabled = (track.url.startsWith("https://") ||
                                                    track.url.startsWith("http://")) && track.url != HLS_LIVE_URL,
                                                onClick = {
                                                    if (download?.state == Download.STATE_COMPLETED ||
                                                        download?.state == Download.STATE_DOWNLOADING ||
                                                        download?.state == Download.STATE_QUEUED
                                                    ) {
                                                        removeOfflineDownload(track.url)
                                                    } else {
                                                        addOfflineDownload(track)
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    when {
                                                        track.url == HLS_LIVE_URL -> "Direct"
                                                        download?.state == Download.STATE_COMPLETED -> "Retirer"
                                                        download?.state == Download.STATE_DOWNLOADING ||
                                                            download?.state == Download.STATE_QUEUED -> "Annuler"
                                                        else -> "Hors ligne"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                downloads[track.url]?.let { download ->
                                    Text(
                                        downloadStatus(download),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        EditorScreen()
                    }
                    else -> {
                        CameraScreen()
                    }
                }
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        val manager = OfflineDownloads.getManager(this)
        downloadManager = manager
        manager.addListener(downloadManagerListener)
        DownloadService.start(this, OfflineDownloadService::class.java)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onMediaButtonPreferencesChanged(
                    controller: MediaController,
                    mediaButtonPreferences: List<CommandButton>,
                ) {
                    readFavorite(mediaButtonPreferences)
                }
                override fun onError(controller: MediaController, sessionError: SessionError) {
                    Toast.makeText(this@MainActivity, sessionError.message, Toast.LENGTH_LONG).show()
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener({
            // onStop() may have released this future before its listener is run.
            if (controllerFuture !== future) return@addListener

            val controller = try {
                future.get()
            } catch (exception: Exception) {
                if (exception is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                Toast.makeText(
                    this,
                    exception.cause?.localizedMessage
                        ?: exception.localizedMessage
                        ?: "Impossible de se connecter au lecteur.",
                    Toast.LENGTH_LONG
                ).show()
                return@addListener
            }

            if (controller.mediaItemCount == 0) {
                val items = tracks.map(::createMediaItem)
                controller.setMediaItems(items)
                controller.prepare()
                controller.play()
            }
            controller.addListener(playbackUiListener)
            player = controller
            updatePlaylistState(controller)
            playbackState = controller.playbackState
            isPlaying = controller.isPlaying
            playbackError = controller.playerError?.let { error ->
                error.localizedMessage ?: error.cause?.localizedMessage ?: error.errorCodeName
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        super.onStop()
        downloadManager?.removeListener(downloadManagerListener)
        downloadManager = null
        player?.removeListener(playbackUiListener)
        player = null
        val future = controllerFuture
        controllerFuture = null
        future?.let { MediaController.releaseFuture(it) }
    }

    override fun onDestroy() {
        downloadLookupExecutor.shutdown()
        super.onDestroy()
    }
}
