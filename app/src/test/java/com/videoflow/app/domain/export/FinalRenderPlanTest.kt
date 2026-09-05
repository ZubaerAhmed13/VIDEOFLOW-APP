package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.ClipTransform
import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeInterpolation
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProjectSettings
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineState
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.editor.PlanBuilder
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalRenderPlanTest {
    private val settings = ProjectSettings("p", 1920, 1080, FrameRate.FPS_2997, createdAt = 1, updatedAt = 1)
    private val videoTrack = TimelineTrack("v1", "p", TrackType.VIDEO, "Video 1", 0)
    private val clip = TimelineClip(
        id = "clip",
        projectId = "p",
        trackId = "v1",
        assetId = "asset",
        timelineStartUs = 2_000_000,
        sourceStartUs = 10_000_000,
        sourceEndUs = 20_000_000,
        speed = 2.0,
        transform = ClipTransform(x = 0.25f, y = 0.75f)
    )

    @Test
    fun finalPlanAlwaysCarriesOriginalUriNotProxyPath() {
        val editorPlan = PlanBuilder.render(settings, TimelineState("p", listOf(videoTrack), listOf(clip)))
        val result = FinalRenderPlanCompiler.compile(editorPlan, listOf(asset()))
        assertTrue(result.ready)
        assertEquals("content://original/video", result.plan!!.originalSources.getValue("asset").sourceUri)
        assertFalse(result.plan!!.originalSources.values.any { it.sourceUri.contains("proxies") })
    }

    @Test
    fun changedOriginalBlocksFinalRender() {
        val editorPlan = PlanBuilder.render(settings, TimelineState("p", listOf(videoTrack), listOf(clip)))
        val result = FinalRenderPlanCompiler.compile(editorPlan, listOf(asset(status = SourceStatus.CHANGED)))
        assertFalse(result.ready)
        assertTrue(result.problems.any { it.code == ExportFailureCode.SOURCE_CHANGED })
    }

    @Test
    fun lostPersistedPermissionBlocksBackgroundRender() {
        val editorPlan = PlanBuilder.render(settings, TimelineState("p", listOf(videoTrack), listOf(clip)))
        val result = FinalRenderPlanCompiler.compile(editorPlan, listOf(asset(permissionPersisted = false)))
        assertFalse(result.ready)
        assertTrue(result.problems.any { it.code == ExportFailureCode.PERMISSION_LOST })
    }

    @Test
    fun evaluatorMapsTimelineToOriginalSourceTimeWithSpeed() {
        val editorPlan = PlanBuilder.render(settings, TimelineState("p", listOf(videoTrack), listOf(clip)))
        val finalPlan = FinalRenderPlanCompiler.compile(editorPlan, listOf(asset())).plan!!
        val state = FinalRenderEvaluator.evaluate(finalPlan, 3_000_000)
        assertEquals(1, state.video.size)
        assertEquals(12_000_000L, state.video.single().sourceTimeUs)
    }

    @Test
    fun evaluatorUsesSharedLinearKeyframeSystem() {
        val frames = listOf(
            Keyframe("k0", "clip", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 0, 0f, KeyframeInterpolation.LINEAR),
            Keyframe("k1", "clip", KeyframeOwnerType.CLIP, KeyframeProperty.OPACITY, 2_000_000, 1f, KeyframeInterpolation.LINEAR)
        )
        val editorPlan = PlanBuilder.render(settings, TimelineState("p", listOf(videoTrack), listOf(clip), keyframes = frames))
        val finalPlan = FinalRenderPlanCompiler.compile(editorPlan, listOf(asset())).plan!!
        val evaluated = FinalRenderEvaluator.evaluate(finalPlan, clip.timelineStartUs + 1_000_000)
        assertEquals(0.5f, evaluated.video.single().transform.opacity, 0.0001f)
    }

    @Test
    fun compilationIsDeterministicRegardlessOfAssetInputOrder() {
        val clip2 = clip.copy(id = "clip2", assetId = "asset2", timelineStartUs = clip.timelineEndUs)
        val editorPlan = PlanBuilder.render(settings, TimelineState("p", listOf(videoTrack), listOf(clip, clip2)))
        val a = asset()
        val b = asset().copy(id = "asset2", sourceUri = "content://original/two", displayName = "two")
        val first = FinalRenderPlanCompiler.compile(editorPlan, listOf(b, a)).plan
        val second = FinalRenderPlanCompiler.compile(editorPlan, listOf(a, b)).plan
        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(listOf("asset", "asset2"), first!!.originalSources.keys.toList())
    }

    private fun asset(
        status: SourceStatus = SourceStatus.AVAILABLE,
        permissionPersisted: Boolean = true
    ) = MediaAsset(
        id = "asset",
        projectId = "p",
        sourceUri = "content://original/video",
        displayName = "video.mp4",
        mimeType = "video/mp4",
        sizeBytes = 4_500_000_000,
        durationUs = 30_000_000,
        width = 3840,
        height = 2160,
        rotationDegrees = 0,
        frameRate = 29.97002997,
        videoCodecMime = "video/hevc",
        audioCodecMime = "audio/mp4a-latm",
        audioSampleRate = 48_000,
        audioChannelCount = 2,
        videoTrackCount = 1,
        audioTrackCount = 1,
        videoBitrate = 55_000_000,
        videoProfile = null,
        videoLevel = null,
        colorStandard = null,
        colorTransfer = null,
        colorRange = null,
        hdrStaticInfoPresent = false,
        fingerprintSha256 = "abc",
        fingerprintAlgorithm = "VideoFlowSampleSHA256-v1",
        fingerprintStrength = FingerprintStrength.STRONG_THREE_REGION,
        fingerprintSampledBytes = 12L * 1024 * 1024,
        fingerprintNote = null,
        permissionPersisted = permissionPersisted,
        sourceStatus = status,
        createdAt = 1
    )
}
