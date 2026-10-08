package com.lyse.mediaplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlaybackProgress(player: Player?) {
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(C.TIME_UNSET) }
    var liveOffset by remember(player) { mutableLongStateOf(C.TIME_UNSET) }
    var isLive by remember(player) { mutableStateOf(false) }
    var isSeekable by remember(player) { mutableStateOf(false) }
    var canSeekToLive by remember(player) { mutableStateOf(false) }
    var dragging by remember(player) { mutableStateOf(false) }
    var dragFraction by remember(player) { mutableFloatStateOf(0f) }

    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (true) {
            if (!dragging) {
                val canReadCurrentItem =
                    activePlayer.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                isLive = canReadCurrentItem && activePlayer.isCurrentMediaItemLive
                isSeekable = canReadCurrentItem && activePlayer.isCurrentMediaItemSeekable
                position = if (canReadCurrentItem) activePlayer.currentPosition.coerceAtLeast(0L) else 0L
                duration = if (canReadCurrentItem) {
                    activePlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: C.TIME_UNSET
                } else {
                    C.TIME_UNSET
                }
                liveOffset = if (isLive) activePlayer.currentLiveOffset else C.TIME_UNSET
                canSeekToLive = isLive &&
                    isSeekable &&
                    liveOffset > 1_500L &&
                    activePlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            }
            delay(500.milliseconds)
        }
    }

    val canSeek = player != null &&
        duration > 0L &&
        isSeekable &&
        player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    val fraction = if (duration > 0L) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownFraction = if (dragging) dragFraction else fraction
    val shownPosition = if (dragging && duration > 0L) {
        (dragFraction * duration).toLong()
    } else {
        position
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (duration > 0L) {
            Slider(
                value = shownFraction,
                enabled = canSeek,
                onValueChange = {
                    dragging = true
                    dragFraction = it
                },
                onValueChangeFinished = {
                    val activePlayer = player
                    if (activePlayer != null && canSeek) {
                        val seekPosition = (dragFraction * duration).toLong()
                        activePlayer.seekTo(seekPosition)
                        position = seekPosition
                    }
                    dragging = false
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isLive && liveOffset != C.TIME_UNSET && liveOffset > 1_000L ->
                        "EN DIRECT · −${formatTime(liveOffset)}"
                    isLive -> "EN DIRECT"
                    duration > 0L -> "${formatTime(shownPosition)} / ${formatTime(duration)}"
                    else -> "Durée inconnue · ${formatTime(position)}"
                }
            )
            if (canSeekToLive) {
                TextButton(
                    onClick = { player?.seekToDefaultPosition() }
                ) {
                    Text("Revenir au direct")
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
