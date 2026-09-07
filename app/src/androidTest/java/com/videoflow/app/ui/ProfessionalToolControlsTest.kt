package com.videoflow.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.ui.editor.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfessionalToolControlsTest {
    @get:Rule val rule=createComposeRule()
    @Test fun contextualRailRoutesEveryProfessionalTool() {
        var selected: ProfessionalEditorTool?=null
        rule.setContent { MaterialTheme { EditorBottomToolbar(EditorSelection.Clip("clip"),"video/mp4",{},{},{},{ selected=it }) } }
        for ((label,expected) in listOf("Audio" to ProfessionalEditorTool.AudioExtract("clip"),"Effects" to ProfessionalEditorTool.Effects("clip"),
            "Enhance" to ProfessionalEditorTool.Enhance("clip"),"AI Tools" to ProfessionalEditorTool.AiWatermark("clip"),"Precise Trim" to ProfessionalEditorTool.PreciseTrim("clip"))) {
            rule.onNodeWithContentDescription(label).performScrollTo().performClick()
            assertEquals(expected,selected)
        }
    }
    @Test fun preciseRangeKeepsHourScaleTimeAndSetStartEnd() {
        var start by mutableLongStateOf(0L); var end by mutableLongStateOf(7_200_000_000L)
        var playhead by mutableLongStateOf(3_600_123_000L)
        rule.setContent { MaterialTheme { PreciseRangeControls(7_200_000_000L,start,end,playhead,{a,b->start=a;end=b},{playhead=it}) } }
        rule.onNodeWithText("Set Start").performClick()
        assertEquals(3_600_123_000L,start)
        rule.runOnIdle { playhead=5_400_456_000L }
        rule.onNodeWithText("Set End").performScrollTo().performClick()
        assertEquals(5_400_456_000L,end)
        rule.onNodeWithText("Zoom in").performClick()
        assertEquals(3_600_123_000L,start)
        rule.onNodeWithText("Full clip").performScrollTo().performClick()
        assertEquals(0L,start);assertEquals(7_200_000_000L,end)
    }
}
