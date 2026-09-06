package com.videoflow.app.domain.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlaybackPolicyTest {
    @Test fun playingDoesNotSeekForNormalUiTickDrift() {
        assertFalse(PreviewPlaybackPolicy.shouldSeek(true, 10_000L, 10_033L))
        assertFalse(PreviewPlaybackPolicy.shouldSeek(true, 10_000L, 10_900L))
        assertTrue(PreviewPlaybackPolicy.shouldSeek(true, 10_000L, 11_050L))
    }

    @Test fun pausedScrubbingRemainsPrecise() {
        assertFalse(PreviewPlaybackPolicy.shouldSeek(false, 1_000L, 1_030L))
        assertTrue(PreviewPlaybackPolicy.shouldSeek(false, 1_000L, 1_050L))
    }
}
