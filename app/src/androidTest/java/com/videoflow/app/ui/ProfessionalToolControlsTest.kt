package com.videoflow.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.ui.editor.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfessionalToolControlsTest {
    @get:Rule val rule=createComposeRule()
    @Test fun contextualRailHasOneTrimAndRoutesProfessionalTools() {
        var selectedTool: EditorTool?=null
        var selectedProfessional: ProfessionalEditorTool?=null
        rule.setContent {
            MaterialTheme {
                EditorBottomToolbar(
                    EditorSelection.Clip("clip"),
                    "video/mp4",
                    {},
                    { selectedTool=it },
                    {},
                    { selectedProfessional=it }
                )
            }
        }
        rule.onNodeWithContentDescription("Trim").performScrollTo().performClick()
        assertEquals(EditorTool.Trim("clip"),selectedTool)
        rule.onAllNodesWithContentDescription("Precise Trim").assertCountEquals(0)
        for ((label,expected) in listOf(
            "Audio" to ProfessionalEditorTool.AudioExtract("clip"),
            "Effects" to ProfessionalEditorTool.Effects("clip"),
            "Enhance" to ProfessionalEditorTool.Enhance("clip"),
            "AI Tools" to ProfessionalEditorTool.AiWatermark("clip")
        )) {
            rule.onNodeWithContentDescription(label).performScrollTo().performClick()
            assertEquals(expected,selectedProfessional)
        }
    }
    @Test fun preciseRangeKeepsHourScaleTimeAndSetStartEnd() {
        var start by mutableLongStateOf(0L); var end by mutableLongStateOf(7_200_000_000L)
        var playhead by mutableLongStateOf(3_600_123_000L)
var showCrop by mutableStateOf(false)
var crop by mutableStateOf(CropRect(.2f,.2f,.8f,.8f))
var committed: CropRect?=null
val initial=crop
        rule.setContent {
    MaterialTheme {
        if (showCrop) {
            Box(Modifier.size(300.dp)) {
                CropInteractionOverlay(
                    crop=crop,
                    aspectRatio=1f,
                    onCropChange={crop=it},
                    onCropCommit={committed=it},
                    modifier=Modifier.semantics { contentDescription="Crop direct interaction" }
                )
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PreciseRangeControls(7_200_000_000L,start,end,playhead,{a,b->start=a;end=b},{playhead=it})
            }
        }
    }
}
        rule.onNodeWithText("Set Start").performScrollTo().performClick()
        assertEquals(3_600_123_000L,start)
        rule.runOnIdle { playhead=5_400_456_000L }
        rule.onNodeWithText("Set End").performScrollTo().performClick()
        assertEquals(5_400_456_000L,end)
        rule.onNodeWithText("Zoom in").performScrollTo().performClick()
        assertEquals(3_600_123_000L,start)
        rule.onNodeWithText("Full clip").performScrollTo().performClick()
        assertEquals(0L,start);assertEquals(7_200_000_000L,end)

        rule.runOnIdle { showCrop=true }
        rule.waitForIdle()
        val cropNode=rule.onNodeWithContentDescription("Crop direct interaction")
        val bounds=cropNode.fetchSemanticsNode().boundsInRoot
        cropNode.performTouchInput {
            swipe(
                start=androidx.compose.ui.geometry.Offset(bounds.width*.80f,bounds.height*.80f),
                end=androidx.compose.ui.geometry.Offset(bounds.width*.68f,bounds.height*.68f),
                durationMillis=300
            )
        }
        rule.waitForIdle()
        assertNotEquals("Corner drag must change crop geometry",initial,crop)
        assertEquals("Drag end must commit the same geometry shown in preview",crop,committed)
        val ratio=(crop.right-crop.left)/(crop.bottom-crop.top)
        assertEquals("1:1 aspect must stay constrained while dragging",1f,ratio,.03f)
    }
}
