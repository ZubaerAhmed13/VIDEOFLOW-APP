package com.videoflow.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.ui.editor.EditorBottomToolbar
import com.videoflow.app.ui.editor.EditorPanel
import com.videoflow.app.ui.editor.EditorSelection
import com.videoflow.app.ui.editor.EditorTool
import com.videoflow.app.ui.editor.VisualOwnerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextualToolbarComposeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun videoSelection_exposesProfessionalContextualActions() {
        var openedTool: EditorTool? = null
        var split = false
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.Clip("clip-1"),
                    selectedClipMime = "video/mp4",
                    onPanel = {},
                    onTool = { openedTool = it },
                    onSplit = { split = true }
                )
            }
        }

        listOf("Split", "Trim", "Speed", "Crop", "Volume", "More").forEach {
            rule.onNodeWithContentDescription(it).fetchSemanticsNode()
        }
        rule.onNodeWithContentDescription("Split").performClick()
        assertTrue(split)
        rule.onNodeWithContentDescription("Trim").performClick()
        assertEquals(EditorTool.Trim("clip-1"), openedTool)
    }

    @Test
    fun audioSelection_exposesTrimVolumeFadeAndSpeed() {
        var openedTool: EditorTool? = null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.Clip("audio-1"),
                    selectedClipMime = "audio/mpeg",
                    onPanel = {},
                    onTool = { openedTool = it },
                    onSplit = {}
                )
            }
        }

        listOf("Split", "Trim", "Volume", "Fade", "Speed", "More").forEach {
            rule.onNodeWithContentDescription(it).fetchSemanticsNode()
        }
        rule.onNodeWithContentDescription("Fade").performClick()
        assertEquals(EditorTool.Fade("audio-1"), openedTool)
    }

    @Test
    fun textSelection_routesToSharedTransformArchitecture() {
        var openedTool: EditorTool? = null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.TextOverlay("text-1"),
                    selectedClipMime = null,
                    onPanel = {},
                    onTool = { openedTool = it },
                    onSplit = {}
                )
            }
        }
        rule.onNodeWithContentDescription("Transform").performClick()
        assertEquals(EditorTool.Transform("text-1", VisualOwnerType.TEXT), openedTool)
    }

    @Test
    fun imageSelection_routesToSharedTransformArchitecture() {
        var openedTool: EditorTool? = null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.ImageOverlay("image-1"),
                    selectedClipMime = null,
                    onPanel = {},
                    onTool = { openedTool = it },
                    onSplit = {}
                )
            }
        }
        rule.onNodeWithContentDescription("Transform").performClick()
        assertEquals(EditorTool.Transform("image-1", VisualOwnerType.IMAGE), openedTool)
    }

    @Test
    fun noSelection_preservesApprovedStepOnePrimaryToolbar() {
        var panel: EditorPanel? = null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.None,
                    selectedClipMime = null,
                    onPanel = { panel = it },
                    onTool = {},
                    onSplit = {}
                )
            }
        }
        listOf("Media", "Audio", "Text", "Overlay", "Canvas", "More").forEach {
            rule.onNodeWithContentDescription(it).fetchSemanticsNode()
        }
        rule.onNodeWithContentDescription("Media").performClick()
        assertEquals(EditorPanel.Media, panel)
    }
}
