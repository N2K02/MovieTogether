package com.movietogether.app.watch

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Play/pause controls are shared for every participant; the host serializes changes.
 * Seek controls stay on the host. Display names aren't authenticated identities.
 */
@OptIn(UnstableApi::class)
@Composable
fun WatchPlayer(uri: Uri, isHost: Boolean, address: RoomAddress?, displayName: String) {
    val context = LocalContext.current
    val snapshot by RoomRuntime.playback.collectAsState()
    val player = remember(uri, isHost, address) {
        val instance = ExoPlayer.Builder(context).build()
        if (!isHost && address != null) {
            val factory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${address.token}"))
            instance.setMediaSource(
                ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(uri)),
            )
        } else {
            instance.setMediaItem(MediaItem.fromUri(uri))
        }
        instance.prepare()
        instance
    }

    val currentDisplayName by rememberUpdatedState(displayName)
    DisposableEffect(player) {
        onDispose {
            // Don't let guests carry on playing after the host leaves the player screen.
            if (isHost && RoomRuntime.playback.value.playing) {
                RoomRuntime.requestPlayback(false, currentDisplayName, player.currentPosition)
            }
            player.release()
        }
    }

    if (isHost) {
        LaunchedEffect(player) {
            while (isActive) {
                RoomRuntime.hostPlayback(player.currentPosition)
                delay(1_000)
            }
        }
    }

    LaunchedEffect(player, snapshot.revision) {
        if (snapshot.revision > 0) {
            if (abs(player.currentPosition - snapshot.positionMs) > 1_200L) {
                player.seekTo(snapshot.positionMs)
            }
            if (player.playWhenReady != snapshot.playing) {
                player.playWhenReady = snapshot.playing
            }
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
            }
        },
        update = { it.player = player; it.useController = false },
        modifier = Modifier.fillMaxWidth().height(240.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isHost) {
            IconButton(onClick = {
                RoomRuntime.hostSeekTo((player.currentPosition - 10_000L).coerceAtLeast(0))
            }) { Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds") }
        }
        Button(onClick = {
            RoomRuntime.requestPlayback(!snapshot.playing, displayName,
                if (isHost) player.currentPosition else null)
        }) {
            Icon(
                if (snapshot.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(if (snapshot.playing) "Pause all" else "Play all")
        }
        if (isHost) {
            IconButton(onClick = {
                RoomRuntime.hostSeekTo(player.currentPosition + 10_000L)
            }) { Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds") }
        }
    }
    Text(
        when {
            snapshot.changedBy != null && snapshot.playing -> "Resumed by ${snapshot.changedBy}"
            snapshot.changedBy != null && !snapshot.playing -> "Paused by ${snapshot.changedBy}"
            snapshot.playing -> "Playing together"
            else -> "Ready to watch"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!isHost) Text(
        "Playback follows the shared room controls. Anyone can pause or resume.",
        style = MaterialTheme.typography.bodySmall,
    )
}
