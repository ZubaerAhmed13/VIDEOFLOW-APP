package com.videoflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.ui.editor.EditorBottomToolbar
import com.videoflow.app.ui.editor.EditorSelection
import com.videoflow.app.ui.editor.FocusedEditorWorkspace
import com.videoflow.app.ui.editor.FocusedToolScaffold
import com.videoflow.app.ui.editor.MainEditorPortraitScaffold
import com.videoflow.app.ui.editor.editorShellMetrics
import com.videoflow.app.ui.editor.registerFocusedToolActions
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic geometry certification for UX Reconstruction Step 1.
 * These tests exercise the production shell composables, not duplicate UI fixtures.
 */
@RunWith(AndroidJUnit4::class)
class UXStep1EditorShellComposeTest {
    @get:Rule
    val rule = createComposeRule()

    private val density: Float
        get() = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    @Test
    fun EDITOR_SHELL_PORTRAIT_GEOMETRY_threeTracksAndPreviewStayStable() {
        val trackCount = mutableIntStateOf(3)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(760.dp)) {
                        MainEditorPortraitScaffold(
                            preview = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .testTag("cert-preview-content")
                                        .semantics { contentDescription = "Certification video preview" }
                                )
                            },
                            transport = {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .semantics { contentDescription = "Certification transport" }
                                )
                            },
                            timeline = { rowHeight ->
                                Column(Modifier.fillMaxSize().testTag("cert-timeline-content")) {
                                    // Same compact navigation + ruler budget used by production metrics.
                                    Box(Modifier.fillMaxWidth().height(84.dp))
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                            .testTag("cert-track-scroll")
                                    ) {
                                        repeat(trackCount.intValue) { index ->
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(rowHeight)
                                                    .semantics {
                                                        contentDescription = "Certification Track ${index + 1}"
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        val previewBefore = rule.onNodeWithTag("editor-preview-slot").fetchSemanticsNode().boundsInRoot
        val timelineBefore = rule.onNodeWithTag("editor-timeline-slot").fetchSemanticsNode().boundsInRoot
        val metrics = editorShellMetrics(760.dp)
        val expectedTimelinePx = metrics.timelineViewportHeight.value * density
        val minimumPreviewPx = metrics.minimumPreviewHeight.value * density

        assertTrue("Preview must meet the professional minimum", previewBefore.height >= minimumPreviewPx - 2f)
        assertTrue("Timeline must use the bounded three-row geometry", abs(timelineBefore.height - expectedTimelinePx) <= 3f)

        for (index in 1..3) {
            val row = rule.onNodeWithContentDescription("Certification Track $index").fetchSemanticsNode().boundsInRoot
            assertTrue("Track $index top must be visible", row.top >= timelineBefore.top - 2f)
            assertTrue("Track $index bottom must be visible", row.bottom <= timelineBefore.bottom + 2f)
        }

        rule.runOnIdle { trackCount.intValue = 10 }
        rule.waitForIdle()
        val previewAfter = rule.onNodeWithTag("editor-preview-slot").fetchSemanticsNode().boundsInRoot
        val timelineAfter = rule.onNodeWithTag("editor-timeline-slot").fetchSemanticsNode().boundsInRoot
        assertTrue("Adding tracks must not collapse preview", abs(previewAfter.height - previewBefore.height) <= 2f)
        assertTrue("Adding tracks must not grow timeline", abs(timelineAfter.height - timelineBefore.height) <= 2f)

        rule.onNodeWithContentDescription("Certification Track 10").performScrollTo()
        rule.waitForIdle()
        val last = rule.onNodeWithContentDescription("Certification Track 10").fetchSemanticsNode().boundsInRoot
        assertTrue("Track 10 must be reachable by vertical scrolling", last.top >= timelineAfter.top - 2f && last.bottom <= timelineAfter.bottom + 2f)
    }

    @Test
    fun EDITOR_SHELL_RESPONSIVE_METRICS_coverRequiredPortraitClasses() {
        val compact = editorShellMetrics(500.dp)
        val normal = editorShellMetrics(640.dp)
        val tall = editorShellMetrics(800.dp)
        val veryTall = editorShellMetrics(915.dp)

        assertTrue(compact.trackRowHeight in 64.dp..72.dp)
        assertTrue(normal.trackRowHeight in 64.dp..72.dp)
        assertTrue(tall.trackRowHeight in 72.dp..80.dp)
        assertTrue(veryTall.trackRowHeight in 72.dp..80.dp)
        listOf(compact, normal, tall, veryTall).forEach { metrics ->
            assertTrue(
                "Timeline height must equal compact chrome plus exactly three rows",
                abs(metrics.timelineViewportHeight.value - (84f + metrics.trackRowHeight.value * 3f)) < 0.1f
            )
        }
    }

    @Test
    fun FOCUSED_TOOL_HOST_GEOMETRY_replacesMainTimelineAndPinsActions() {
        val focused = mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(650.dp).testTag("cert-focused-root")) {
                    if (focused.value) {
                        FocusedEditorWorkspace(
                            preview = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .testTag("cert-focused-preview")
                                        .semantics { contentDescription = "Focused certification preview" }
                                )
                            },
                            transport = {
                                Box(Modifier.fillMaxWidth().height(52.dp).testTag("cert-focused-transport"))
                            },
                            focusedTool = {
                                FocusedToolScaffold(
                                    toolKey = "geometry",
                                    fallbackCancel = { focused.value = false }
                                ) {
                                    registerFocusedToolActions(
                                        onCancel = { focused.value = false },
                                        onReset = null,
                                        onDone = { focused.value = false }
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(420.dp)
                                            .testTag("cert-long-primary-controls")
                                    )
                                }
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize().testTag("cert-main-return"))
                    }
                }
            }
        }

        rule.onNodeWithTag("editor-focused-workspace").fetchSemanticsNode()
        assertTrue(
            "Main timeline must be absent in focused mode",
            rule.onAllNodesWithTag("editor-timeline-slot").fetchSemanticsNodes().isEmpty()
        )
        val root = rule.onNodeWithTag("cert-focused-root").fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithTag("focused-tool-action-bar").fetchSemanticsNode().boundsInRoot
        assertTrue("Focused action bar must be inside the workspace", action.bottom <= root.bottom + 2f)
        assertTrue("Focused action bar must not scroll away", action.height >= 48f * density - 2f)

        val cancel = rule.onNodeWithContentDescription("Cancel").fetchSemanticsNode().boundsInRoot
        val done = rule.onNodeWithContentDescription("Done").fetchSemanticsNode().boundsInRoot
        assertTrue("Cancel touch target must be >=48dp", cancel.width >= 48f * density - 2f && cancel.height >= 48f * density - 2f)
        assertTrue("Done touch target must be >=48dp", done.width >= 48f * density - 2f && done.height >= 48f * density - 2f)

        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.onNodeWithTag("cert-main-return").fetchSemanticsNode()

        rule.runOnIdle { focused.value = true }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Done").performClick()
        rule.onNodeWithTag("cert-main-return").fetchSemanticsNode()
    }

    @Test
    fun EDITOR_SHELL_LANDSCAPE_focusedControlsUseSidePanel() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    FocusedEditorWorkspace(
                        preview = { Box(Modifier.fillMaxSize().testTag("cert-wide-preview")) },
                        transport = { Box(Modifier.fillMaxWidth().height(52.dp)) },
                        focusedTool = {
                            FocusedToolScaffold(fallbackCancel = {}) {
                                registerFocusedToolActions(onCancel = {}, onReset = null, onDone = {})
                                Box(Modifier.fillMaxWidth().height(160.dp))
                            }
                        }
                    )
                }
            }
        }
        val preview = rule.onNodeWithTag("editor-preview-slot").fetchSemanticsNode().boundsInRoot
        val tool = rule.onNodeWithTag("editor-focused-tool-slot").fetchSemanticsNode().boundsInRoot
        assertTrue("Landscape focused controls should be to the right of preview", tool.left >= preview.right - 2f)
        assertTrue("Preview must remain the larger landscape region", preview.width > tool.width)
    }

    @Test
    fun EDITOR_SHELL_TOOL_CAROUSEL_reachesFirstAndLastWithAccessibleTargets() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(100.dp)) {
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
        }

        listOf("Media", "More").forEach { label ->
            val node = rule.onNodeWithContentDescription(label)
            node.performScrollTo()
            rule.waitForIdle()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$label width must be >=48dp", bounds.width >= 48f * density - 2f)
            assertTrue("$label height must be >=48dp", bounds.height >= 48f * density - 2f)
        }
    }
}
