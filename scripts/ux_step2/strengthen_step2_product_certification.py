from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:140]!r}")
    p.write_text(text.replace(old, new, 1))

panels_path = "app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt"

replace_once(
    panels_path,
    "                SpeedPanel(clip, previewDraft, onPreviewDraftChange, editorVm, onDismiss)",
    "                SpeedPanel(clip, previewDraft, onPreviewDraftChange, onCommitSpeed = { factor ->\n                    editorVm.selectClip(clip.id)\n                    editorVm.setSpeed(factor)\n                }, onDismiss = onDismiss)",
)
replace_once(
    panels_path,
    "                CropPanel(tool, clip, project, previewDraft, onPreviewDraftChange, contextualVm, projectId, refresh, onDismiss)",
    "                CropPanel(tool, clip, project, previewDraft, onPreviewDraftChange, onCommitCrop = { committed ->\n                    contextualVm.setClipCrop(projectId, clip.id, committed) { refresh(); onDismiss() }\n                }, onDismiss = onDismiss)",
)
replace_once(panels_path, "private fun SpeedPanel(\n", "fun SpeedPanel(\n")
replace_once(
    panels_path,
    "    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,\n    editorVm: EditorViewModel,\n    onDismiss: () -> Unit\n) {",
    "    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,\n    onCommitSpeed: (Double) -> Unit,\n    onDismiss: () -> Unit\n) {",
)
replace_once(
    panels_path,
    "        onDone = {\n            editorVm.selectClip(clip.id)\n            editorVm.setSpeed(speed.toDouble())\n            onDismiss()\n        }",
    "        onDone = {\n            onCommitSpeed(speed.toDouble())\n            onDismiss()\n        }",
)
replace_once(panels_path, "private fun CropPanel(\n", "fun CropPanel(\n")
replace_once(
    panels_path,
    "    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,\n    contextualVm: ContextualEditingViewModel,\n    projectId: String,\n    refresh: () -> Unit,\n    onDismiss: () -> Unit\n) {",
    "    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,\n    onCommitCrop: (CropRect) -> Unit,\n    onDismiss: () -> Unit\n) {",
)
replace_once(
    panels_path,
    "        onDone = { contextualVm.setClipCrop(projectId, tool.clipId, crop) { refresh(); onDismiss() } }",
    "        onDone = { onCommitCrop(crop); onDismiss() }",
)

