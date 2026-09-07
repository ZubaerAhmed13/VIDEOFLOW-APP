package com.videoflow.app.ai.watermark

import com.videoflow.app.domain.ai.AiModelCatalog
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AiPreviewIdentityTest {
    private val asset = MediaAsset(
        id = "asset",
        projectId = "project",
        sourceUri = "content://source/video",
        displayName = "source.mp4",
        mimeType = "video/mp4",
        sizeBytes = 10_000_000L,
        durationUs = 10_000_000L,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        frameRate = 30.0,
        videoCodecMime = "video/avc",
        audioCodecMime = "audio/mp4a-latm",
        audioSampleRate = 48_000,
        audioChannelCount = 2,
        videoTrackCount = 1,
        audioTrackCount = 1,
        videoBitrate = 8_000_000,
        videoProfile = null,
        videoLevel = null,
        colorStandard = null,
        colorTransfer = null,
        colorRange = null,
        hdrStaticInfoPresent = false,
        fingerprintSha256 = "fingerprint-a",
        fingerprintAlgorithm = "VideoFlowSampleSHA256-v1",
        fingerprintStrength = FingerprintStrength.STRONG_THREE_REGION,
        fingerprintSampledBytes = 12_000_000L,
        fingerprintNote = null,
        permissionPersisted = true,
        sourceStatus = SourceStatus.AVAILABLE,
        createdAt = 1L
    )
    private val project = VideoFlowProject("project", "Test", 1, 1L, 2L, 2L, listOf(asset))
    private val clip = TimelineClip("clip", "project", "video", "asset", 0L, 0L, 10_000_000L)
    private val base = AiWatermarkEffect(
        id = "fx",
        projectId = "project",
        clipId = "clip",
        clipLocalStartUs = 2_000_000L,
        clipLocalEndUs = 4_000_000L,
        roi = NormalizedRoi(.1f, .1f, .3f, .3f),
        modelId = AiModelCatalog.FINAL_512.id
    )

    @Test
    fun `same source clip and AI state produces deterministic key`() {
        val first = AiPreviewIdentity.stateKey(project, clip, asset, null, listOf(base))
        val second = AiPreviewIdentity.stateKey(project, clip, asset, null, listOf(base.copy()))
        assertEquals(first, second)
    }

    @Test
    fun `ROI range tracking quality and enabled changes invalidate preview identity`() {
        val original = AiPreviewIdentity.stateKey(project, clip, asset, null, listOf(base))
        val variants = listOf(
            base.copy(roi = NormalizedRoi(.2f, .1f, .4f, .3f)),
            base.copy(clipLocalStartUs = 2_100_000L),
            base.copy(motionAnchors = listOf(RoiMotionAnchor(2_000_000L, .2f, .2f), RoiMotionAnchor(4_000_000L, .7f, .6f))),
            base.copy(contextPaddingPx = 64),
            base.copy(featherPx = 20),
            base.copy(temporalStability = .25f),
            base.copy(enabled = false)
        )
        variants.forEach { variant ->
            assertNotEquals(original, AiPreviewIdentity.stateKey(project, clip, asset, null, listOf(variant)))
        }
    }

    @Test
    fun `source fingerprint change invalidates preview identity`() {
        val original = AiPreviewIdentity.stateKey(project, clip, asset, null, listOf(base))
        val changedAsset = asset.copy(fingerprintSha256 = "fingerprint-b")
        val changedProject = project.copy(mediaAssets = listOf(changedAsset))
        assertNotEquals(original, AiPreviewIdentity.stateKey(changedProject, clip, changedAsset, null, listOf(base)))
    }
}
