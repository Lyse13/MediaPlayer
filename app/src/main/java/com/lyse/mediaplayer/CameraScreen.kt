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
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Observer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var hasAudio by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var useBackCamera by remember { mutableStateOf(true) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var photoCaptureMode by remember {
        mutableIntStateOf(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    }
    var videoQuality by remember { mutableStateOf(Quality.HD) }
    var videoQualityMenuExpanded by remember { mutableStateOf(false) }
    var videoDynamicRange by remember { mutableStateOf(DynamicRange.SDR) }
    var slowMotionMode by remember { mutableStateOf(false) }
    var torchState by remember { mutableIntStateOf(TorchState.OFF) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var luma by remember { mutableDoubleStateOf(0.0) }

    var recording by remember { mutableStateOf<Recording?>(null) }
    var seconds by remember { mutableLongStateOf(0L) }
    var photoCaptureInProgress by remember { mutableStateOf(false) }
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
            setImageCaptureMode(photoCaptureMode)
            setVideoCaptureQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
        }
    }

    val cameraInfo = controller.cameraInfo
    val exposureState = cameraInfo?.exposureState
    val exposureRange = exposureState?.exposureCompensationRange
    val hasFlash = cameraInfo?.hasFlashUnit() == true
    val supportedVideoDynamicRanges = remember(cameraInfo) {
        cameraInfo?.let {
            runCatching { Recorder.getVideoCapabilities(it).supportedDynamicRanges.toList() }
                .getOrDefault(emptyList())
        }.orEmpty()
    }
    val supportedHdrDynamicRange = supportedVideoDynamicRanges.firstOrNull {
        it == DynamicRange.HLG_10_BIT || it == DynamicRange.HDR10_10_BIT
    }
    val supportedVideoQualities = remember(cameraInfo, videoDynamicRange) {
        cameraInfo?.let {
            runCatching {
                Recorder.getVideoCapabilities(it).getSupportedQualities(videoDynamicRange)
            }.getOrDefault(emptyList())
        }.orEmpty()
    }

    DisposableEffect(controller, lifecycleOwner) {
        val torchObserver = Observer<Int> { state ->
            torchState = state ?: TorchState.OFF
        }
        val zoomObserver = Observer<ZoomState> { state ->
            state?.let {
                zoomRatio = it.zoomRatio
                minZoomRatio = it.minZoomRatio
                maxZoomRatio = it.maxZoomRatio
            }
        }
        controller.torchState.observe(lifecycleOwner, torchObserver)
        controller.zoomState.observe(lifecycleOwner, zoomObserver)
        onDispose {
            controller.torchState.removeObserver(torchObserver)
            controller.zoomState.removeObserver(zoomObserver)
        }
    }

    LaunchedEffect(useBackCamera, torchState) {
        exposureIndex = controller.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0
    }

    LaunchedEffect(useBackCamera, supportedVideoQualities) {
        if (supportedVideoQualities.isNotEmpty() && videoQuality !in supportedVideoQualities) {
            videoQuality = supportedVideoQualities.first()
            controller.setVideoCaptureQualitySelector(
                QualitySelector.from(
                    videoQuality,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
        }
    }

    LaunchedEffect(useBackCamera, supportedVideoDynamicRanges) {
        if (supportedVideoDynamicRanges.isNotEmpty() &&
            videoDynamicRange !in supportedVideoDynamicRanges
        ) {
            videoDynamicRange = DynamicRange.SDR
            controller.setVideoCaptureDynamicRange(DynamicRange.SDR)
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
    DisposableEffect(hasCamera, slowMotionMode) {
        if (hasCamera && !slowMotionMode) controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            currentRecording?.stop()
            if (!slowMotionMode) controller.unbind()
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

        if (slowMotionMode) {
            SlowMotionCameraScreen(onExit = { slowMotionMode = false })
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
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Touchez l’aperçu pour faire le point · pincez pour zoomer",
                    style = MaterialTheme.typography.labelMedium,
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            enabled = !isRecording && supportedVideoQualities.isNotEmpty(),
                            onClick = { videoQualityMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Vidéo : ${qualityLabel(videoQuality)}")
                        }
                        DropdownMenu(
                            expanded = videoQualityMenuExpanded,
                            onDismissRequest = { videoQualityMenuExpanded = false }
                        ) {
                            supportedVideoQualities.forEach { quality ->
                                DropdownMenuItem(
                                    text = { Text(qualityLabel(quality)) },
                                    onClick = {
                                        videoQuality = quality
                                        controller.setVideoCaptureQualitySelector(
                                            QualitySelector.from(
                                                quality,
                                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                                            )
                                        )
                                        videoQualityMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        enabled = !isRecording,
                        onClick = {
                            photoCaptureMode = if (
                                photoCaptureMode == ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            ) {
                                ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                            } else {
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            }
                            controller.setImageCaptureMode(photoCaptureMode)
                        }
                    ) {
                        Text(
                            if (photoCaptureMode == ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) {
                                "Photo : qualité"
                            } else {
                                "Photo : rapide"
                            }
                        )
                    }
                }
                OutlinedButton(
                    enabled = !isRecording && !analyzing,
                    onClick = { slowMotionMode = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mode ralenti haute fréquence")
                }
                if (supportedHdrDynamicRange != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text("Vidéo HDR")
                            Text(
                                "${dynamicRangeLabel(supportedHdrDynamicRange)} · selon l’écran et l’appareil",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = videoDynamicRange != DynamicRange.SDR,
                            enabled = !isRecording,
                            onCheckedChange = { enabled ->
                                val nextRange = if (enabled) {
                                    supportedHdrDynamicRange
                                } else {
                                    DynamicRange.SDR
                                }
                                val qualities = cameraInfo?.let {
                                    runCatching {
                                        Recorder.getVideoCapabilities(it)
                                            .getSupportedQualities(nextRange)
                                    }.getOrDefault(emptyList())
                                }.orEmpty()
                                if (qualities.isNotEmpty()) {
                                    videoDynamicRange = nextRange
                                    val selectedQuality = videoQuality.takeIf { it in qualities }
                                        ?: qualities.first()
                                    videoQuality = selectedQuality
                                    controller.setVideoCaptureDynamicRange(nextRange)
                                    controller.setVideoCaptureQualitySelector(
                                        QualitySelector.from(
                                            selectedQuality,
                                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                                        )
                                    )
                                } else {
                                    status = "Aucune résolution compatible avec ce mode vidéo."
                                }
                            }
                        )
                    }
                }
                if (maxZoomRatio > minZoomRatio) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Zoom")
                        Text("%.1f×".format(zoomRatio))
                    }
                    Slider(
                        value = zoomRatio.coerceIn(minZoomRatio, maxZoomRatio),
                        onValueChange = { value ->
                            zoomRatio = value
                            controller.cameraControl?.setZoomRatio(value)
                        },
                        valueRange = minZoomRatio..maxZoomRatio,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
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
                        enabled = !isRecording && hasFlash,
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                            controller.imageCaptureFlashMode = flashMode
                        }
                    ) {
                        Text(
                            "Flash : ${when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> "auto"
                                ImageCapture.FLASH_MODE_ON -> "activé"
                                else -> "désactivé"
                            }}"
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Torche")
                        Switch(
                            checked = torchState == TorchState.ON,
                            enabled = hasFlash,
                            onCheckedChange = { enabled ->
                                val future = controller.enableTorch(enabled)
                                future.addListener({
                                    try {
                                        future.get()
                                    } catch (exception: Exception) {
                                        status = "Torche indisponible : ${exception.cause?.localizedMessage ?: exception.localizedMessage ?: "erreur caméra"}"
                                    }
                                }, ContextCompat.getMainExecutor(context))
                            }
                        )
                    }
                }
                if (exposureState?.isExposureCompensationSupported == true &&
                    exposureRange != null && exposureRange.lower < exposureRange.upper
                ) {
                    val minIndex = exposureRange.lower
                    val maxIndex = exposureRange.upper
                    val stepEv = exposureState.exposureCompensationStep.toDouble()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Exposition")
                        Text("%+.1f EV".format(exposureIndex * stepEv))
                    }
                    Slider(
                        value = exposureIndex.toFloat().coerceIn(minIndex.toFloat(), maxIndex.toFloat()),
                        onValueChange = { value ->
                            val newIndex = value.roundToInt().coerceIn(minIndex, maxIndex)
                            exposureIndex = newIndex
                            controller.cameraControl?.setExposureCompensationIndex(newIndex)
                        },
                        valueRange = minIndex.toFloat()..maxIndex.toFloat(),
                        steps = (maxIndex - minIndex - 1).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                } else {
                    Text(
                        "Réglage d’exposition indisponible sur cette caméra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                            flashMode = ImageCapture.FLASH_MODE_OFF
                            controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                            controller.cameraSelector =
                                if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA
                                else CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                    ) {
                        Text("Retourner")
                    }
                    Button(
                        enabled = !photoCaptureInProgress,
                        onClick = {
                            photoCaptureInProgress = true
                            takePhoto(context, controller) {
                                photoCaptureInProgress = false
                                status = it
                            }
                        }
                    ) {
                        Text(if (photoCaptureInProgress) "Photo…" else "Photo")
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

private fun qualityLabel(quality: Quality): String = when (quality) {
    Quality.UHD -> "4K · UHD"
    Quality.FHD -> "1080p · FHD"
    Quality.HD -> "720p · HD"
    Quality.SD -> "480p · SD"
    else -> "Auto"
}

private fun dynamicRangeLabel(dynamicRange: DynamicRange): String = when (dynamicRange) {
    DynamicRange.HLG_10_BIT -> "HLG"
    DynamicRange.HDR10_10_BIT -> "HDR10"
    else -> "HDR"
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

    try {
        controller.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onStatus("Photo enregistrée dans Pictures/MediaPlayer")
                }

                override fun onError(exception: ImageCaptureException) {
                    onStatus("Erreur photo : ${exception.message}")
                }
            }
        )
    } catch (exception: IllegalStateException) {
        onStatus("Capture photo indisponible : ${exception.localizedMessage ?: "caméra non prête"}")
    }
}
