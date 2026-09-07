package com.videoflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
    fun videoToolbarKeepsTouchTargetsSpacingAndLabelsAcrossWidthsAndFontScales() {
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

                val buttonBounds = labels.map { label ->
                    rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                }
                buttonBounds.forEachIndexed { index, bounds ->
                    assertTrue("${labels[index]} touch width at ${width}dp/$scale", bounds.width >= 48f)
                    assertTrue("${labels[index]} touch height at ${width}dp/$scale", bounds.height >= 48f)
                }
                buttonBounds.zipWithNext().forEachIndexed { index, (left, right) ->
                    assertTrue("${labels[index]} overlaps ${labels[index + 1]} at ${width}dp/$scale", left.right <= right.left + 0.5f)
                    assertTrue("toolbar gap collapsed at ${width}dp/$scale", right.left - left.right >= 7f)
                }
                assertTrue("first-cell leading padding missing at ${width}dp/$scale", buttonBounds.first().left >= 11f)

                labels.forEach { label ->
                    val cell = rule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                    val text = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue("$label text exceeds its cell horizontally at fontScale=$scale", text.left >= cell.left - 1f && text.right <= cell.right + 1f)
                    assertTrue("$label text exceeds its cell vertically at fontScale=$scale", text.top >= cell.top - 1f && text.bottom <= cell.bottom + 1f)
                }
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
