package com.lyse.mediaplayer

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = future.get()
            if (controller.mediaItemCount == 0) {
                val items = tracks.map { track ->
                    MediaItem.Builder()
                        .setUri(track.url)
                        .setMimeType(track.mimeType)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
                        .build()
                }
                controller.setMediaItems(items)
                controller.prepare()
                controller.play()
            }
            currentIndex = controller.currentMediaItemIndex
            controller.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentIndex = controller.currentMediaItemIndex
                }
            })
            player = controller
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        super.onStop()
        player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}