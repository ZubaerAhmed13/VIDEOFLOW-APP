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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.videoflow.app.domain.editor.PreviewPlaybackPolicy
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
    volume: Float = 1f,
    videoEffects: List<androidx.media3.common.Effect> = emptyList()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(uri) { mutableStateOf<String?>(null) }
    var frameRendered by remember(uri) { mutableStateOf(false) }
    var redrawPending by remember(uri) { mutableStateOf(false) }
    val playerDisposed=remember(uri) { java.util.concurrent.atomic.AtomicBoolean(false) }
    val appliedEffects=remember(uri) { java.util.concurrent.atomic.AtomicReference(videoEffects) }
    val mediaUri = remember(uri) {
        if (uri.startsWith("/")) Uri.fromFile(File(uri)) else Uri.parse(uri)
    }
    // Player identity follows the actual preview source only. Playhead/UI recomposition must not
    // recreate the decoder/surface lifecycle.
    val player = remember(uri) {
        ExoPlayer.Builder(context, EditorPreviewRenderersFactory(context)).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            setVideoEffects(videoEffects)
            val frameHandler=android.os.Handler(android.os.Looper.getMainLooper())
            setVideoFrameMetadataListener { _, _, _, _ -> frameHandler.post {
                if (!playerDisposed.get()) {
                    if(redrawPending) {
                        redrawPending=false
                        setVideoEffects(androidx.media3.common.VideoFrameProcessor.REDRAW)
                    } else frameRendered=true
                }
            } }
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("VideoFlowPreview",error.errorCodeName,error)
                    playbackError = "VideoFlow could not prepare this media for playback."
                }
            })
            prepare()
        }
    }

    LaunchedEffect(player, videoEffects) {
        val current=appliedEffects.get()
        if(current===videoEffects) return@LaunchedEffect
        val ready=frameRendered
        frameRendered=false
        if(com.videoflow.app.render.effects.VisualEffectPipeline.updatePreview(current,videoEffects)) {
            // Parameter-only edits reuse shader programs and Media3's bounded replay cache.
            if (!player.playWhenReady) {
                if(ready) player.setVideoEffects(androidx.media3.common.VideoFrameProcessor.REDRAW)
                else redrawPending=true
            }
        } else {
            // Adding/removing/reordering stages registers a new stream and clears Media3's cache.
            // Reprepare this same player at its retained position to decode that paused frame again.
            redrawPending=false
            appliedEffects.set(videoEffects)
            player.stop()
            player.setVideoEffects(videoEffects)
            player.prepare()
        }
    }

    // Do not chase every high-frequency UI playhead tick with a decoder seek. While playing, only
    // correct a meaningful discontinuity/drift; while paused/scrubbing keep precise seek response.
    LaunchedEffect(player, startPositionMs, playWhenReady) {
        val requested = startPositionMs.coerceAtLeast(0L)
        if (PreviewPlaybackPolicy.shouldSeek(playWhenReady, player.currentPosition, requested)) {
            player.seekTo(requested)
        }
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
            playerDisposed.set(true)
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
                if (it.player !== player) it.player = player
                it.useController = showControls
            },
            modifier = modifier.heightIn(min = 220.dp, max = 420.dp).testTag("native-video-preview")
                .semantics { stateDescription=if(frameRendered) "Video preview ready" else "Preparing video preview" }
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
    LaunchedEffect(player, startPositionMs, playWhenReady) {
        val requested = startPositionMs.coerceAtLeast(0L)
        if (PreviewPlaybackPolicy.shouldSeek(playWhenReady, player.currentPosition, requested)) {
            player.seekTo(requested)
        }
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
