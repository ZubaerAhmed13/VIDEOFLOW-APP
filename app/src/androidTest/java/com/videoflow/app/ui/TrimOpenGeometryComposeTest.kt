package com.videoflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.ui.editor.ContextualToolPanelSurface
import com.videoflow.app.ui.editor.EditorBottomToolbar
import com.videoflow.app.ui.editor.EditorSelection
import com.videoflow.app.ui.editor.EditorTool
import com.videoflow.app.ui.editor.TimelineWorkspace
import com.videoflow.app.ui.editor.TrimPanel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrimOpenGeometryComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun openingTrimKeepsSelectedVideoLayerVisibleAboveControls() {
        val projectId = "trim-geometry"
        val clip = TimelineClip("clip", projectId, "video", "asset", 0L, 0L, 8_000_000L)
        val track = TimelineTrack("video", projectId, TrackType.VIDEO, "Video 1", 0)
        val asset = MediaAsset(
            id = "asset", projectId = projectId, sourceUri = "content://trim-geometry", displayName = "Geometry video",
            mimeType = "video/mp4", sizeBytes = 1L, durationUs = 8_000_000L, width = 1920, height = 1080,
            rotationDegrees = 0, frameRate = 30.0, videoCodecMime = "video/avc", audioCodecMime = null,
            audioSampleRate = null, audioChannelCount = null, videoTrackCount = 1, audioTrackCount = 0,
            videoBitrate = null, videoProfile = null, videoLevel = null, colorStandard = null, colorTransfer = null,
            colorRange = null, hdrStaticInfoPresent = false, fingerprintSha256 = null, fingerprintAlgorithm = null,
            fingerprintStrength = FingerprintStrength.UNAVAILABLE, fingerprintSampledBytes = 0L, fingerprintNote = null,
            permissionPersisted = true, sourceStatus = SourceStatus.AVAILABLE, createdAt = 0L
        )
        val project = VideoFlowProject(projectId, "Trim geometry", 1, 0L, 0L, null, listOf(asset))
        var activeTool by mutableStateOf<EditorTool?>(null)

        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.fillMaxWidth().weight(.48f))
                        TimelineWorkspace(
                            tracks = listOf(track), clips = listOf(clip), textOverlays = emptyList(), imageOverlays = emptyList(),
                            keyframes = emptyList(), playheadUs = 1_000_000L, durationUs = 8_000_000L, pixelsPerSecond = 56f,
                            selection = EditorSelection.Clip(clip.id), mediaNames = mapOf(asset.id to asset.displayName),
                            thumbnails = emptyMap(), waveforms = emptyMap(), onZoom = {}, onSeek = {}, onSelect = {},
                            onClearSelection = {}, onMoveClip = { _, _ -> }, onTrimClipStart = { _, _ -> },
                            onTrimClipEnd = { _, _ -> }, onToggleMute = { _ -> }, onToggleVisible = { _ -> }, onToggleLock = { _ -> },
                            onTrackSettings = { _ -> }, modifier = Modifier.fillMaxWidth().weight(.52f)
                        )
                        EditorBottomToolbar(
                            selection = EditorSelection.Clip(clip.id), selectedClipMime = "video/mp4", onPanel = {},
                            onTool = { activeTool = it }, onSplit = {}, onProfessionalTool = {}
                        )
                    }
                    val trim = activeTool as? EditorTool.Trim
                    if (trim != null) {
                        ContextualToolPanelSurface {
                            TrimPanel(
                                tool = trim, clip = clip, project = project, thumbnails = emptyMap(), filmstripPaths = emptyList(),
                                waveforms = emptyMap(), playheadUs = 1_000_000L, onPreviewSeek = {},
                                onCommitTrim = { _, _ -> }, onDismiss = { activeTool = null }
                            )
                        }
                    }
                }
            }
        }

        rule.onNodeWithContentDescription("Trim").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Contextual tool panel").assertIsDisplayed()
        val row = rule.onNodeWithContentDescription("Video 1 track row").fetchSemanticsNode().boundsInRoot
        val panel = rule.onNodeWithContentDescription("Contextual tool panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Selected video row must have visible geometry after opening Trim", row.width > 0f && row.height > 0f)
        assertTrue("Trim controls cover the selected video layer: row=$row panel=$panel", row.bottom <= panel.top + 1f)
    }
}
