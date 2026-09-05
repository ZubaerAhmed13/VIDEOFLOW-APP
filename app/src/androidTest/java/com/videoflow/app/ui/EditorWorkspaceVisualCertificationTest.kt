package com.videoflow.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.videoflow.app.MainActivity
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.ui.editor.EditorSelection
import com.videoflow.app.ui.editor.TimelineWorkspace
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorWorkspaceVisualCertificationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun portraitLandscapeLargeFontAndTouchTargetsRemainUsable() {
        try {
            device.executeShellCommand("settings put system font_scale 1.0")
            device.setOrientationNatural()
            openEmptyEditor()

            assertCoreEditorVisible()
            assertPrimaryTouchTargets()
            capture("editor-portrait.png")

            device.setOrientationLeft()
            device.waitForIdle()
            rule.waitForIdle()
            waitForEditor()
            assertCoreEditorVisible()
            assertPrimaryTouchTargets()
            capture("editor-landscape.png")

            device.executeShellCommand("settings put system font_scale 1.5")
            rule.activityRule.scenario.recreate()
            device.waitForIdle()
            rule.waitForIdle()
            waitForEditor()
            assertCoreEditorVisible()
            assertPrimaryTouchTargets()
            capture("editor-font-scale-150.png")
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
            device.unfreezeRotation()
        }
    }

    private fun openEmptyEditor() {
        rule.onNodeWithContentDescription("New Project").performClick()
        rule.onNodeWithText("Create").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Open Editor").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Open Editor").performClick()
        waitForEditor()
    }

    private fun waitForEditor() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Start your video").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithContentDescription("Media").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertCoreEditorVisible() {
        rule.onAllNodesWithText("Start your video").fetchSemanticsNodes().let {
            assertTrue("Preview/timeline empty-state should remain visible", it.isNotEmpty())
        }
        listOf("Media", "Audio", "Text", "Overlay", "Canvas", "More").forEach { label ->
            rule.onNodeWithContentDescription(label).fetchSemanticsNode()
        }
        rule.onNodeWithContentDescription("Jump to start").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Play").fetchSemanticsNode()
        rule.onNodeWithText("Export").fetchSemanticsNode()
    }

    private fun assertPrimaryTouchTargets() {
        val density = rule.activity.resources.displayMetrics.density
        val minimumPx = 48f * density
        listOf("Media", "Audio", "Text", "Overlay", "Canvas", "More").forEach { label ->
            val bounds = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue("$label width should be at least 48dp", bounds.width >= minimumPx - 1f)
            assertTrue("$label height should be at least 48dp", bounds.height >= minimumPx - 1f)
        }
    }

    private fun capture(name: String) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(targetContext.getExternalFilesDir(null), "ui-step1-visual")
        assertTrue(directory.exists() || directory.mkdirs())
        assertTrue("Screenshot $name should be captured", device.takeScreenshot(File(directory, name)))
    }
}

@RunWith(AndroidJUnit4::class)
class LongTimelineWorkspaceSmokeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun longTimelineKeepsTrackControlsAndZoomInteractive() {
        val tracks = listOf(
            TimelineTrack("video-1", "project-1", TrackType.VIDEO, "Video 1", 0),
            TimelineTrack("audio-1", "project-1", TrackType.AUDIO, "Audio 1", 1),
            TimelineTrack("overlay-1", "project-1", TrackType.OVERLAY, "Overlay 1", 2)
        )
        val clips = listOf(
            TimelineClip("clip-a", "project-1", "video-1", "asset-a", 0L, 0L, 30_000_000L),
            TimelineClip("clip-b", "project-1", "video-1", "asset-b", 90_000_000L, 0L, 30_000_000L),
            TimelineClip("clip-c", "project-1", "audio-1", "asset-c", 150_000_000L, 0L, 30_000_000L)
        )
        var zoom = 28f

        rule.setContent {
            MaterialTheme {
                TimelineWorkspace(
                    tracks = tracks,
                    clips = clips,
                    textOverlays = emptyList(),
                    imageOverlays = emptyList(),
                    keyframes = emptyList(),
                    playheadUs = 95_000_000L,
                    durationUs = 180_000_000L,
                    pixelsPerSecond = zoom,
                    selection = EditorSelection.None,
                    mediaNames = mapOf("asset-a" to "Opening", "asset-b" to "Middle", "asset-c" to "Music"),
                    thumbnails = emptyMap(),
                    waveforms = mapOf("asset-c" to floatArrayOf(0.1f, 0.8f, 0.3f, 0.6f)),
                    onZoom = { zoom = it },
                    onSeek = {},
                    onSelect = {},
                    onClearSelection = {},
                    onMoveClip = { _, _ -> },
                    onToggleMute = {},
                    onToggleVisible = {},
                    onToggleLock = {},
                    onTrackSettings = {}
                )
            }
        }

        rule.onNodeWithContentDescription("Zoom out timeline").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Zoom in timeline").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Open Video 1 settings").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Open Audio 1 settings").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Hide Video 1").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Mute Audio 1").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Lock Video 1").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Video clip Opening, 00:30").fetchSemanticsNode()
    }
}
