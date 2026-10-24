package com.movietogether.app.watch

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
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
 * Host plays its SAF file locally; viewer streams the same file through authenticated byte ranges.
 * Only host playback controls are enabled. Revisions supersede old playback events.
 */
@OptIn(UnstableApi::class)
@Composable
fun WatchPlayer(uri: Uri, isHost: Boolean, address: RoomAddress?) {
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

    DisposableEffect(player) {
        onDispose {
            if (isHost) RoomRuntime.hostPlayback(player.currentPosition, false)
            player.release()
        }
    }
    if (isHost) {
        LaunchedEffect(player) {
            while (isActive) {
                RoomRuntime.hostPlayback(player.currentPosition, player.isPlaying)
                delay(1_000)
            }
        }
    } else {
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
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = isHost
            }
        },
        update = { it.player = player; it.useController = isHost },
        modifier = Modifier.fillMaxWidth().height(240.dp),
    )
    if (!isHost) Text("Playback follows the host. Use chat to request a pause or seek.")
}
