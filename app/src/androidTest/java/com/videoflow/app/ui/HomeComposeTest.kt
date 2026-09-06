package com.videoflow.app.ui

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeComposeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun uiStep3ProductFlow_homeSettingsCreateAndExport() {
        reachProductHome()

        rule.onNodeWithText("VideoFlow").fetchSemanticsNode()
        rule.onNodeWithText("Recent Projects").fetchSemanticsNode()
        rule.onNodeWithContentDescription("New Project").fetchSemanticsNode()

        // Settings must expose real product policy rather than a placebo proxy preference.
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitUntil(10_000) { nodeExistsWithText("Settings") && nodeExistsWithText("Proxy policy") }
        rule.onNodeWithText("Proxy policy").fetchSemanticsNode()
        rule.onNodeWithText("Editing proxies").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.waitUntil(10_000) { nodeExistsWithDescription("New Project") }
        rule.onNodeWithContentDescription("New Project").performClick()

        // Use controls unique to the modal rather than the repeated "New Project" label
        // that also exists on the Home CTA and empty-state action behind the dialog.
        rule.waitUntil(10_000) {
            nodeExistsWithText("16:9 Landscape") &&
                nodeExistsWithText("Start from Media") &&
                nodeExistsWithText("Create Project")
        }
        rule.onNodeWithText("16:9 Landscape").fetchSemanticsNode()
        rule.onNodeWithText("Start from Media").fetchSemanticsNode()
        rule.onNodeWithText("Create Project").fetchSemanticsNode()

        val projectNameField = rule.onNodeWithText("Project Name")
        projectNameField.performTextClearance()
        projectNameField.performTextInput("UI Step 3 Product Test")
        rule.waitUntil(5_000) { nodeExistsWithText("UI Step 3 Product Test") }
        rule.onNodeWithText("Create Project").performClick()

        rule.waitUntil(15_000) {
            nodeExistsWithText("Start your video") && nodeExistsWithText("Export")
        }
        rule.onAllNodesWithText("Start your video").fetchSemanticsNodes().also {
            check(it.isNotEmpty()) { "Editor empty-state should be visible after project creation" }
        }
        rule.onNodeWithContentDescription("Media").fetchSemanticsNode()
        rule.onNodeWithText("Export").performClick()

        rule.waitUntil(15_000) {
            nodeExistsWithText("Export Video") && nodeExistsWithText("Recommended export")
        }
        rule.onNodeWithText("Export Video").fetchSemanticsNode()
        rule.onNodeWithText("Recommended export").fetchSemanticsNode()

        // The export content is a LazyColumn. On the API-35 certification device (320x640),
        // Advanced Settings is intentionally below the fold. The first scrollable semantics
        // node is the parent vertical export list; selecting it without a transient-descendant
        // predicate keeps the interaction valid while LazyColumn items enter/leave composition.
        rule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Advanced Settings"))
        rule.onNodeWithText("Advanced Settings").fetchSemanticsNode()
    }

    private fun reachProductHome() {
        rule.waitUntil(10_000) {
            nodeExistsWithText("Skip") || nodeExistsWithDescription("New Project")
        }
        if (nodeExistsWithText("Skip")) {
            rule.onNodeWithText("Skip").performClick()
        }
        rule.waitUntil(10_000) { nodeExistsWithDescription("New Project") }
    }

    private fun nodeExistsWithText(text: String): Boolean =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun nodeExistsWithDescription(description: String): Boolean =
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
}