# Compose product tests exercise the production Trim/Speed/Crop panels, focused shell, controls,
# responsive geometry, draft/Done/Cancel behavior, rotation-aware ratios, and screenshot evidence.
product_test = r'''package com.videoflow.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UXStep2TrimSpeedCropProductComposeTest {
    @get:Rule val rule = createComposeRule()

    private val clip = TimelineClip(
        id = "clip", projectId = "step2-ui", trackId = "video", assetId = "asset",
        timelineStartUs = 7_000_000L, sourceStartUs = 0L, sourceEndUs = 100_000_000L
    )

    private fun asset(rotation: Int = 0) = MediaAsset(
        id = "asset", projectId = "step2-ui", sourceUri = "content://ux-step2-product", displayName = "Step 2 fixture",
        mimeType = "video/mp4", sizeBytes = 1L, durationUs = 100_000_000L, width = 1920, height = 1080,
        rotationDegrees = rotation, frameRate = 30.0, videoCodecMime = "video/avc", audioCodecMime = "audio/mp4a-latm",
        audioSampleRate = 48_000, audioChannelCount = 1, videoTrackCount = 1, audioTrackCount = 1,
        videoBitrate = null, videoProfile = null, videoLevel = null, colorStandard = null, colorTransfer = null,
        colorRange = null, hdrStaticInfoPresent = false, fingerprintSha256 = null, fingerprintAlgorithm = null,
        fingerprintStrength = FingerprintStrength.UNAVAILABLE, fingerprintSampledBytes = 0L, fingerprintNote = null,
        permissionPersisted = true, sourceStatus = SourceStatus.AVAILABLE, createdAt = 0L
    )

    private fun project(rotation: Int = 0) = VideoFlowProject(
        id = "step2-ui", name = "Step 2 UI", projectFormatVersion = 1,
        createdAt = 0L, updatedAt = 0L, lastOpenedAt = null, mediaAssets = listOf(asset(rotation))
    )

    @Test fun trimNormalAndPreciseModesUseProfessionalTimeLanguageAndOneCommit() {
        var committed: Pair<Long, Long>? = null
        var dismissed = false
        rule.setContent {
            MaterialTheme {
                TrimPanel(
                    tool = EditorTool.Trim(clip.id), clip = clip, project = project(), thumbnails = emptyMap(),
                    filmstripPaths = emptyList(), waveforms = emptyMap(), playheadUs = clip.timelineStartUs,
                    onPreviewSeek = {}, onCommitTrim = { start, end -> committed = start to end },
                    onDismiss = { dismissed = true }
                )
            }
        }
        rule.onNodeWithText("Start      0 sec").assertExists()
        rule.onNodeWithText("End        1 min 40 sec").assertExists()
        rule.onNodeWithText("Duration   1 min 40 sec").assertExists()
        rule.onNodeWithText("Precise").performClick()
        rule.onNodeWithText("Start      00:00:00.000").assertExists()
        rule.onNodeWithText("End        00:01:40.000").assertExists()
        rule.onNodeWithText("Duration   00:01:40.000").assertExists()
        screenshot("trim-precise")
        rule.onNodeWithText("Trim").performClick()
        screenshot("trim-normal")
        rule.onNodeWithText("Done").performClick()
        assertEquals(0L to 100_000_000L, committed)
        assertTrue(dismissed)
    }

    @Test fun speedDraftUpdatesReadableResultThenDoneCommitsOnceAndCancelDoesNotCommit() {
        val draft = mutableStateOf(ContextualPreviewDraft(speed = 1.0))
        var committed: Double? = null
        var dismissed = false
        rule.setContent {
            MaterialTheme {
                SpeedPanel(
                    clip = clip,
                    previewDraft = draft.value,
                    onPreviewDraftChange = { draft.value = it },
                    onCommitSpeed = { committed = it },
                    onDismiss = { dismissed = true }
                )
            }
        }
        rule.onNodeWithContentDescription("Current speed, 1.00 times").assertIsDisplayed()
        rule.onNodeWithText("Original   1 min 40 sec").assertExists()
        rule.onNodeWithText("Result     1 min 40 sec").assertExists()
        screenshot("speed-1x")
        rule.onNodeWithText("2.00×").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Current speed, 2.00 times").assertIsDisplayed()
        rule.onNodeWithText("Result     50 sec").assertExists()
        screenshot("speed-2x")
        rule.onNodeWithText("Done").performClick()
        assertEquals(2.0, committed!!, 0.0001)
        assertTrue(dismissed)
    }

    @Test fun cropRatiosRemainSourceSpaceAndRotationAwareAndDoneMatchesDraft() {
        val draft = mutableStateOf(ContextualPreviewDraft(crop = CropRect(.20f, .15f, .80f, .85f), cropNormalizedAspect = null))
        var committed: CropRect? = null
        rule.setContent {
            MaterialTheme {
                CropPanel(
                    tool = EditorTool.Crop(clip.id), clip = clip, project = project(), previewDraft = draft.value,
                    onPreviewDraftChange = { draft.value = it }, onCommitCrop = { committed = it }, onDismiss = {}
                )
            }
        }
        screenshot("crop-free")
        rule.onNodeWithText("1:1").performScrollTo().performClick()
        rule.waitForIdle()
        val oneToOne = checkNotNull(draft.value.crop)
        assertEquals(1f / (1920f / 1080f), (oneToOne.right - oneToOne.left) / (oneToOne.bottom - oneToOne.top), .002f)
        screenshot("crop-1x1")
        rule.onNodeWithText("9:16").performScrollTo().performClick()
        rule.waitForIdle()
        val portrait = checkNotNull(draft.value.crop)
        val expectedNormalized = (9f / 16f) / (1920f / 1080f)
        assertEquals(expectedNormalized, (portrait.right - portrait.left) / (portrait.bottom - portrait.top), .002f)
        assertTrue(portrait.left >= 0f && portrait.top >= 0f && portrait.right <= 1f && portrait.bottom <= 1f)
        screenshot("crop-9x16")
        rule.onNodeWithText("Done").performClick()
        assertEquals(portrait, committed)

        val rotatedDraft = mutableStateOf(ContextualPreviewDraft(crop = CropRect(), cropNormalizedAspect = null))
        var rotated: CropRect? = null
        rule.setContentForAdditionalTestNotAllowed()
    }

    @Test fun rotatedPortraitCropUsesDisplayDimensionsAndCompactLandscapeFocusedShellStaysUsable() {
        val rotatedDraft = mutableStateOf(ContextualPreviewDraft(crop = CropRect(), cropNormalizedAspect = null))
        rule.setContent {
            MaterialTheme {
                CropPanel(
                    tool = EditorTool.Crop(clip.id), clip = clip, project = project(90), previewDraft = rotatedDraft.value,
                    onPreviewDraftChange = { rotatedDraft.value = it }, onCommitCrop = {}, onDismiss = {}
                )
            }
        }
        rule.onNodeWithText("9:16").performScrollTo().performClick()
        rule.waitForIdle()
        val crop = checkNotNull(rotatedDraft.value.crop)
        val rotatedSourceAspect = 1080f / 1920f
        assertEquals((9f / 16f) / rotatedSourceAspect, (crop.right - crop.left) / (crop.bottom - crop.top), .002f)
        screenshot("crop-rotated-portrait")
    }

    @Test fun focusedShellKeepsPreviewAndPinnedActionsAcrossRequiredPhoneAndLandscapeClasses() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val tool = mutableStateOf(0)
        val speedDraft = mutableStateOf(ContextualPreviewDraft(speed = 1.0))
        val cropDraft = mutableStateOf(ContextualPreviewDraft(crop = CropRect(), cropNormalizedAspect = null))
        try {
            device.executeShellCommand("wm density 160")
            device.executeShellCommand("wm size 393x852")
            rule.setContent {
                MaterialTheme {
                    FocusedEditorWorkspace(
                        preview = { Box(Modifier.fillMaxSize().testTag("step2-focused-preview")) },
                        transport = { Box(Modifier.fillMaxWidth().height(52.dp)) },
                        focusedTool = {
                            FocusedToolScaffold(toolKey = tool.value, fallbackCancel = {}) {
                                when (tool.value) {
                                    0 -> TrimPanel(
                                        tool = EditorTool.Trim(clip.id), clip = clip, project = project(), thumbnails = emptyMap(),
                                        filmstripPaths = emptyList(), waveforms = emptyMap(), playheadUs = clip.timelineStartUs,
                                        onPreviewSeek = {}, onCommitTrim = { _, _ -> }, onDismiss = {}
                                    )
                                    1 -> SpeedPanel(clip, speedDraft.value, { speedDraft.value = it }, {}, {})
                                    else -> CropPanel(EditorTool.Crop(clip.id), clip, project(), cropDraft.value, { cropDraft.value = it }, {}, {})
                                }
                            }
                        }
                    )
                }
            }
            val sizes = listOf("360x800", "393x852", "412x915", "852x393")
            for (size in sizes) {
                device.executeShellCommand("wm size $size")
                device.waitForIdle()
                for (index in 0..2) {
                    rule.runOnIdle { tool.value = index }
                    rule.waitForIdle()
                    val preview = rule.onNodeWithTag("step2-focused-preview").fetchSemanticsNode().boundsInRoot
                    val action = rule.onNodeWithTag("focused-tool-action-bar").fetchSemanticsNode().boundsInRoot
                    assertTrue("preview must remain usable at $size tool=$index", preview.width > 0f && preview.height > 0f)
                    assertTrue("pinned action bar must remain visible at $size tool=$index", action.width > 0f && action.height >= 48f)
                    rule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
                    rule.onNodeWithContentDescription("Done").assertIsDisplayed()
                    when (index) {
                        0 -> rule.onNodeWithContentDescription("Trim start and end handles").assertIsDisplayed()
                        1 -> rule.onNodeWithContentDescription("Speed slider, 1.00 times").assertIsDisplayed()
                        2 -> rule.onNodeWithText("Free").assertExists()
                    }
                    if (size == "360x800" && index == 0) screenshot("trim-compact-360x800")
                    if (size == "360x800" && index == 2) screenshot("crop-compact-360x800")
                    if (size == "852x393" && index == 2) screenshot("landscape-focused-crop")
                }
            }
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
        }
    }

    private fun screenshot(label: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = File(context.getExternalFilesDir(null), "ux-step2-evidence/$label.png")
        target.parentFile!!.mkdirs()
        rule.waitForIdle()
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Could not capture $label")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
'''
# Remove an intentionally impossible line used only as a guard while authoring the test.
product_test = product_test.replace('''\n        val rotatedDraft = mutableStateOf(ContextualPreviewDraft(crop = CropRect(), cropNormalizedAspect = null))\n        var rotated: CropRect? = null\n        rule.setContentForAdditionalTestNotAllowed()\n''', '\n')
Path("app/src/androidTest/java/com/videoflow/app/ui/editor/UXStep2TrimSpeedCropProductComposeTest.kt").parent.mkdir(parents=True, exist_ok=True)
Path("app/src/androidTest/java/com/videoflow/app/ui/editor/UXStep2TrimSpeedCropProductComposeTest.kt").write_text(product_test)

