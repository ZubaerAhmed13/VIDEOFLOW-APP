package com.videoflow.app.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEngineTest {
    private fun clip(speed: Double = 1.0) = TimelineClip(
        id = "clip-a",
        projectId = "project",
        trackId = "v1",
        assetId = "asset",
        timelineStartUs = 1_000_000,
        sourceStartUs = 2_000_000,
        sourceEndUs = 12_000_000,
        speed = speed
    )

    @Test fun supportedFrameRatesPreserveExactRationals() {
        assertEquals(FrameRate(24, 1), FrameRate.FPS_24)
        assertEquals(FrameRate(25, 1), FrameRate.FPS_25)
        assertEquals(FrameRate(30_000, 1_001), FrameRate.FPS_2997)
        assertEquals(FrameRate(30, 1), FrameRate.FPS_30)
        assertEquals(FrameRate(60_000, 1_001), FrameRate.FPS_5994)
        assertEquals(FrameRate(60, 1), FrameRate.FPS_60)
    }

    @Test fun splitAtNormalSpeedMapsSourceCorrectly() {
        val (left, right) = TimelineEngine.splitClip(clip(), 5_000_000, "clip-b")
        assertEquals(6_000_000, left.sourceEndUs)
        assertEquals(6_000_000, right.sourceStartUs)
        assertEquals(5_000_000, right.timelineStartUs)
        assertEquals("asset", right.assetId)
    }

    @Test fun splitAtDoubleSpeedMapsSourceCorrectly() {
        val fast = clip(speed = 2.0)
        val (left, right) = TimelineEngine.splitClip(fast, 3_000_000, "clip-b")
        assertEquals(6_000_000, left.sourceEndUs)
        assertEquals(6_000_000, right.sourceStartUs)
    }

    @Test fun splitAtHalfSpeedMapsSourceCorrectly() {
        val slow = clip(speed = 0.5)
        val (left, right) = TimelineEngine.splitClip(slow, 5_000_000, "clip-b")
        assertEquals(4_000_000, left.sourceEndUs)
        assertEquals(4_000_000, right.sourceStartUs)
    }

    @Test fun trimStartMovesTimelineStartWithoutMovingEndUnexpectedly() {
        val original = clip()
        val trimmed = TimelineEngine.trimStart(original, 4_000_000)
        assertEquals(3_000_000, trimmed.timelineStartUs)
        assertEquals(4_000_000, trimmed.sourceStartUs)
        assertEquals(original.timelineEndUs, trimmed.timelineEndUs)
    }

    @Test fun trimAndSplitClampLongFadesBeforeConstructingShorterClips() {
        val faded = clip().copy(fadeInUs = 8_000_000, fadeOutUs = 9_000_000)
        val trimmed = TimelineEngine.trimEnd(faded, 6_000_000, 20_000_000)
        assertEquals(4_000_000, trimmed.timelineDurationUs)
        assertEquals(4_000_000, trimmed.fadeInUs)
        assertEquals(4_000_000, trimmed.fadeOutUs)

        val (left, right) = TimelineEngine.splitClip(faded, 5_000_000, "clip-b")
        assertTrue(left.fadeInUs <= left.timelineDurationUs)
        assertTrue(left.fadeOutUs <= left.timelineDurationUs)
        assertTrue(right.fadeInUs <= right.timelineDurationUs)
        assertTrue(right.fadeOutUs <= right.timelineDurationUs)
    }

    @Test fun duplicateKeepsSourceIdentityAndPlacesCopyAfterOriginal() {
        val original = clip()
        val duplicate = TimelineEngine.duplicateClip(original, "copy")
        assertEquals("asset", duplicate.assetId)
        assertEquals(original.sourceStartUs, duplicate.sourceStartUs)
        assertEquals(original.sourceEndUs, duplicate.sourceEndUs)
        assertEquals(original.timelineEndUs, duplicate.timelineStartUs)
    }

    @Test fun trackCompatibilityIsTypeSpecific() {
        val video = TimelineTrack("v", "project", TrackType.VIDEO, "V1", 0)
        val audio = TimelineTrack("a", "project", TrackType.AUDIO, "A1", 1)
        val overlay = TimelineTrack("o", "project", TrackType.OVERLAY, "O1", 2)
        assertTrue(TimelineEngine.compatible(video, "video/avc"))
        assertTrue(TimelineEngine.compatible(audio, "audio/aac"))
        assertTrue(TimelineEngine.compatible(overlay, "image/png"))
        assertFalse(TimelineEngine.compatible(video, "audio/aac"))
    }

    @Test fun snapUsesDisplayScaleWithoutRoundingStoredTime() {
        val candidate = 1_005_000L
        assertEquals(1_000_000L, TimelineEngine.snapTime(candidate, 100.0, 2.0, listOf(1_000_000L)))
        assertEquals(candidate, TimelineEngine.snapTime(candidate, 1000.0, 2.0, listOf(1_000_000L)))
    }

    @Test fun sixHourTimelineUsesLongSafely() {
        val longClip = TimelineClip(
            id = "long",
            projectId = "project",
            trackId = "v1",
            assetId = "asset",
            timelineStartUs = 5L * 60 * 60 * 1_000_000,
            sourceStartUs = 0,
            sourceEndUs = 60L * 60 * 1_000_000
        )
        assertEquals(6L * 60 * 60 * 1_000_000, longClip.timelineEndUs)
    }

    @Test fun hundredClipTwoHourProjectRemainsDeterministic() {
        val tracks = (0 until 5).map { index -> TimelineTrack("t$index", "project", TrackType.VIDEO, "V${index + 1}", index) }
        val clips = (0 until 100).map { index ->
            TimelineClip(
                id = "c$index",
                projectId = "project",
                trackId = tracks[index % tracks.size].id,
                assetId = "asset",
                timelineStartUs = index * 90_000_000L,
                sourceStartUs = 0,
                sourceEndUs = 30_000_000L
            )
        }
        val state = TimelineState("project", tracks, clips)
        assertTrue(state.durationUs > 2L * 60 * 60 * 1_000_000)
        assertTrue(ProjectValidator.validate(state).isEmpty())
    }

    @Test fun trackSoloOverridesNonSoloAndMuteRemovesTrack() {
        val tracks = listOf(
            TimelineTrack("a", "project", TrackType.AUDIO, "A1", 0),
            TimelineTrack("b", "project", TrackType.AUDIO, "A2", 1, solo = true),
            TimelineTrack("c", "project", TrackType.VIDEO, "V1", 2, solo = true, muted = true)
        )
        assertEquals(listOf("b"), TimelineEngine.effectiveAudioTracks(tracks).map { it.id })
    }

    @Test fun audioGainAndFadesAreDeterministic() {
        assertEquals(1f, AudioMath.dbToLinear(0f), 0.0001f)
        assertEquals(0.501187f, AudioMath.dbToLinear(-6f), 0.001f)
        assertEquals(0f, AudioMath.fadeGain(0, 10_000_000, 2_000_000, 2_000_000), 0.0001f)
        assertEquals(0.5f, AudioMath.fadeGain(1_000_000, 10_000_000, 2_000_000, 2_000_000), 0.0001f)
        assertEquals(1f, AudioMath.fadeGain(5_000_000, 10_000_000, 2_000_000, 2_000_000), 0.0001f)
        assertEquals(0.5f, AudioMath.fadeGain(9_000_000, 10_000_000, 2_000_000, 2_000_000), 0.0001f)
    }

    @Test fun keyframeLinearAndHoldAreDeterministic() {
        val linear = listOf(
            Keyframe("k1", "c", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 0, 0f),
            Keyframe("k2", "c", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 1_000_000, 1f)
        )
        assertEquals(0.5f, KeyframeEvaluator.evaluate(1f, 500_000, linear), 0.0001f)

        val hold = linear.first().copy(interpolation = KeyframeInterpolation.HOLD)
        assertEquals(0f, KeyframeEvaluator.evaluate(1f, 500_000, listOf(hold, linear.last())), 0.0001f)
    }

    @Test fun keyframesMoveWithClipBecauseTimesAreLocal() {
        val keyframe = Keyframe("k", "clip-a", KeyframeOwnerType.CLIP, KeyframeProperty.POSITION_X, 2_000_000, 0.8f)
        val moved = TimelineEngine.moveClip(clip(), 10_000_000)
        assertEquals(2_000_000, keyframe.timeUs)
        assertEquals(10_000_000, moved.timelineStartUs)
    }

    @Test fun splitKeyframesPreservesBoundaryAnimation() {
        val frames = listOf(
            Keyframe("k1", "clip-a", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 1_000_000, 0.2f),
            Keyframe("k2", "clip-a", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 3_000_000, 0.6f),
            Keyframe("k3", "clip-a", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 5_000_000, 1f)
        )
        val (left, right) = TimelineEngine.splitKeyframes(frames, "clip-a", "clip-b", 3_000_000) { "r-${it.id}" }
        assertEquals(listOf(1_000_000L, 3_000_000L), left.map { it.timeUs })
        assertEquals(listOf(0L, 2_000_000L), right.map { it.timeUs })
        assertTrue(right.all { it.ownerId == "clip-b" })
    }

    @Test fun previewPlanUsesReadyProxyWithoutRequiringOriginalOnline() {
        val settings = ProjectSettings("project", createdAt = 1, updatedAt = 1)
        val track = TimelineTrack("v1", "project", TrackType.VIDEO, "V1", 0)
        val state = TimelineState("project", listOf(track), listOf(clip()))
        val proxy = ProxyMedia(
            id = "p",
            assetId = "asset",
            path = "/proxy.mp4",
            width = 1280,
            height = 720,
            codecMime = "video/avc",
            sourceFingerprint = "fingerprint",
            status = ProxyStatus.READY,
            quality = ProxyQuality.BALANCED,
            createdAt = 1,
            sizeBytes = 1_000_000
        )
        val plan = PlanBuilder.preview(settings, state, emptyMap(), mapOf("asset" to proxy), preferProxy = true)
        assertEquals("/proxy.mp4", plan.clips.single().source)
        assertTrue(plan.clips.single().usingProxy)
    }

    @Test fun projectValidatorRejectsMissingTrackAndKeyframeOwner() {
        val state = TimelineState(
            projectId = "project",
            tracks = emptyList(),
            clips = listOf(clip()),
            keyframes = listOf(Keyframe("k", "missing", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 0, 1f))
        )
        val codes = ProjectValidator.validate(state).map { it.code }.toSet()
        assertTrue("MISSING_TRACK" in codes)
        assertTrue("MISSING_KEYFRAME_OWNER" in codes)
    }

    @Test fun editHistorySupportsUndoRedoAndInvalidatesRedo() {
        data class SetValue(val from: Int, val to: Int) : EditCommand<Int> {
            override fun apply(state: Int) = to
            override fun revert(state: Int) = from
        }
        val history = EditHistory<Int>(3)
        var state = history.execute(0, SetValue(0, 1))
        state = history.execute(state, SetValue(1, 2))
        state = history.undo(state)
        assertEquals(1, state)
        assertTrue(history.canRedo())
        state = history.execute(state, SetValue(1, 7))
        assertEquals(7, state)
        assertFalse(history.canRedo())
    }

    @Test fun previewAndRenderPlansAreDeterministic() {
        val settings = ProjectSettings("project", createdAt = 1, updatedAt = 1)
        val track = TimelineTrack("v1", "project", TrackType.VIDEO, "V1", 0)
        val state = TimelineState("project", listOf(track), listOf(clip()))
        assertEquals(PlanBuilder.render(settings, state), PlanBuilder.render(settings, state))
        val originals = mapOf("asset" to "content://asset")
        assertEquals(
            PlanBuilder.preview(settings, state, originals, emptyMap()),
            PlanBuilder.preview(settings, state, originals, emptyMap())
        )
    }
}
