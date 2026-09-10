package com.videoflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.videoflow.app.domain.editor.TimelineTrack
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.ui.editor.EditorBottomToolbar
import com.videoflow.app.ui.editor.EditorSelection
import com.videoflow.app.ui.editor.EditorTopBar
import com.videoflow.app.ui.editor.FocusedEditorWorkspace
import com.videoflow.app.ui.editor.FocusedToolScaffold
import com.videoflow.app.ui.editor.MainEditorPortraitScaffold
import com.videoflow.app.ui.editor.TimelineWorkspace
import com.videoflow.app.ui.editor.registerFocusedToolActions
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Captures the required Step-1 shell evidence with production shell/timeline composables. */
@RunWith(AndroidJUnit4::class)
class UXStep1VisualEvidenceTest {
    @get:Rule
    val rule = createComposeRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun capturePortraitTrackAndFocusedWorkspaceEvidence() {
        val trackCount = mutableIntStateOf(3)
        val focused = mutableStateOf(false)
        try {
            device.executeShellCommand("wm density 160")
            device.executeShellCommand("wm size 393x852")
            device.waitForIdle()

            rule.setContent {
                val tracks = (1..trackCount.intValue).map { index ->
                    TimelineTrack(
                        id = "ux-track-$index",
                        projectId = "ux-step1-evidence",
                        type = when (index) {
                            1 -> TrackType.VIDEO
                            2 -> TrackType.AUDIO
                            else -> TrackType.OVERLAY
                        },
                        name = when (index) {
                            1 -> "Video 1"
                            2 -> "Audio 1"
                            3 -> "Overlay 1"
                            else -> "Overlay ${index - 2}"
                        },
                        orderIndex = index - 1
                    )
                }

                MaterialTheme {
                    Scaffold(
                        topBar = {
                            EditorTopBar(
                                projectName = "UX Step 1 Evidence",
                                saving = false,
                                canUndo = true,
                                canRedo = true,
                                onBack = {},
                                onUndo = {},
                                onRedo = {},
                                onExport = {},
                                showExport = !focused.value
                            )
                        },
                        bottomBar = {
                            if (!focused.value) {
                                EditorBottomToolbar(
                                    selection = EditorSelection.None,
                                    selectedClipMime = null,
                                    onPanel = {},
                                    onTool = {},
                                    onSplit = {},
                                    onProfessionalTool = {}
                                )
                            }
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            if (focused.value) {
                                FocusedEditorWorkspace(
                                    preview = { EvidencePreview() },
                                    transport = { EvidenceTransport() },
                                    focusedTool = {
                                        FocusedToolScaffold(toolKey = "evidence", fallbackCancel = {}) {
                                            registerFocusedToolActions(onCancel = {}, onReset = {}, onDone = {})
                                            Text(
                                                "Focused controls\nPrimary tool controls stay in this region.\nAdvanced options may scroll here.",
                                                modifier = Modifier.padding(20.dp)
                                            )
                                        }
                                    }
                                )
                            } else {
                                MainEditorPortraitScaffold(
                                    preview = { EvidencePreview() },
                                    transport = { EvidenceTransport() },
                                    timeline = { rowHeight ->
                                        TimelineWorkspace(
                                            tracks = tracks,
                                            clips = emptyList(),
                                            textOverlays = emptyList(),
                                            imageOverlays = emptyList(),
                                            keyframes = emptyList(),
                                            playheadUs = 0L,
                                            durationUs = 30_000_000L,
                                            pixelsPerSecond = 72f,
                                            selection = EditorSelection.None,
                                            mediaNames = emptyMap(),
                                            thumbnails = emptyMap(),
                                            waveforms = emptyMap(),
                                            onZoom = {},
                                            onSeek = {},
                                            onSelect = {},
                                            onClearSelection = {},
                                            onMoveClip = { _, _ -> },
                                            onToggleMute = {},
                                            onToggleVisible = {},
                                            onToggleLock = {},
                                            onTrackSettings = {},
                                            trackRowHeight = rowHeight,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            rule.waitForIdle()
            capture("A-main-393x852-three-tracks.png")

            rule.runOnIdle { trackCount.intValue = 6 }
            rule.waitForIdle()
            capture("B-main-393x852-six-tracks-before-scroll.png")

            rule.onNodeWithContentDescription("Open Overlay 4 settings").performScrollTo()
            rule.waitForIdle()
            capture("C-main-393x852-tracks-4-to-6.png")

            rule.runOnIdle { focused.value = true }
            rule.waitForIdle()
            capture("D-focused-393x852-preview-controls-actions.png")

            rule.runOnIdle { focused.value = false; trackCount.intValue = 3 }
            device.executeShellCommand("wm size 360x800")
            device.waitForIdle()
            rule.waitForIdle()
            capture("E-main-360x800-compact.png")

            device.executeShellCommand("wm size 412x915")
            device.waitForIdle()
            rule.waitForIdle()
            capture("F-main-412x915-tall.png")
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
        }
    }

    private fun capture(name: String) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(targetContext.filesDir, "ux-step1-evidence")
        assertTrue(directory.exists() || directory.mkdirs())
        assertTrue("Screenshot $name should be captured", device.takeScreenshot(File(directory, name)))
    }
}

@androidx.compose.runtime.Composable
private fun EvidencePreview() {
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Evidence video preview" },
        contentAlignment = Alignment.Center
    ) {
        Text("VIDEO PREVIEW")
    }
}

@androidx.compose.runtime.Composable
private fun EvidenceTransport() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Evidence transport" },
        contentAlignment = Alignment.Center
    ) {
        Text("00:00    ▶    00:30")
    }
}
