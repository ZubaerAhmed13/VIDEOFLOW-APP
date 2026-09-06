package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.ClipTransform
import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.editor.RenderPlan
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePreservationPolicyTest {
    @Test
    fun contiguousCompatibleMergeIsSmartCopyCandidateBeforeRuntimeCodecConfigCheck() {
        val plan = compatibleTwoClipPlan()
        val analysis = SourcePreservationPolicy.analyze(plan)
        assertTrue(analysis.smartCopyCandidate)
        assertTrue(analysis.requiresRuntimeSyncAndCodecConfigCheck)
    }

    @Test
    fun transformOrCropForcesRenderedPath() {
        val base = compatibleTwoClipPlan()
        val changed = base.copy(
            editorPlan = base.editorPlan.copy(
                clips = base.editorPlan.clips.mapIndexed { index, clip ->
                    if (index == 0) clip.copy(transform = ClipTransform(scaleX = 1.1f, scaleY = 1.1f)) else clip
                }
            )
        )
        val analysis = SourcePreservationPolicy.analyze(changed)
        assertFalse(analysis.smartCopyCandidate)
        assertTrue(analysis.smartCopyReasons.any { it.contains("transform", ignoreCase = true) })
    }

    @Test
    fun incompatibleAudioSampleDescriptionRejectsStaticSmartCopyEligibility() {
        val base = compatibleTwoClipPlan()
        val second = base.originalSources.getValue("a2").copy(audioSampleRate = 44_100)
        val changed = base.copy(originalSources = base.originalSources + ("a2" to second))
        val analysis = SourcePreservationPolicy.analyze(changed)
        assertFalse(analysis.smartCopyCandidate)
        assertTrue(analysis.smartCopyReasons.any { it.contains("compatible encoded source characteristics") })
    }

    @Test
    fun matchSourcePreserves4kRationalFpsCodecAndAudioAuthority() {
        val plan = compatibleTwoClipPlan()
        val settings = SourcePreservationPolicy.settingsForMode(plan, ExportSettings(), ExportMode.MATCH_SOURCE)
        assertEquals(ExportResolutionPreset.CUSTOM, settings.resolutionPreset)
        assertEquals(3840, settings.customWidth)
        assertEquals(2160, settings.customHeight)
        assertEquals(FrameRate.FPS_2997, settings.frameRate)
        assertEquals(VideoCodec.HEVC, settings.videoCodec)
        assertEquals(48_000, settings.audioSampleRate)
        assertEquals(2, settings.audioChannels)
        assertEquals(ExportMode.MATCH_SOURCE, settings.mode)
    }

    @Test
    fun mixedSourceProjectUsesProjectAuthorityInsteadOfPretendingEverySourceMatches() {
        val base = compatibleTwoClipPlan()
        val second = base.originalSources.getValue("a2").copy(width = 1920, height = 1080, videoCodecMime = VideoCodec.H264.mimeType)
        val changed = base.copy(originalSources = base.originalSources + ("a2" to second))
        val analysis = SourcePreservationPolicy.analyze(changed)
        assertFalse(analysis.profile.homogeneous)
        assertEquals(base.editorPlan.width, analysis.profile.width)
        assertEquals(base.editorPlan.height, analysis.profile.height)
        assertTrue(analysis.matchSourceWarnings.any { it.contains("multiple source formats", ignoreCase = true) })
    }

    @Test
    fun smartCopyEstimateUses64BitSafeArithmeticForLargeSources() {
        val bytes = SourcePreservationPolicy.estimateSmartCopyPayloadBytes(compatibleTwoClipPlan())
        assertTrue(requireNotNull(bytes) > Int.MAX_VALUE.toLong())
    }

    private fun compatibleTwoClipPlan(): FinalRenderPlan {
        val track = TimelineTrack("v1", "p", TrackType.VIDEO, "Video 1", 0)
        val first = TimelineClip("c1", "p", "v1", "a1", 0L, 0L, 10_000_000L)
        val second = TimelineClip("c2", "p", "v1", "a2", first.timelineEndUs, 0L, 10_000_000L)
        val render = RenderPlan(
            projectId = "p",
            width = 3840,
            height = 2160,
            frameRate = FrameRate.FPS_2997,
            tracks = listOf(track),
            clips = listOf(first, second),
            textOverlays = emptyList(),
            imageOverlays = emptyList(),
            keyframes = emptyList()
        )
        return FinalRenderPlan(
            editorPlan = render,
            originalSources = mapOf("a1" to source("a1"), "a2" to source("a2")),
            durationUs = second.timelineEndUs
        )
    }

    private fun source(id: String) = OriginalRenderSource(
        assetId = id,
        sourceUri = "content://source/$id",
        displayName = "$id.mp4",
        mimeType = "video/mp4",
        sizeBytes = 4_500_000_000L,
        durationUs = 10_000_000L,
        width = 3840,
        height = 2160,
        rotationDegrees = 0,
        frameRate = 29.97002997,
        videoCodecMime = VideoCodec.HEVC.mimeType,
        audioCodecMime = "audio/mp4a-latm",
        audioSampleRate = 48_000,
        audioChannelCount = 2,
        videoBitrate = 55_000_000,
        colorStandard = 1,
        colorTransfer = 3,
        colorRange = 2,
        hdrStaticInfoPresent = false,
        fingerprintSha256 = "sha-$id"
    )
}
