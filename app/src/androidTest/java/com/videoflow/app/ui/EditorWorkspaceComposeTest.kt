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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorWorkspaceComposeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun primaryToolbarExposesMainEditingCategories() {
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.None,
                    selectedClipMime = null,
                    onPanel = {},
                    onTool = {},
                    onSplit = {}
                )
            }
        }

        listOf("Media", "Audio", "Text", "Overlay", "Canvas", "More").forEach { label ->
            rule.onNodeWithContentDescription(label).fetchSemanticsNode()
        }
    }

    @Test
    fun selectedVideoClipSwitchesToContextualToolbar() {
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.Clip("clip-1"),
                    selectedClipMime = "video/mp4",
                    onPanel = {},
                    onTool = {},
                    onSplit = {}
                )
            }
        }

        listOf("Split", "Trim", "Speed", "Crop", "Volume", "More").forEach { label ->
            rule.onNodeWithContentDescription(label).fetchSemanticsNode()
        }
    }

    @Test
    fun selectedAudioClipExposesAudioContext() {
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.Clip("audio-1"),
                    selectedClipMime = "audio/mpeg",
                    onPanel = {},
                    onTool = {},
                    onSplit = {}
                )
            }
        }

        listOf("Split", "Trim", "Volume", "Fade", "Speed", "More").forEach { label ->
            rule.onNodeWithContentDescription(label).fetchSemanticsNode()
        }
    }

    @Test
    fun mediaTapRoutesToMediaPanel() {
        var opened: EditorPanel? = null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.None,
                    selectedClipMime = null,
                    onPanel = { opened = it },
                    onTool = {},
                    onSplit = {}
                )
            }
        }

        rule.onNodeWithContentDescription("Media").performClick()
        rule.runOnIdle { assertEquals(EditorPanel.Media, opened) }
    }

    @Test
    fun splitIsDirectOneTapActionForSelectedVideoClip() {
        var splitInvoked = false
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.Clip("clip-1"),
                    selectedClipMime = "video/mp4",
                    onPanel = {},
                    onTool = {},
                    onSplit = { splitInvoked = true }
                )
            }
        }

        rule.onNodeWithContentDescription("Split").performClick()
        rule.runOnIdle { assertTrue(splitInvoked) }
    }

    @Test
    fun trimTapCarriesSelectedClipIdentityToContextualTool() {
        var openedTool: EditorTool? = null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    selection = EditorSelection.Clip("clip-42"),
                    selectedClipMime = "video/mp4",
                    onPanel = {},
                    onTool = { openedTool = it },
                    onSplit = {}
                )
            }
        }

        rule.onNodeWithContentDescription("Trim").performClick()
        rule.runOnIdle {
            assertEquals(EditorTool.Trim("clip-42"), openedTool)
        }
    }
}
