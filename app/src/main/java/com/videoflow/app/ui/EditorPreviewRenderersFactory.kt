package com.videoflow.app.ui

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper
import androidx.media3.exoplayer.video.VideoFrameReleaseControl
import androidx.media3.exoplayer.video.VideoRendererEventListener

/** Native preview renderer with Media3's bounded last-frame cache for paused effect changes. */
@androidx.annotation.OptIn(UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
internal class EditorPreviewRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        // Keep native codec selection and the default audio/text renderers. VideoFlow does not
        // install extension video renderers. Cache size is independent of media duration.
        out.add(EditorVideoRenderer(MediaCodecVideoRenderer.Builder(context)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setMediaCodecSelector(mediaCodecSelector)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)))
    }

    private class EditorVideoRenderer(builder: MediaCodecVideoRenderer.Builder) : MediaCodecVideoRenderer(builder) {
        // Media3 1.11 exposes this protected renderer hook but marks its return type library-only.
        // Until Media3 exposes a player-level replay option, keep this one version-pinned adapter
        // explicit and cover it with the real paused-frame UI test. No reflection or global lint
        // exclusion is used. Recheck this adapter when upgrading Media3.
        @android.annotation.SuppressLint("RestrictedApi")
        override fun createPlaybackVideoGraphWrapper(
            context: Context,
            videoFrameReleaseControl: VideoFrameReleaseControl
        ): PlaybackVideoGraphWrapper = PlaybackVideoGraphWrapper.Builder(context, videoFrameReleaseControl)
            .setEnablePlaylistMode(true)
            .setEnableReplayableCache(true)
            .experimentalSetLateThresholdToDropInputUs(DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US)
            .setClock(clock)
            .build()
    }
}
