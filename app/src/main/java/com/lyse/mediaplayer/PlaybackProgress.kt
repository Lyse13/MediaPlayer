package com.lyse.mediaplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlaybackProgress(player: Player?) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        while (player != null) {
            if (!dragging) {
                position = player.currentPosition
                duration = if (player.duration == C.TIME_UNSET) 0L else player.duration
            }
            delay(500.milliseconds)
        }
    }

    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownFraction = if (dragging) dragFraction else fraction
    val shownPosition = if (dragging) (dragFraction * duration).toLong() else position

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Slider(
            value = shownFraction,
            enabled = duration > 0,
            onValueChange = {
                dragging = true
                dragFraction = it
            },
            onValueChangeFinished = {
                player?.seekTo((dragFraction * duration).toLong())
                position = (dragFraction * duration).toLong()
                dragging = false
            }
        )
        Text("${formatTime(shownPosition)} / ${formatTime(duration)}")
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}