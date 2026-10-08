package com.lyse.mediaplayer

import android.Manifest
import android.content.ComponentName
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture


private const val DASH_URL =
    "https://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=51AF5F39AB0CEC3E5497CD9C900EBFEAECCCB5C7.8506521BFC350652163895D4C26DEE124209AA9E&key=ik0"
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

class MainActivity : ComponentActivity() {

    private val tracks by lazy {
        listOf(
            Track("Big Buck Bunny (video)", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"),
            Track("Jazz in Paris (audio)", "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"),
            Track("Local sample (audio)", "android.resource://$packageName/${R.raw.sample_audio}"),
            Track("Streaming adaptatif DASH (vidéo)", DASH_URL, MimeTypes.APPLICATION_MPD),
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

    private fun createMediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.url)
            .setUri(track.url)
            .setMimeType(track.mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()

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
            Column(Modifier.systemBarsPadding()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Lecteur") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Éditeur") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Caméra") })
                }
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
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            itemsIndexed(playlistItems) { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.mediaMetadata.title?.toString() ?: item.mediaId,
                                        fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
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
                            item {
                                Text(
                                    "Ajouter à la playlist",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(tracks) { track ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(track.title, modifier = Modifier.weight(1f))
                                    Button(
                                        enabled = canChangePlaylist,
                                        onClick = { player?.addMediaItem(createMediaItem(track)) }
                                    ) {
                                        Text("Ajouter")
                                    }
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

    @OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
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
        player?.removeListener(playbackUiListener)
        player = null
        val future = controllerFuture
        controllerFuture = null
        future?.let { MediaController.releaseFuture(it) }
    }
}
