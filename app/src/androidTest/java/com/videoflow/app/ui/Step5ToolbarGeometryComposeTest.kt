package com.videoflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.videoflow.app.ui.editor.EditorBottomToolbar
import com.videoflow.app.ui.editor.EditorSelection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Step5ToolbarGeometryComposeTest {
    @get:Rule val rule = createComposeRule()

    private val labels = listOf(
        "Split", "Trim", "Speed", "Crop", "Volume", "Audio", "Text", "Overlay",
        "Effects", "Enhance", "AI Tools", "Precise Trim", "Canvas", "More"
    )

    @Test
    fun videoToolbarKeepsTouchTargetsAndLabelsAcrossWidthsAndFontScales() {
        val widthDp = mutableIntStateOf(360)
        val fontScale = mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale.floatValue)) {
                Box(Modifier.width(widthDp.intValue.dp)) {
                    EditorBottomToolbar(
                        selection = EditorSelection.Clip("clip"),
                        selectedClipMime = "video/mp4",
                        onPanel = {},
                        onTool = {},
                        onSplit = {},
                        onProfessionalTool = {}
                    )
                }
            }
        }

        val widths = listOf(360, 393, 412, 480, 600)
        val fontScales = listOf(1f, 1.15f, 1.3f, 1.5f)
        for (width in widths) {
            for (scale in fontScales) {
                rule.runOnIdle {
                    widthDp.intValue = width
                    fontScale.floatValue = scale
                }
                rule.waitForIdle()

                // A horizontal scroll container clips boundsInRoot for off-screen children.
                // Bring each tool fully into view before certifying its actual interactive cell.
                labels.forEach { label ->
                    val node = rule.onNodeWithContentDescription(label)
                    node.performScrollTo()
                    rule.waitForIdle()
                    val cell = node.fetchSemanticsNode().boundsInRoot
                    assertTrue("$label touch width at ${width}dp/$scale", cell.width >= 48f)
                    assertTrue("$label touch height at ${width}dp/$scale", cell.height >= 48f)

                    val text = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        "$label text exceeds its cell horizontally at ${width}dp/$scale",
                        text.left >= cell.left - 1f && text.right <= cell.right + 1f
                    )
                    assertTrue(
                        "$label text exceeds its cell vertically at ${width}dp/$scale",
                        text.top >= cell.top - 1f && text.bottom <= cell.bottom + 1f
                    )
                }

                rule.onNodeWithContentDescription("Split").performScrollTo()
                rule.waitForIdle()
                val first = rule.onNodeWithContentDescription("Split").fetchSemanticsNode().boundsInRoot
                assertTrue("first-cell leading padding missing at ${width}dp/$scale", first.left >= 11f)
            }
        }
    }

    @Test
    fun videoToolbarKeepsNonOverlappingEightDpSpacingAcrossFontScales() {
        val fontScale = mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale.floatValue)) {
                // Deliberately expose the whole row. Pairwise spacing must be measured in one
                // common coordinate space; independently scrolling each item changes that space
                // and can make perfectly spaced neighbours appear to have a zero gap.
                Box(Modifier.width(1400.dp)) {
                    EditorBottomToolbar(
                        selection = EditorSelection.Clip("clip"),
                        selectedClipMime = "video/mp4",
                        onPanel = {},
                        onTool = {},
                        onSplit = {},
                        onProfessionalTool = {}
                    )
                }
            }
        }

        listOf(1f, 1.15f, 1.3f, 1.5f).forEach { scale ->
            rule.runOnIdle { fontScale.floatValue = scale }
            rule.waitForIdle()

            val bounds = labels.map { label ->
                rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            }
            bounds.zipWithNext().forEachIndexed { index, (left, right) ->
                val leftLabel = labels[index]
                val rightLabel = labels[index + 1]
                assertTrue(
                    "$leftLabel overlaps $rightLabel at fontScale=$scale",
                    left.right <= right.left + 0.5f
                )
                assertTrue(
                    "$leftLabel/$rightLabel toolbar gap collapsed at fontScale=$scale",
                    right.left - left.right >= 7f
                )
            }
        }
    }

    @Test
    fun laterToolsAreReachableAndEndPaddingSurvivesHorizontalScroll() {
        val widthDp = 360
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                Box(Modifier.width(widthDp.dp)) {
                    EditorBottomToolbar(
                        selection = EditorSelection.Clip("clip"),
                        selectedClipMime = "video/mp4",
                        onPanel = {},
                        onTool = {},
                        onSplit = {},
                        onProfessionalTool = {}
                    )
                }
            }
        }
        repeat(8) {
            rule.onRoot().performTouchInput { swipeLeft(startX = 340f, endX = 40f, durationMillis = 120) }
            rule.waitForIdle()
        }
        rule.onNodeWithContentDescription("More").assertIsDisplayed()
        rule.onNodeWithContentDescription("Precise Trim").assertIsDisplayed()
        val last = rule.onNodeWithContentDescription("More").fetchSemanticsNode().boundsInRoot
        assertTrue("last-cell trailing padding missing", last.right <= widthDp - 11f)
    }
}
