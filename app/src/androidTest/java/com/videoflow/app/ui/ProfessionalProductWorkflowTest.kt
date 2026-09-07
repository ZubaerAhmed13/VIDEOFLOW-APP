package com.videoflow.app.ui

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.ai.watermark.*
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.audio.AudioExtractionService
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.media.MediaAnalyzer
import com.videoflow.app.data.media.UriFingerprintService
import com.videoflow.app.data.project.*
import com.videoflow.app.domain.effects.Adjustment
import com.videoflow.app.ui.ai.*
import com.videoflow.app.ui.editor.ProfessionalEditorTool
import com.videoflow.app.ui.effects.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Drives the real panels and ViewModels against real media, Room, shaders and local inference. */
@RunWith(AndroidJUnit4::class)
class ProfessionalProductWorkflowTest {
    @get:Rule val rule=createComposeRule()
    @Test fun audioEffectsEnhanceAndCorrectedAiThroughProductPanels() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val db=Room.inMemoryDatabaseBuilder(context,VideoFlowDatabase::class.java).build()
        val resolver=context.contentResolver
        val source=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,"professional-ui-${System.nanoTime()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VideoFlowCertification")
        })!!
        val ai=AiWatermarkRepository(context)
        val store=ViewModelStore()
        var projectId: String?=null
        try {
            resolver.openOutputStream(source)!!.use { out -> instrumentation.context.assets.open("sample_av.mp4").use { it.copyTo(out) } }
            val projects=ProjectRepository(db,context,MediaAnalyzer(context),UriFingerprintService(context),LocalDiagnosticLog())
            val id=projects.createProject("Professional product workflow");projectId=id
            val asset=(projects.addMedia(id,source) as AddMediaResult.Added).asset
            val editor=EditorRepository(db)
            val clip=editor.addClip(id,asset.id,0L)
            val loaded=editor.load(id)
            val project=projects.getProject(id)!!
            val history=EditHistoryService(db,ai)
            val toolsVm=ProfessionalToolsViewModel(ai,history,AudioExtractionService(context,db,editor,projects,history),context)
            val manager=AiModelPackManager(context)
            val previewEngine=LocalWatermarkPreviewEngine(context,manager)
            val processedPreviewManager=AiProcessedPreviewManager(context,editor,projects,ai,manager)
            val aiVm=WatermarkStudioViewModel(ai,manager,previewEngine,processedPreviewManager,LocalRoiTracker(previewEngine),history)
            store.put("tools",toolsVm);store.put("ai",aiVm)
            var tool by mutableStateOf<ProfessionalEditorTool?>(ProfessionalEditorTool.AudioExtract(clip.id))
            rule.setContent { com.videoflow.app.ui.theme.VideoFlowTheme(com.videoflow.app.ui.product.AppAppearance.DARK) { Surface(Modifier.fillMaxSize(),color=com.videoflow.app.ui.editor.VideoFlowEditorColors.EditorSurfaceElevated,contentColor=com.videoflow.app.ui.editor.VideoFlowEditorColors.PrimaryText) {
                val selected=tool
                if(selected is ProfessionalEditorTool.AiWatermark) WatermarkStudioPanel(id,clip.id,project,loaded,0L,{tool=null},{},vm=aiVm)
                else if(selected!=null) ProfessionalToolPanel(id,selected,clip,asset,{tool=null},{},vm=toolsVm)
            } } }
            rule.waitUntil(30_000) { toolsVm.state.value.loaded }
            rule.onNodeWithText("Extract audio").performScrollTo().performClick()
            rule.waitUntil(60_000) { tool==null }
            assertEquals(2,editor.load(id).timeline.clips.size)

            rule.runOnIdle { tool=ProfessionalEditorTool.Effects(clip.id) }
            rule.waitUntil(30_000) { toolsVm.state.value.loaded }
            waitForVideoFrame()
            val originalPreview=stablePreviewPixels()
            rule.onNodeWithText("Film",substring=false).performScrollTo().performClick()
            rule.onNodeWithText("Sepia").performScrollTo().performClick()
            assertTrue(ai.visualEdits.load(id).effects.isEmpty()) // Draft does not persist on selection.
            waitForVideoFrame()
            waitForPreviewChange(originalPreview)
            screenshot("effects")
            rule.onNodeWithText("Done").performClick()
            rule.waitUntil(30_000) { tool==null }
            assertEquals(1,ai.visualEdits.load(id).effects.size)

            rule.runOnIdle { tool=ProfessionalEditorTool.Enhance(clip.id) }
            rule.waitUntil(30_000) { toolsVm.state.value.loaded }
            waitForVideoFrame()
            val beforeEnhanceStage=stablePreviewPixels()
            // Darken first: the fixture has saturated primaries that positive exposure can clip.
            rule.onNodeWithContentDescription("Exposure adjustment").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(-.2f) }
            waitForVideoFrame()
            waitForPreviewChange(beforeEnhanceStage)
            val beforeExposure=stablePreviewPixels()
            rule.onNodeWithContentDescription("Exposure adjustment").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.2f) }
            assertEquals(.2f,toolsVm.state.value.draft.enhance.getValue(clip.id)[Adjustment.EXPOSURE],.001f)
            waitForVideoFrame()
            waitForPreviewChange(beforeExposure)
            screenshot("enhance")
            rule.onNodeWithText("Done").performClick()
            rule.waitUntil(30_000) { tool==null }
            assertEquals(.2f,ai.visualEdits.load(id).enhance.getValue(clip.id)[Adjustment.EXPOSURE],.001f)

            rule.runOnIdle { tool=ProfessionalEditorTool.AiWatermark(clip.id) }
            rule.waitUntil(120_000) { aiVm.state.value.runtimeReady && aiVm.state.value.sourceFrame!=null && aiVm.state.value.busy==WatermarkStudioBusy.IDLE }
            rule.onNodeWithText("Time",substring=false).performClick()
            rule.onNodeWithContentDescription("AI preview playhead").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.25f) }
            rule.onNodeWithText("Set Start").performScrollTo().performClick()
            rule.onNodeWithContentDescription("AI preview playhead").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.75f) }
            rule.onNodeWithText("Set End").performScrollTo().performClick()
            rule.onNodeWithText("Jump to start").performScrollTo().performClick()
            rule.onNodeWithText("Track",substring=false).performClick()
            rule.onNodeWithText("Add correction").performScrollTo().performClick()
            rule.onNodeWithText("Select",substring=false).performClick()
            rule.onNodeWithText("Move left").performScrollTo().performClick()
            rule.onNodeWithText("Preview",substring=false).performClick()
            rule.waitUntil(30_000) { aiVm.state.value.busy==WatermarkStudioBusy.IDLE }
            rule.onNodeWithText("Generate Still Preview").performScrollTo().performClick()
            rule.waitUntil(180_000) { aiVm.state.value.aiPreview!=null || aiVm.state.value.error!=null }
            assertNotNull(aiVm.state.value.error,aiVm.state.value.aiPreview)
            rule.onNodeWithText("Before",substring=false).performClick()
            rule.onNodeWithText("After",substring=false).performClick()
            screenshot("ai-preview")
            // Stage chip and commit button both say Apply; the chip exists before opening the stage.
            rule.onNodeWithContentDescription("AI stage Apply").performClick()
            rule.onNodeWithContentDescription("Apply AI removal").assertIsEnabled().performScrollTo().performClick()
            rule.waitUntil(30_000) { tool==null || aiVm.state.value.error!=null }
            assertNull("AI commit failed",aiVm.state.value.error)
            assertNull("Applied panel must close",tool)
            val saved=ai.load(id).single()
            assertEquals(Math.round(clip.timelineDurationUs*.25),saved.clipLocalStartUs)
            assertEquals(Math.round(clip.timelineDurationUs*.75),saved.clipLocalEndUs)
            assertEquals(1,saved.motionAnchors.count { it.manual })
            assertTrue(saved.motionAnchors.single().centerX < (.68f+.97f)/2f)
            assertEquals(saved,AiWatermarkRepository(context).load(id).single())
            instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","PROFESSIONAL_PRODUCT_PANELS_CERTIFIED audio=${editor.load(id).timeline.clips.size} effects=1 enhance=0.2 aiCorrections=1\n") })
        } finally {
            runCatching { screenshot("last-state") }
            runCatching { println(rule.onRoot().printToString()) }
            instrumentation.runOnMainSync { store.clear() }
            projectId?.let { ai.deleteProjectState(it);ai.visualEdits.delete(it);File(context.filesDir,"extracted-audio/$it").deleteRecursively() }
            db.close();resolver.delete(source,null,null)
        }
    }
    private fun waitForVideoFrame() {
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("native-video-preview").fetchSemanticsNodes().any {
                it.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "Video preview ready"
            }
        }
    }
    private fun previewPixels(): IntArray {
        val bounds=rule.onNodeWithTag("native-video-preview").fetchSemanticsNode().boundsInRoot
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Could not capture preview pixels")
        // Sample well inside the video surface, away from its edges and surrounding controls.
        // The fixture remains paused, so changing controls alone cannot satisfy this check.
        return try { IntArray(64) { index ->
            val x=(bounds.left+bounds.width*(.25f+(index%8)/14f)).toInt().coerceIn(0,bitmap.width-1)
            val y=(bounds.top+bounds.height*(.25f+(index/8)/14f)).toInt().coerceIn(0,bitmap.height-1)
            bitmap.getPixel(x,y)
        } } finally { bitmap.recycle() }
    }
    private fun waitForPreviewChange(before: IntArray) {
        rule.waitUntil(30_000) {
            val after=previewPixels()
            visiblePixels(after) && pixelDifference(before,after)>3.0
        }
    }
    private fun stablePreviewPixels(): IntArray {
        var previous: IntArray?=null
        var stable=0
        rule.waitUntil(30_000) {
            val current=previewPixels()
            stable=if(visiblePixels(current) && previous?.let { pixelDifference(it,current)<.5 }==true) stable+1 else 0
            previous=current
            stable>=2
        }
        return checkNotNull(previous)
    }
    private fun pixelDifference(before: IntArray, after: IntArray): Double = before.indices.sumOf { index ->
        listOf(0,8,16).sumOf { shift ->
            kotlin.math.abs(((before[index] ushr shift) and 255)-((after[index] ushr shift) and 255))
        }
    }.toDouble()/(before.size*3)
    private fun visiblePixels(pixels: IntArray): Boolean = pixels.sumOf { pixel ->
        listOf(0,8,16).sumOf { shift -> (pixel ushr shift) and 255 }
    }.toDouble()/(pixels.size*3)>20.0
    private fun screenshot(label: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"professional-screenshots/$label.png")
        file.parentFile!!.mkdirs()
        rule.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("Could not capture product screen")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
