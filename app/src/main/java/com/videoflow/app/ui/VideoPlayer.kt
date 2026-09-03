package com.videoflow.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun NativeVideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    showControls: Boolean = false,
    playWhenReady: Boolean = false,
    speed: Float = 1f,
    volume: Float = 1f
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(uri) { mutableStateOf<String?>(null) }
    val mediaUri = remember(uri) {
        if (uri.startsWith("/")) Uri.fromFile(File(uri)) else Uri.parse(uri)
    }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = "VideoFlow could not prepare this media for playback."
                }
            })
            prepare()
        }
    }

    LaunchedEffect(player, startPositionMs) {
        val delta = kotlin.math.abs(player.currentPosition - startPositionMs.coerceAtLeast(0L))
        if (!playWhenReady || delta > 250L) player.seekTo(startPositionMs.coerceAtLeast(0L))
    }
    LaunchedEffect(player, playWhenReady, speed, volume) {
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f))
        player.volume = volume.coerceIn(0f, 1f)
        player.playWhenReady = playWhenReady
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_DESTROY -> player.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Column {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = showControls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    contentDescription = "Native timeline video preview"
                }
            },
            update = {
                it.player = player
                it.useController = showControls
            },
            modifier = modifier.heightIn(min = 220.dp, max = 420.dp)
        )
        playbackError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun NativeAudioPreview(
    uri: String,
    startPositionMs: Long,
    playWhenReady: Boolean,
    speed: Float,
    volume: Float
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaUri = remember(uri) { if (uri.startsWith("/")) Uri.fromFile(File(uri)) else Uri.parse(uri) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
        }
    }
    LaunchedEffect(player, startPositionMs) {
        val delta = kotlin.math.abs(player.currentPosition - startPositionMs.coerceAtLeast(0L))
        if (!playWhenReady || delta > 250L) player.seekTo(startPositionMs.coerceAtLeast(0L))
    }
    LaunchedEffect(player, playWhenReady, speed, volume) {
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f))
        player.volume = volume.coerceIn(0f, 1f)
        player.playWhenReady = playWhenReady
    }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
}
