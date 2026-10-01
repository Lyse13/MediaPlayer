package com.lyse.mediaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView

data class Track(val title: String, val url: String)

class MainActivity : ComponentActivity() {

    private val tracks by lazy {
        listOf(
            Track("Big Buck Bunny (video)", "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"),
            Track("Jazz in Paris (audio)", "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"),
            Track("Local sample (audio)", "android.resource://$packageName/${R.raw.sample_audio}"),
        )
    }

    private var player by mutableStateOf<ExoPlayer?>(null)
    private var mediaSession: MediaSession? = null
    private var currentIndex by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    factory = { context -> PlayerView(context) },
                    update = { view -> view.player = player }
                )
                LazyColumn {
                    itemsIndexed(tracks) { index, track ->
                        Text(
                            text = track.title,
                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    player?.seekToDefaultPosition(index)
                                    player?.play()
                                }
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val exo = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, exo).build()
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
                .build()
        }
        exo.setMediaItems(items)
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exo.currentMediaItemIndex
            }
        })
        exo.prepare()
        exo.play()
        currentIndex = 0
        player = exo
    }

    override fun onStop() {
        super.onStop()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }
}