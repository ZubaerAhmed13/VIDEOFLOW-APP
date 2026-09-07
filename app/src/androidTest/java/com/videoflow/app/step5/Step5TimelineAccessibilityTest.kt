package com.videoflow.app.step5

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.editor.*
import com.videoflow.app.ui.editor.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step5TimelineAccessibilityTest {
    @get:Rule val rule=createComposeRule()
    @Test fun eightHourNavigatorReachesLateClipAndZoomHasAccessibleTargets() {
        var time by mutableLongStateOf(0L);var zoom by mutableFloatStateOf(240f)
        val duration=28_800_000_000L
        val track=TimelineTrack("track","step5-timeline",TrackType.VIDEO,"Video",0)
        val clips=listOf(TimelineClip("opening",track.projectId,track.id,"a",0,0,1_000_000),
            TimelineClip("last",track.projectId,track.id,"b",duration-1_000_000,0,1_000_000))
        rule.setContent { MaterialTheme { TimelineWorkspace(listOf(track),clips,emptyList(),emptyList(),emptyList(),time,duration,zoom,
            EditorSelection.None,mapOf("a" to "Opening","b" to "Last second"),emptyMap(),emptyMap(),{zoom=it},{time=it},{},{},{_,_->},
            onToggleMute={},onToggleVisible={},onToggleLock={},onTrackSettings={},modifier=Modifier.fillMaxSize()) } }
        val button=rule.onNodeWithContentDescription("Zoom in timeline")
        button.assertHasClickAction().assertIsDisplayed()
        val bounds=button.getUnclippedBoundsInRoot()
        assertTrue("Zoom target must be at least 48 dp: $bounds",bounds.right.value-bounds.left.value>=48f && bounds.bottom.value-bounds.top.value>=48f)
        rule.onNodeWithContentDescription("Navigate whole project",substring=true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.99999f) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Last second").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(time>Int.MAX_VALUE)
        rule.onNodeWithText("Last second").assertIsDisplayed()
        rule.onNodeWithContentDescription("Zoom out timeline").performClick()
        assertTrue(zoom<240f)
    }
}
