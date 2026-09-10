package com.videoflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.ui.editor.EditorBottomToolbar
import com.videoflow.app.ui.editor.EditorSelection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UXStep1FontScaleComposeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun EDITOR_SHELL_ACCESSIBILITY_fontScales100_115_130_150RemainReachable() {
        val requestedFontScale = mutableFloatStateOf(1.0f)
        rule.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(systemDensity.density, requestedFontScale.floatValue)
            ) {
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
        }

        for (scale in listOf(1.0f, 1.15f, 1.30f, 1.50f)) {
            rule.runOnIdle { requestedFontScale.floatValue = scale }
            rule.waitForIdle()
            listOf("Media", "More").forEach { label ->
                val node = rule.onNodeWithContentDescription(label)
                node.performScrollTo()
                rule.waitForIdle()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertTrue("$label must remain operable at font scale $scale", bounds.width > 0f && bounds.height > 0f)
            }
        }
    }
}
