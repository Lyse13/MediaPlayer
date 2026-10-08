package com.lyse.mediaplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var hasAudio by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var useBackCamera by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var luma by remember { mutableDoubleStateOf(0.0) }

    var recording by remember { mutableStateOf<Recording?>(null) }
    var seconds by remember { mutableLongStateOf(0L) }
    val isRecording = recording != null

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasCamera = granted(Manifest.permission.CAMERA)
        hasAudio = granted(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { !granted(it) }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            analysisExecutor.shutdown()
        }
    }

    fun setAnalysis(enabled: Boolean) {
        analyzing = enabled
        if (enabled) {
            controller.setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS
            )
            controller.setImageAnalysisAnalyzer(
                analysisExecutor,
                LuminosityAnalyzer { value -> luma = value }
            )
        } else {
            controller.clearImageAnalysisAnalyzer()
            controller.setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE
            )
        }
    }

    val currentRecording by rememberUpdatedState(recording)
    DisposableEffect(hasCamera) {
        if (hasCamera) controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            currentRecording?.stop()
            controller.unbind()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!hasCamera) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Autorisation requise", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "L’accès à la caméra est nécessaire pour utiliser cet onglet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    }) {
                        Text("Autoriser la caméra")
                    }
                }
            }
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PreviewView(ctx).also { it.controller = controller } }
            )
            val lines = buildList {
                if (isRecording) add("● REC  %02d:%02d".format(seconds / 60, seconds % 60))
                status?.let { add(it) }
                if (analyzing) add("Luminosité moyenne : %.0f / 255".format(luma))
                if (isEmpty() && !hasAudio) add("Micro non autorisé : les vidéos seront sans son.")
            }
            if (lines.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        lines.joinToString("\n"),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("Analyse de luminosité", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Désactive la capture vidéo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = analyzing,
                        enabled = !isRecording,
                        onCheckedChange = { setAnalysis(it) }
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        enabled = !isRecording,
                        onClick = {
                            useBackCamera = !useBackCamera
                            controller.cameraSelector =
                                if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA
                                else CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                    ) {
                        Text("Retourner")
                    }
                    Button(
                        enabled = !isRecording,
                        onClick = { takePhoto(context, controller) { status = it } }
                    ) {
                        Text("Photo")
                    }
                    Button(enabled = !analyzing, onClick = {
                        if (isRecording) {
                            recording?.stop()
                        } else {
                            status = null
                            seconds = 0L
                            recording = startRecording(context, controller, hasAudio) { event ->
                                when (event) {
                                    is VideoRecordEvent.Status ->
                                        seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000
                                    is VideoRecordEvent.Finalize -> {
                                        recording = null
                                        status = if (event.hasError()) {
                                            "Erreur vidéo (code ${event.error})"
                                        } else {
                                            "Vidéo enregistrée dans Movies/MediaPlayer"
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }) {
                        Text(if (isRecording) "Arrêter" else "Vidéo")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun startRecording(
    context: Context,
    controller: LifecycleCameraController,
    withAudio: Boolean,
    onEvent: (VideoRecordEvent) -> Unit
): Recording {
    val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MediaPlayer")
        }
    }
    val options = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(values).build()

    return controller.startRecording(
        options,
        AudioConfig.create(withAudio),
        ContextCompat.getMainExecutor(context)
    ) { event -> onEvent(event) }
}

private fun takePhoto(
    context: Context,
    controller: LifecycleCameraController,
    onStatus: (String) -> Unit
) {
    val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaPlayer")
        }
    }
    val options = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()

    controller.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onStatus("Photo enregistrée dans Pictures/MediaPlayer")
            }

            override fun onError(exception: ImageCaptureException) {
                onStatus("Erreur : ${exception.message}")
            }
        }
    )
}
