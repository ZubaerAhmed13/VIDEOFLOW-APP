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

    @Test fun trackSoloOverridesNonSoloAndMuteRemovesTrack() {
        val tracks = listOf(
            TimelineTrack("a", "project", TrackType.AUDIO, "A1", 0),
            TimelineTrack("b", "project", TrackType.AUDIO, "A2", 1, solo = true),
            TimelineTrack("c", "project", TrackType.VIDEO, "V1", 2, solo = true, muted = true)
        )
        assertEquals(listOf("b"), TimelineEngine.effectiveAudioTracks(tracks).map { it.id })
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

    @Test fun renderPlanIsDeterministic() {
        val settings = ProjectSettings("project", createdAt = 1, updatedAt = 1)
        val track = TimelineTrack("v1", "project", TrackType.VIDEO, "V1", 0)
        val state = TimelineState("project", listOf(track), listOf(clip()))
        assertEquals(PlanBuilder.render(settings, state), PlanBuilder.render(settings, state))
    }
}
