package com.videoflow.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Assert.assertEquals
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
        rule.onNodeWithText("Start      0 sec").assertIsDisplayed()
        rule.onNodeWithText("End        1 min 40 sec").assertIsDisplayed()
        rule.onNodeWithText("Duration   1 min 40 sec").assertIsDisplayed()
        rule.onNodeWithText("Precise").performClick()
        rule.onNodeWithContentDescription("Precise trim start")
            .assertTextContains("00:00:00.000")
        rule.onNodeWithContentDescription("Precise trim end")
            .assertTextContains("00:01:40.000")
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
        rule.onNodeWithText("Original   1 min 40 sec").assertIsDisplayed()
        rule.onNodeWithText("Result     1 min 40 sec").assertIsDisplayed()
        screenshot("speed-1x")
        rule.onNodeWithText("2.00×").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Current speed, 2.00 times").assertIsDisplayed()
        rule.onNodeWithText("Result     50 sec").assertIsDisplayed()
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
        rule.onNode(hasText("1:1") and hasClickAction()).performClick()
        rule.waitForIdle()
        var oneToOne: CropRect? = null
        rule.runOnIdle { oneToOne = draft.value.crop }
        val square = checkNotNull(oneToOne)
        assertEquals(1f / (1920f / 1080f), (square.right - square.left) / (square.bottom - square.top), .002f)
        screenshot("crop-1x1")
        rule.onNode(hasText("9:16") and hasClickAction()).performClick()
        rule.waitForIdle()
        var portraitDraft: CropRect? = null
        rule.runOnIdle { portraitDraft = draft.value.crop }
        val portrait = checkNotNull(portraitDraft)
        val expectedNormalized = (9f / 16f) / (1920f / 1080f)
        assertEquals(expectedNormalized, (portrait.right - portrait.left) / (portrait.bottom - portrait.top), .002f)
        assertTrue(portrait.left >= 0f && portrait.top >= 0f && portrait.right <= 1f && portrait.bottom <= 1f)
        screenshot("crop-9x16")
        rule.onNodeWithText("Done").performClick()
        assertEquals(portrait, committed)
    }

    @Test fun rotatedPortraitCropUsesDisplayDimensionsAndCompactLandscapeFocusedShellStaysUsable() {
        val rotatedDraft = mutableStateOf(ContextualPreviewDraft(crop = CropRect(.15f, .20f, .85f, .80f), cropNormalizedAspect = null))
        rule.setContent {
            MaterialTheme {
                CropPanel(
                    tool = EditorTool.Crop(clip.id), clip = clip, project = project(90), previewDraft = rotatedDraft.value,
                    onPreviewDraftChange = { rotatedDraft.value = it }, onCommitCrop = {}, onDismiss = {}
                )
            }
        }
        rule.onNode(hasText("1:1") and hasClickAction()).performClick()
        rule.waitForIdle()
        var rotatedCrop: CropRect? = null
        rule.runOnIdle { rotatedCrop = rotatedDraft.value.crop }
        val crop = checkNotNull(rotatedCrop)
        val rotatedSourceAspect = 1080f / 1920f
        assertEquals(1f / rotatedSourceAspect, (crop.right - crop.left) / (crop.bottom - crop.top), .002f)
        screenshot("crop-rotated-1x1")
    }

    @Test fun focusedShellKeepsPreviewAndPinnedActionsAt360x800() = exerciseFocusedShellAtSize("360x800")

    @Test fun focusedShellKeepsPreviewAndPinnedActionsAt393x852() = exerciseFocusedShellAtSize("393x852")

    @Test fun focusedShellKeepsPreviewAndPinnedActionsAt412x915() = exerciseFocusedShellAtSize("412x915")

    @Test fun focusedShellKeepsPreviewAndPinnedActionsAt852x393() = exerciseFocusedShellAtSize("852x393")

    private fun exerciseFocusedShellAtSize(size: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val tool = mutableStateOf(0)
        val speedDraft = mutableStateOf(ContextualPreviewDraft(speed = 1.0))
        val cropDraft = mutableStateOf(ContextualPreviewDraft(crop = CropRect(), cropNormalizedAspect = null))
        try {
            device.executeShellCommand("wm density 160")
            device.executeShellCommand("wm size $size")
            device.waitForIdle()
            instrumentation.waitForIdleSync()
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
                    2 -> rule.onNode(hasText("Free", substring = true) and hasClickAction()).performScrollTo().assertIsDisplayed()
                }
                if (size == "360x800" && index == 0) screenshot("trim-compact-360x800")
                if (size == "360x800" && index == 2) screenshot("crop-compact-360x800")
                if (size == "852x393" && index == 2) screenshot("landscape-focused-crop")
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