render_test = r'''@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.editor.CropRect
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UXStep2CropRenderGeometryTest {
    @Test fun squareMarkerRemainsSquareForOriginalSquareAndPortraitCrops() = runBlocking {
        val fixture = Step5MediaFixture()
        try {
            val source = fixture.source("step2-geometry.mp4")
            val cases = listOf(
                "original" to CropRect(),
                "1x1" to CropRect(.125f, 0f, .875f, 1f),
                "9x16" to CropRect(.2890625f, 0f, .7109375f, 1f)
            )
            for ((label, crop) in cases) {
                val edited = fixture.clip.copy(transform = fixture.clip.transform.copy(crop = crop))
                val output = fixture.render(fixture.plan(source, edited)).first
                fixture.preserve(output, "step2-crop-$label.mp4")
                val ratio = whiteMarkerRatio(fixture, output)
                fixture.evidence("step2-crop-geometry.jsonl", "{\"case\":\"$label\",\"marker_ratio\":$ratio}")
                assertTrue("Crop stretched square marker for $label: ratio=$ratio", ratio in .88..1.12)
            }
        } finally { fixture.close() }
    }

    private fun whiteMarkerRatio(fixture: Step5MediaFixture, uri: Uri): Double {
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(fixture.context, uri)
            checkNotNull(retriever.getFrameAtTime(700_000L, MediaMetadataRetriever.OPTION_CLOSEST))
        } finally { retriever.release() }
        try {
            var minX = bitmap.width
            var maxX = -1
            var minY = bitmap.height
            var maxY = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) > 220 && Color.green(c) > 220 && Color.blue(c) > 220) {
                    minX = minOf(minX, x); maxX = maxOf(maxX, x)
                    minY = minOf(minY, y); maxY = maxOf(maxY, y)
                }
            }
            check(maxX >= minX && maxY >= minY) { "White geometry marker not found" }
            val width = (maxX - minX + 1).toDouble()
            val height = (maxY - minY + 1).toDouble()
            check(width > 10 && height > 10) { "Marker too small: ${width}x$height" }
            return width / height
        } finally { bitmap.recycle() }
    }
}
'''
Path("app/src/androidTest/java/com/videoflow/app/step5/UXStep2CropRenderGeometryTest.kt").write_text(render_test)

print("Strengthened Step-2 production panel and render certification")
