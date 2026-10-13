package com.lyse.mediaplayer

import android.net.Uri
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EditorScreen() {
    val context = LocalContext.current

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var range by remember { mutableStateOf(0f..1f) }
    var rotate by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(false) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    var exportInProgress by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var inProgressFile by remember { mutableStateOf<File?>(null) }

    val player = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY &&
                            durationMs == 0L &&
                            exo.duration != C.TIME_UNSET
                        ) {
                            durationMs = exo.duration
                        }
                    }
                })
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (exportInProgress) {
                activeTransformer?.cancel()
                inProgressFile?.delete()
            }
            player.release()
        }
    }

    LaunchedEffect(exportInProgress, activeTransformer) {
        val transformer = activeTransformer
        if (exportInProgress && transformer != null) {
            val progressHolder = ProgressHolder()
            while (isActive && exportInProgress) {
                if (transformer.getProgress(progressHolder) ==
                    Transformer.PROGRESS_STATE_AVAILABLE
                ) {
                    exportProgress = progressHolder.progress
                }
                delay(500)
            }
        }
    }

    fun applyEdits(
        rotationEnabled: Boolean = rotate,
        cropEnabled: Boolean = crop,
    ) {
        val uri = videoUri ?: return
        val startMs = (range.start * durationMs).toLong()
        val endMs = (range.endInclusive * durationMs).toLong()
        if (endMs - startMs < 500) return // avoid an empty clip

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()

        val effects = buildList<Effect> {
            if (rotationEnabled) {
                add(ScaleAndRotateTransformation.Builder().setRotationDegrees(90f).build())
            }
            if (cropEnabled) add(Crop(-0.5f, 0.5f, -0.5f, 0.5f)) // keeps the center half
        }
        player.setVideoEffects(effects)
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    fun startExport() {
        val uri = videoUri ?: return
        val startMs = (range.start * durationMs).toLong()
        val endMs = (range.endInclusive * durationMs).toLong()
        if (endMs - startMs < 500L) {
            exportMessage = "Choisissez une durée d’au moins 0,5 seconde."
            return
        }

        exportedFile?.delete()
        exportedFile = null
        exportProgress = 0
        exportMessage = null
        val outputFile = File(context.cacheDir, "editor-export-${System.currentTimeMillis()}.mp4")
        inProgressFile = outputFile

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()
        val videoEffects = buildList<Effect> {
            if (rotate) {
                add(ScaleAndRotateTransformation.Builder().setRotationDegrees(90f).build())
            }
            if (crop) add(Crop(-0.5f, 0.5f, -0.5f, 0.5f))
        }
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        lateinit var createdTransformer: Transformer
        createdTransformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (activeTransformer !== createdTransformer) return
                    activeTransformer = null
                    inProgressFile = null
                    exportedFile = outputFile
                    exportProgress = 100
                    exportInProgress = false
                    exportMessage = "Export terminé. Le fichier est temporairement dans le cache de l’application."
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (activeTransformer !== createdTransformer) return
                    activeTransformer = null
                    inProgressFile = null
                    outputFile.delete()
                    exportInProgress = false
                    exportMessage = "Échec de l’export : ${exportException.localizedMessage ?: exportException.errorCodeName}"
                }
            })
            .build()

        activeTransformer = createdTransformer
        exportInProgress = true
        exportMessage = "Export en cours…"
        try {
            createdTransformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (exception: Exception) {
            activeTransformer = null
            inProgressFile = null
            outputFile.delete()
            exportInProgress = false
            exportMessage = "Échec de l’export : ${exception.localizedMessage ?: exception.javaClass.simpleName}"
        }
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        inProgressFile?.delete()
        inProgressFile = null
        exportInProgress = false
        exportMessage = "Export annulé."
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && !exportInProgress) {
            videoUri = uri
            durationMs = 0L
            range = 0f..1f
            rotate = false
            crop = false
            player.setVideoEffects(emptyList())
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Button(enabled = !exportInProgress, onClick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Choisir une vidéo")
        }
        Spacer(Modifier.height(16.dp))

        if (videoUri == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Aucune vidéo sélectionnée", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Choisissez une vidéo pour prévisualiser la découpe, la rotation et le recadrage.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            factory = { ctx -> PlayerView(ctx).also { it.player = player } }
        )

        if (durationMs > 0) {
            Spacer(Modifier.height(16.dp))
            val startS = range.start * durationMs / 1000f
            val endS = range.endInclusive * durationMs / 1000f
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Réglages de l’aperçu", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Découpe : %.1f s → %.1f s (sur %.1f s)".format(
                            startS,
                            endS,
                            durationMs / 1000f
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RangeSlider(
                        value = range,
                        enabled = !exportInProgress,
                        onValueChange = { range = it },
                        onValueChangeFinished = { applyEdits() }
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rotation 90°")
                        Switch(
                            checked = rotate,
                            enabled = !exportInProgress,
                            onCheckedChange = {
                                rotate = it
                                applyEdits(rotationEnabled = it)
                            }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recadrage centré")
                        Switch(
                            checked = crop,
                            enabled = !exportInProgress,
                            onCheckedChange = {
                                crop = it
                                applyEdits(cropEnabled = it)
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = !exportInProgress && endS - startS >= 0.5f,
                        onClick = ::startExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (exportInProgress) "Export en cours…" else "Exporter la vidéo")
                    }
                    if (exportInProgress) {
                        Spacer(Modifier.height(8.dp))
                        if (exportProgress > 0) {
                            LinearProgressIndicator(
                                progress = { exportProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("$exportProgress %")
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                Text("Préparation de l’export…")
                            }
                        }
                        OutlinedButton(
                            onClick = ::cancelExport,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Annuler l’export")
                        }
                    }
                    exportMessage?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    exportedFile?.let { file ->
                        OutlinedButton(
                            enabled = !exportInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                player.setVideoEffects(emptyList())
                                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                                player.prepare()
                                player.play()
                            }
                        ) {
                            Text("Lire le résultat exporté")
                        }
                    }
                }
            }
        }
    }
}
