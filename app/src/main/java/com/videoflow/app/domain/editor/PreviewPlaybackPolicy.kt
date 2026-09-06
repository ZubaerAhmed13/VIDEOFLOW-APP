package com.videoflow.app.domain.editor

import kotlin.math.abs

/** Keeps high-frequency UI playhead updates from repeatedly flushing the decoder. */
object PreviewPlaybackPolicy {
    const val PAUSED_SEEK_THRESHOLD_MS = 40L
    const val PLAYING_DRIFT_THRESHOLD_MS = 1_000L

    fun shouldSeek(playWhenReady: Boolean, currentPositionMs: Long, requestedPositionMs: Long): Boolean {
        val requested = requestedPositionMs.coerceAtLeast(0L)
        val threshold = if (playWhenReady) PLAYING_DRIFT_THRESHOLD_MS else PAUSED_SEEK_THRESHOLD_MS
        return abs(currentPositionMs - requested) > threshold
    }
}
