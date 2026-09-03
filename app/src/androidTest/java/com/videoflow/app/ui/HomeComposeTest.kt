package com.videoflow.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun homeNewProjectOpensStep2MediaBinAffordances() {
        rule.onNodeWithText("VideoFlow").fetchSemanticsNode()
        rule.onNodeWithContentDescription("Settings").fetchSemanticsNode()
        rule.onNodeWithContentDescription("New Project").performClick()

        val projectNameField = rule.onNodeWithText("Project name")
        projectNameField.performTextClearance()
        projectNameField.performTextInput("Step 2 UI Test")

        rule.onNodeWithText("Create", useUnmergedTree = true).performClick()

        // Creating a project must navigate into the Step 2 project media bin. Verify the
        // current Step 2 affordances instead of the removed Step 1 "Add media" semantic.
        rule.waitUntil(15_000) {
            runCatching {
                rule.onNodeWithText("Import Media").fetchSemanticsNode()
                rule.onNodeWithText("Open Editor").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        rule.onNodeWithText("Project Media Bin").fetchSemanticsNode()
        rule.onNodeWithText("Import Media").fetchSemanticsNode()
        rule.onNodeWithText("Open Editor").fetchSemanticsNode()
    }
}
