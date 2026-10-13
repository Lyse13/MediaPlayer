package com.lyse.mediaplayer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SlowMotionCameraScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var recorder by remember { mutableStateOf<Recorder?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var frameRate by remember { mutableStateOf<Int?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var status by remember { mutableStateOf("Initialisation du mode ralenti…") }
    val isRecording = recording != null

    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var capture: VideoCapture<Recorder>? = null
        try {
            provider = ProcessCameraProvider.awaitInstance(context)
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraInfo = provider.getCameraInfo(selector)
            val highSpeedCapabilities = Recorder.getHighSpeedVideoCapabilities(cameraInfo)
            if (highSpeedCapabilities == null) {
                status = "Le ralenti haute fréquence n’est pas pris en charge par cette caméra."
                return@LaunchedEffect
            }

            val qualities = highSpeedCapabilities.getSupportedQualities(DynamicRange.SDR)
            val quality = qualities.firstOrNull()
            if (quality == null) {
                status = "Aucune résolution haute fréquence n’est disponible."
                return@LaunchedEffect
            }

            val activeRecorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality))
                .build()
            val activeCapture = VideoCapture.withOutput(activeRecorder)
            val activePreview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }
            val configBuilder = HighSpeedVideoSessionConfig.Builder(activeCapture)
                .setPreview(activePreview)
                .setSlowMotionEnabled(true)
            val selectedFrameRate = cameraInfo
                .getSupportedFrameRateRanges(configBuilder.build())
                .maxByOrNull { it.upper }
            if (selectedFrameRate == null) {
                status = "Aucune fréquence de capture ralentie n’est disponible."
                return@LaunchedEffect
            }

            configBuilder.setFrameRateRange(selectedFrameRate)
            provider.bindToLifecycle(lifecycleOwner, selector, configBuilder.build())
            preview = activePreview
            capture = activeCapture
            recorder = activeRecorder
            videoCapture = activeCapture
            frameRate = selectedFrameRate.upper
            status = "Ralenti prêt · ${selectedFrameRate.upper} images/s"
            awaitCancellation()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            status = "Mode ralenti indisponible : ${exception.localizedMessage ?: "erreur caméra"}"
        } finally {
            recording?.stop()
            if (provider != null && preview != null && capture != null) {
                provider.unbind(preview, capture)
            }
            recorder = null
            videoCapture = null
            frameRate = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { view -> PreviewView(view).also { previewView = it } }
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White
            ) {
                Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            if (recorder == null && status.startsWith("Initialisation")) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(enabled = !isRecording, onClick = onExit) {
                Text("Retour caméra")
            }
            Button(
                enabled = videoCapture != null,
                onClick = {
                    if (isRecording) {
                        recording?.stop()
                    } else {
                        val activeRecorder = recorder ?: return@Button
                        val activeCapture = videoCapture ?: return@Button
                        recording = startSlowMotionRecording(context, activeRecorder, activeCapture) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> status = "Ralenti en cours…"
                                is VideoRecordEvent.Finalize -> {
                                    recording = null
                                    status = if (event.hasError()) {
                                        "Erreur d’enregistrement (${event.error})"
                                    } else {
                                        "Ralenti enregistré dans Movies/MediaPlayer"
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            ) {
                Text(if (isRecording) "Arrêter" else "Enregistrer ralenti")
            }
        }
        Text(
            "${frameRate ?: "—"} images/s · ralenti prêt à lire",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun startSlowMotionRecording(
    context: Context,
    recorder: Recorder,
    videoCapture: VideoCapture<Recorder>,
    onEvent: (VideoRecordEvent) -> Unit
): Recording {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
        .format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "ralenti-$timestamp")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MediaPlayer")
        }
    }
    val outputOptions = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()

    return videoCapture.output.prepareRecording(context, outputOptions)
        .start(ContextCompat.getMainExecutor(context), onEvent)
}
