package com.lyse.mediaplayer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun EditorScreen() {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }

    val player = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .build()
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }) {
            Text("Choisir une vidéo")
        }
        Spacer(Modifier.height(16.dp))
        if (videoUri != null) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                factory = { ctx -> PlayerView(ctx).also { it.player = player } }
            )
        } else {
            Text("Aucune vidéo sélectionnée")
        }
    }
}