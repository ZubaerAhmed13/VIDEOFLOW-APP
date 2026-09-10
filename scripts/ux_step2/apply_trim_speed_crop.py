#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")

def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected exactly one anchor, found {count}: {old[:100]!r}")
    write(rel, text.replace(old, new, 1))

def insert_before_once(rel, anchor, addition):
    text = read(rel)
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f"{rel}: expected one insertion anchor, found {count}: {anchor[:100]!r}")
    write(rel, text.replace(anchor, addition + anchor, 1))

# 1) Focused Trim primitive preserving timeline anchoring.
timeline = "app/src/main/java/com/videoflow/app/domain/editor/TimelineEngine.kt"
insert_before_once(timeline, "    fun trimEnd(", '''    /**
     * Focused-tool trim semantics: alter only the retained source range while keeping the clip's
     * timeline anchor stable. Direct timeline edge trim continues to use trimStart/trimEnd.
     */
    fun trimSourceRangeKeepingTimelineAnchor(
        clip: TimelineClip,
        newSourceStartUs: Long,
        newSourceEndUs: Long,
        assetDurationUs: Long
    ): TimelineClip {
        require(assetDurationUs > 0L)
        val start = newSourceStartUs.coerceIn(0L, assetDurationUs - 1L)
        val end = newSourceEndUs.coerceIn(start + 1L, assetDurationUs)
        require(end > start)
        return clip.copy(sourceStartUs = start, sourceEndUs = end)
    }

''')

repo = "app/src/main/java/com/videoflow/app/data/editor/EditorRepository.kt"
insert_before_once(repo, "    suspend fun splitClip(", '''    /**
     * Atomic focused Trim commit. Unlike direct timeline edge trimming, source trim-in does not
     * move timelineStartUs; the selected clip remains anchored where the user placed it.
     */
    suspend fun trimClipContentsKeepingTimelineAnchor(
        projectId: String,
        clipId: String,
        newSourceStartUs: Long,
        newSourceEndUs: Long
    ): TimelineClip = withContext(Dispatchers.IO) {
        val assetDuration = db.editorDao().getClips(projectId).first { it.id == clipId }.let { clip ->
            db.mediaAssetDao().get(clip.assetId)?.durationUs ?: error("Source duration unavailable")
        }
        mutateClip(projectId, clipId) { clip, track, clips, _ ->
            requireUnlocked(track)
            val trimmed = TimelineEngine.trimSourceRangeKeepingTimelineAnchor(
                clip = clip,
                newSourceStartUs = newSourceStartUs,
                newSourceEndUs = newSourceEndUs,
                assetDurationUs = assetDuration
            )
            ensureNoOverlap(trimmed, clips.filterNot { it.id == clip.id })
            trimmed
        }
    }

''')

vm = "app/src/main/java/com/videoflow/app/ui/ContextualEditingViewModel.kt"
old_trim_block = '''        val beforeProject = editorRepository.load(projectId)
        val before = beforeProject.timeline.clips.first { it.id == clipId }
        val beforeFrames = beforeProject.timeline.keyframes.filter { it.ownerId == clipId }
            // Mutate the boundary that expands the valid interval first. This prevents a
            // transient invalid clip when the requested range moves completely before/after the
            // previous source interval.
            if (sourceEndUs < before.sourceStartUs) {
                if (sourceStartUs != before.sourceStartUs) editorRepository.trimClipStart(projectId, clipId, sourceStartUs)
                if (sourceEndUs != before.sourceEndUs) editorRepository.trimClipEnd(projectId, clipId, sourceEndUs)
            } else {
                if (sourceEndUs != before.sourceEndUs) editorRepository.trimClipEnd(projectId, clipId, sourceEndUs)
                if (sourceStartUs != before.sourceStartUs) editorRepository.trimClipStart(projectId, clipId, sourceStartUs)
            }
        val fresh = editorRepository.load(projectId)
'''
new_trim_block = '''        val beforeProject = editorRepository.load(projectId)
        val before = beforeProject.timeline.clips.first { it.id == clipId }
        val beforeFrames = beforeProject.timeline.keyframes.filter { it.ownerId == clipId }
        editorRepository.trimClipContentsKeepingTimelineAnchor(
            projectId = projectId,
            clipId = clipId,
            newSourceStartUs = sourceStartUs,
            newSourceEndUs = sourceEndUs
        )
        val fresh = editorRepository.load(projectId)
'''
replace_once(vm, old_trim_block, new_trim_block)

# 2) Human-readable time, preserving exact timecode for Precise Trim.
fmt = "app/src/main/java/com/videoflow/app/util/Formatters.kt"
text = read(fmt)
if "fun formatHumanDurationUs" not in text:
    text += r'''

fun formatHumanDurationUs(us: Long?): String {
    if (us == null) return "Unknown"
    val safe = us.coerceAtLeast(0L)
    if (safe < 60_000_000L) {
        val tenths = ((safe + 50_000L) / 100_000L) / 10.0
        return if (tenths % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f sec", tenths)
        } else {
            String.format(Locale.US, "%.1f sec", tenths)
        }
    }
    val totalSeconds = (safe + 500_000L) / 1_000_000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        if (seconds == 0L) String.format(Locale.US, "%d hr %02d min", hours, minutes)
        else String.format(Locale.US, "%d hr %02d min %02d sec", hours, minutes, seconds)
    } else String.format(Locale.US, "%d min %02d sec", minutes, seconds)
}
'''
    write(fmt, text)

# 3) Pure Speed and Crop preview math.
math = "app/src/main/java/com/videoflow/app/ui/editor/ContextualEditingMath.kt"
text = read(math)
if "speedAdjustedDurationUs" not in text:
    text += r'''

internal fun speedAdjustedDurationUs(sourceDurationUs: Long, speed: Double): Long {
    require(sourceDurationUs >= 0L)
    require(speed.isFinite() && speed > 0.0)
    return (sourceDurationUs.toDouble() / speed).toLong().coerceAtLeast(0L)
}

internal data class UniformCropPreviewGeometry(
    val scale: Float,
    val contentWidthFraction: Float,
    val contentHeightFraction: Float
)

internal fun uniformCropPreviewGeometry(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    crop: com.videoflow.app.domain.editor.CropRect
): UniformCropPreviewGeometry {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(viewportWidth > 0f && viewportHeight > 0f)
    val sw = sourceWidth.toFloat()
    val sh = sourceHeight.toFloat()
    val fullFit = minOf(viewportWidth / sw, viewportHeight / sh)
    val cropWidth = (crop.right - crop.left).coerceAtLeast(0.0001f)
    val cropHeight = (crop.bottom - crop.top).coerceAtLeast(0.0001f)
    val croppedFit = minOf(viewportWidth / (sw * cropWidth), viewportHeight / (sh * cropHeight))
    val fullRenderedWidth = sw * fullFit
    val fullRenderedHeight = sh * fullFit
    return UniformCropPreviewGeometry(
        scale = (croppedFit / fullFit).coerceAtLeast(1f),
        contentWidthFraction = (fullRenderedWidth / viewportWidth).coerceIn(0f, 1f),
        contentHeightFraction = (fullRenderedHeight / viewportHeight).coerceIn(0f, 1f)
    )
}
'''
    write(math, text)

# 4) Draft Speed state + professional Trim/Speed controls.
models = "app/src/main/java/com/videoflow/app/ui/editor/EditorWorkspaceModels.kt"
replace_once(models, '''    val cropNormalizedAspect: Float? = null,
    val transform: PreviewTransformDraft? = null,''', '''    val cropNormalizedAspect: Float? = null,
    val speed: Double? = null,
    val transform: PreviewTransformDraft? = null,''')

screen = "app/src/main/java/com/videoflow/app/ui/screens/EditorScreen.kt"
replace_once(screen, '''        previewDraft = when (tool) {
            is EditorTool.Crop -> clips.firstOrNull { it.id == tool.clipId }?.let { ContextualPreviewDraft(crop = it.transform.crop) } ?: ContextualPreviewDraft()
            is EditorTool.Volume ->''', '''        previewDraft = when (tool) {
            is EditorTool.Crop -> clips.firstOrNull { it.id == tool.clipId }?.let { ContextualPreviewDraft(crop = it.transform.crop) } ?: ContextualPreviewDraft()
            is EditorTool.Speed -> clips.firstOrNull { it.id == tool.clipId }?.let { ContextualPreviewDraft(speed = it.speed) } ?: ContextualPreviewDraft()
            is EditorTool.Volume ->''')

panels = "app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt"
replace_once(panels, "import com.videoflow.app.util.formatDurationUs\n", "import com.videoflow.app.util.formatDurationUs\nimport com.videoflow.app.util.formatHumanDurationUs\n")
replace_once(panels, "value = editorVm.sampleTrimFilmstrip(clip.assetId, clip.sourceStartUs, clip.sourceEndUs, 8)", "value = editorVm.sampleTrimFilmstrip(clip.assetId, clip.sourceStartUs, clip.sourceEndUs, 10)")
replace_once(panels, '''            is EditorTool.Speed -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip -> SpeedPanel(clip, editorVm, onDismiss) }''', '''            is EditorTool.Speed -> timeline.clips.firstOrNull { it.id == tool.clipId }?.let { clip ->
                SpeedPanel(clip, previewDraft, onPreviewDraftChange, editorVm, onDismiss)
            }''')
replace_once(panels, '''    ToolHeader("Trim", if (preciseMode) "Precise — exact Long-microsecond boundaries" else if (isAudio) "Drag the waveform handles" else "Drag the trim handles")''', '''    ToolHeader("Trim", if (preciseMode) "Precise timecode" else if (isAudio) "Drag the waveform handles" else "Drag the trim handles")''')
replace_once(panels, '''        Text("Start      ${TrimTimecode.formatUs(draftStartUs)}")
        Text("End        ${TrimTimecode.formatUs(draftEndUs)}")
        Text("Duration   ${TrimTimecode.formatUs(((draftEndUs - draftStartUs).toDouble() / clip.speed).roundToLong().coerceAtLeast(0L))}")''', '''        val visibleDurationUs = speedAdjustedDurationUs(draftEndUs - draftStartUs, clip.speed)
        if (preciseMode) {
            Text("Start      ${TrimTimecode.formatUs(draftStartUs)}")
            Text("End        ${TrimTimecode.formatUs(draftEndUs)}")
            Text("Duration   ${TrimTimecode.formatUs(visibleDurationUs)}")
        } else {
            Text("Start      ${formatHumanDurationUs(draftStartUs)}")
            Text("End        ${formatHumanDurationUs(draftEndUs)}")
            Text("Duration   ${formatHumanDurationUs(visibleDurationUs)}")
        }''')

speed_re = re.compile(r'''@Composable\nprivate fun SpeedPanel\(clip: TimelineClip, editorVm: EditorViewModel, onDismiss: \(\) -> Unit\) \{.*?\n\}\n\n@Composable\nprivate fun CropPanel\(''', re.S)
text = read(panels)
m = speed_re.search(text)
if not m: raise SystemExit("SpeedPanel block not found")
new_speed = r'''@Composable
private fun SpeedPanel(
    clip: TimelineClip,
    previewDraft: ContextualPreviewDraft,
    onPreviewDraftChange: (ContextualPreviewDraft) -> Unit,
    editorVm: EditorViewModel,
    onDismiss: () -> Unit
) {
    var speed by remember(clip.id) { mutableFloatStateOf((previewDraft.speed ?: clip.speed).toFloat()) }
    fun updateSpeed(value: Float) {
        speed = value.coerceIn(0.25f, 4f)
        onPreviewDraftChange(previewDraft.copy(speed = speed.toDouble()))
    }
    ToolHeader("Speed")
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${formatMultiplier(speed)}×", fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = "Current speed, ${formatMultiplier(speed)} times" })
        Slider(value = speed, onValueChange = ::updateSpeed, valueRange = 0.25f..4f,
            modifier = Modifier.semantics { contentDescription = "Speed slider, ${formatMultiplier(speed)} times" })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f).forEach { preset ->
                OutlinedButton(onClick = { updateSpeed(preset) }) {
                    Text(if (kotlin.math.abs(speed - preset) < 0.001f) "✓ ${formatMultiplier(preset)}×" else "${formatMultiplier(preset)}×")
                }
            }
        }
        val originalDurationUs = clip.sourceDurationUs
        val resultDurationUs = speedAdjustedDurationUs(originalDurationUs, speed.toDouble())
        Text("Original   ${formatHumanDurationUs(originalDurationUs)}")
        Text("Result     ${formatHumanDurationUs(resultDurationUs)}")
    }
    ActionRow(
        onCancel = onDismiss,
        onReset = { updateSpeed(1f) },
        onDone = {
            editorVm.selectClip(clip.id)
            editorVm.setSpeed(speed.toDouble())
            onDismiss()
        }
    )
}

@Composable
private fun CropPanel('''
text = text[:m.start()] + new_speed + text[m.end():]
write(panels, text)
replace_once(panels, '''            OutlinedButton(onClick = { update(CropRect(), null) }) { Text("Original") }''', '''            OutlinedButton(onClick = { update(CropRect(), 1f) }) { Text(if (previewDraft.cropNormalizedAspect == 1f && crop == CropRect()) "✓ Original" else "Original") }''')

# 5) Preview draft speed + full-source Crop editing + one uniform crop scale.
preview = "app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt"
replace_once(preview, '''    val activeLocalUs = activeVideoClip?.let { (playheadUs - it.timelineStartUs).coerceAtLeast(0L) } ?: 0L
    val activeFrames = activeVideoClip?.let { clip -> keyframes.filter { it.ownerId == clip.id } }.orEmpty()
    val sourcePositionMs = activeVideoClip?.let { clip ->
        ((clip.sourceStartUs + activeLocalUs * clip.speed) / 1000.0).toLong()
    } ?: 0L
''', '''    val activeLocalUs = activeVideoClip?.let { (playheadUs - it.timelineStartUs).coerceAtLeast(0L) } ?: 0L
    val activeFrames = activeVideoClip?.let { clip -> keyframes.filter { it.ownerId == clip.id } }.orEmpty()
    val effectivePreviewSpeed = activeVideoClip?.let { clip ->
        (activeTool as? EditorTool.Speed)?.takeIf { it.clipId == clip.id }?.let { previewDraft.speed } ?: clip.speed
    } ?: 1.0
    val sourcePositionMs = activeVideoClip?.let { clip ->
        val sourceUs = (clip.sourceStartUs + activeLocalUs.toDouble() * effectivePreviewSpeed).toLong()
            .coerceIn(clip.sourceStartUs, (clip.sourceEndUs - 1L).coerceAtLeast(clip.sourceStartUs))
        sourceUs / 1_000L
    } ?: 0L
''')
replace_once(preview, '''                    val cropDraft = (activeTool as? EditorTool.Crop)
                        ?.takeIf { it.clipId == activeVideoClip?.id }
                        ?.let { previewDraft.crop }
                    val crop = cropDraft ?: activeVideoClip?.transform?.crop
                    val cropWidth = (crop?.right?.minus(crop.left) ?: 1f).coerceAtLeast(0.01f)
                    val cropHeight = (crop?.bottom?.minus(crop.top) ?: 1f).coerceAtLeast(0.01f)
                    val cropCenterX = ((crop?.left ?: 0f) + (crop?.right ?: 1f)) / 2f
                    val cropCenterY = ((crop?.top ?: 0f) + (crop?.bottom ?: 1f)) / 2f
''', '''                    val cropEditing = (activeTool as? EditorTool.Crop)?.takeIf { it.clipId == activeVideoClip?.id } != null
                    val crop = if (cropEditing) CropRect.FULL else activeVideoClip?.transform?.crop ?: CropRect.FULL
                    val cropCenterX = (crop.left + crop.right) / 2f
                    val cropCenterY = (crop.top + crop.bottom) / 2f
                    val displaySize = activeAsset?.let { asset ->
                        val w = asset.width
                        val h = asset.height
                        if (w != null && h != null && w > 0 && h > 0) displayDimensionsForRotation(w, h, asset.rotationDegrees) else null
                    }
                    val cropGeometry = displaySize?.let { (sw, sh) ->
                        uniformCropPreviewGeometry(sw, sh, maxWidth.value.coerceAtLeast(1f), maxHeight.value.coerceAtLeast(1f), crop)
                    } ?: UniformCropPreviewGeometry(1f, 1f, 1f)
''')
replace_once(preview, '''                        speed = activeVideoClip?.speed?.toFloat() ?: 1f,''', '''                        speed = effectivePreviewSpeed.toFloat(),''')
replace_once(preview, '''                                scaleX = (t.scaleX / cropWidth) * if (t.flipHorizontal) -1f else 1f
                                scaleY = (t.scaleY / cropHeight) * if (t.flipVertical) -1f else 1f
                                rotationZ = t.rotation
                                alpha = t.opacity.coerceIn(0f, 1f)
                                translationX = (0.5f - cropCenterX) * size.width / cropWidth
                                translationY = (0.5f - cropCenterY) * size.height / cropHeight
''', '''                                val cropScale = cropGeometry.scale
                                scaleX = (t.scaleX * cropScale) * if (t.flipHorizontal) -1f else 1f
                                scaleY = (t.scaleY * cropScale) * if (t.flipVertical) -1f else 1f
                                rotationZ = t.rotation
                                alpha = t.opacity.coerceIn(0f, 1f)
                                translationX = (0.5f - cropCenterX) * size.width * cropGeometry.contentWidthFraction * cropScale
                                translationY = (0.5f - cropCenterY) * size.height * cropGeometry.contentHeightFraction * cropScale
''')
replace_once(preview, '''                        CropInteractionOverlay(
                            crop = previewDraft.crop ?: target.transform.crop,
                            aspectRatio = previewDraft.cropNormalizedAspect,
                            onCropChange = onCropChange,
                            onCropCommit = onCropCommit,
                            modifier = Modifier.fillMaxSize()
                        )
''', '''                        val asset = project?.mediaAssets?.firstOrNull { it.id == target.assetId }
                        val cropModifier = asset?.let { source ->
                            val w = source.width
                            val h = source.height
                            if (w != null && h != null && w > 0 && h > 0) {
                                val (displayW, displayH) = displayDimensionsForRotation(w, h, source.rotationDegrees)
                                val sourceAspect = displayW.toFloat() / displayH.toFloat()
                                val viewportAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else sourceAspect
                                if (viewportAspect > sourceAspect) Modifier.align(Alignment.Center).height(maxHeight).width(maxHeight * sourceAspect)
                                else Modifier.align(Alignment.Center).width(maxWidth).height(maxWidth / sourceAspect)
                            } else Modifier.fillMaxSize()
                        } ?: Modifier.fillMaxSize()
                        CropInteractionOverlay(
                            crop = previewDraft.crop ?: target.transform.crop,
                            aspectRatio = previewDraft.cropNormalizedAspect,
                            onCropChange = onCropChange,
                            onCropCommit = onCropCommit,
                            modifier = cropModifier
                        )
''')

# 6) Crop accessibility.
interaction = "app/src/main/java/com/videoflow/app/ui/editor/PreviewInteraction.kt"
text = read(interaction)
if "import androidx.compose.ui.semantics.contentDescription" not in text:
    text = text.replace("import androidx.compose.ui.input.pointer.pointerInput\n", "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.semantics.contentDescription\nimport androidx.compose.ui.semantics.semantics\n")
anchor = '''    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(crop, aspectRatio) {
'''
if anchor not in text: raise SystemExit("Crop overlay Box anchor not found")
text = text.replace(anchor, '''    Box(
        modifier = modifier
            .clipToBounds()
            .semantics { contentDescription = "Crop rectangle. Drag to move; drag edges or corners to resize." }
            .pointerInput(crop, aspectRatio) {
''', 1)
write(interaction, text)

# 7) JVM hard-invariant tests.
test_path = "app/src/test/java/com/videoflow/app/ui/editor/UXStep2TrimSpeedCropCoreTest.kt"
write(test_path, r'''package com.videoflow.app.ui.editor

import com.videoflow.app.domain.editor.CropRect
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TimelineEngine
import com.videoflow.app.util.formatHumanDurationUs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UXStep2TrimSpeedCropCoreTest {
    private fun clip() = TimelineClip(id="clip", projectId="project", trackId="track", assetId="asset", timelineStartUs=7_000_000L, sourceStartUs=0L, sourceEndUs=120_000_000L)

    @Test fun focusedTrim_changesSourceRangeButKeepsTimelineAnchor() {
        val before = clip()
        val after = TimelineEngine.trimSourceRangeKeepingTimelineAnchor(before, 12_000_000L, 120_000_000L, 120_000_000L)
        assertEquals(7_000_000L, after.timelineStartUs)
        assertEquals(12_000_000L, after.sourceStartUs)
        assertEquals(120_000_000L, after.sourceEndUs)
        assertEquals(108_000_000L, after.timelineDurationUs)
    }

    @Test fun directTimelineTrimStart_keepsItsExistingEdgeTrimSemantics() {
        val before = clip()
        assertTrue(TimelineEngine.trimStart(before, 12_000_000L).timelineStartUs > before.timelineStartUs)
    }

    @Test fun humanDuration_isReadableAndSpeedMathStaysLongBased() {
        assertEquals("12.6 sec", formatHumanDurationUs(12_600_000L))
        assertEquals("1 min 39 sec", formatHumanDurationUs(99_000_000L))
        assertEquals(49_500_000L, speedAdjustedDurationUs(99_000_000L, 2.0))
        assertEquals(198_000_000L, speedAdjustedDurationUs(99_000_000L, 0.5))
    }

    @Test fun cropGeometry_usesOneUniformScale() {
        val full = uniformCropPreviewGeometry(1920,1080,393f,221f,CropRect(0f,0f,1f,1f))
        val portrait = uniformCropPreviewGeometry(1920,1080,393f,221f,CropRect(0.342f,0f,0.658f,1f))
        assertEquals(1f, full.scale, 0.001f)
        assertTrue(portrait.scale > 1f)
        assertTrue(portrait.contentWidthFraction > 0f && portrait.contentHeightFraction > 0f)
    }

    @Test fun rotationAwareDisplayDimensions_areCorrect() {
        assertEquals(1920 to 1080, displayDimensionsForRotation(1920,1080,0))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920,1080,90))
        assertEquals(1080 to 1920, displayDimensionsForRotation(1920,1080,270))
    }
}
''')

# 8) Dedicated Step-2 certification workflow.
cert = ".github/workflows/android-ux-step2-trim-speed-crop-certification.yml"
write(cert, r'''name: VideoFlow UX Step 2 Trim Speed Crop Certification

on:
  push:
    branches: [ux-step2-trim-speed-crop]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ux-step2-trim-speed-crop-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-test-source-audit:
    name: Source scope, compile, unit, lint, androidTest and APK
    runs-on: ubuntu-latest
    timeout-minutes: 80
    steps:
      - uses: actions/checkout@v4
        with: {fetch-depth: 0}
      - uses: actions/setup-java@v5
        with: {distribution: temurin, java-version: '17'}
      - uses: android-actions/setup-android@v3
        with: {cmdline-tools-version: 15859902, packages: platform-tools}
      - name: Install Android API 37 platform and build tools
        shell: bash
        run: |
          set -euo pipefail
          PLATFORM_URL='https://dl.google.com/android/repository/platform-37.0_r02.zip'
          PLATFORM_SHA1='ed8ebf7f8822a4de5686d427f237d2fa30ff7410'
          curl -fL --retry 4 --retry-delay 2 "$PLATFORM_URL" -o /tmp/platform-37.0_r02.zip
          echo "$PLATFORM_SHA1  /tmp/platform-37.0_r02.zip" | sha1sum -c -
          rm -rf "$ANDROID_HOME/platforms/android-37.0"; mkdir -p "$ANDROID_HOME/platforms"
          unzip -q /tmp/platform-37.0_r02.zip -d "$ANDROID_HOME/platforms"
          sdkmanager 'build-tools;36.0.0'
      - uses: gradle/actions/setup-gradle@v4
        with: {gradle-version: '9.6.0'}
      - name: Bootstrap wrapper
        run: gradle wrapper --gradle-version 9.6.0 && chmod +x gradlew
      - name: Step 2 source-scope and hard invariant audit
        shell: bash
        run: |
          set -euo pipefail
          BASE='0123b734f687dfdbf0589b6040aeb4d8642c5968'
          test "$(git merge-base "$BASE" HEAD)" = "$BASE"
          git diff --name-only "$BASE"...HEAD | tee /tmp/ux-step2-files.txt
          ! grep -E '^app/src/main/java/com/videoflow/app/(ai|data/proxy|domain/ai)/' /tmp/ux-step2-files.txt
          grep -q 'trimSourceRangeKeepingTimelineAnchor' app/src/main/java/com/videoflow/app/domain/editor/TimelineEngine.kt
          grep -q 'trimClipContentsKeepingTimelineAnchor' app/src/main/java/com/videoflow/app/data/editor/EditorRepository.kt
          grep -q 'formatHumanDurationUs' app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt
          grep -q 'speedAdjustedDurationUs' app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt
          grep -q 'effectivePreviewSpeed' app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt
          grep -q 'uniformCropPreviewGeometry' app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt
          ! grep -q 't.scaleX / cropWidth' app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt
          ! grep -q 't.scaleY / cropHeight' app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt
          grep -q 'Crop(' app/src/main/java/com/videoflow/app/render/Media3CompositionBuilder.kt
          grep -q 'return factor to factor' app/src/main/java/com/videoflow/app/render/Media3CompositionBuilder.kt
      - name: Unit tests
        run: ./gradlew test --stacktrace
      - name: Lint
        run: ./gradlew lint --stacktrace
      - name: Compile instrumentation
        run: ./gradlew compileDebugAndroidTestKotlin --stacktrace
      - name: Assemble runtime
        run: ./gradlew assembleDebug assembleDebugAndroidTest --stacktrace
      - name: Package exact-head runtime
        run: |
          mkdir -p ux-step2-runtime
          cp app/build/outputs/apk/debug/app-debug.apk ux-step2-runtime/VideoFlow_UXStep2_Debug.apk
          cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk ux-step2-runtime/VideoFlow_UXStep2_Debug-androidTest.apk
          git rev-parse HEAD > ux-step2-runtime/EXACT_HEAD.txt
          (cd ux-step2-runtime && sha256sum *.apk > SHA256SUMS.txt)
      - uses: actions/upload-artifact@v4
        with: {name: VideoFlow-UX-Step2-Runtime-Bundle, path: ux-step2-runtime/, if-no-files-found: error, retention-days: 30}

  api35-product-regression:
    name: API 35 Step 1 regression and Step 2 product certification
    needs: build-test-source-audit
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      - name: Reclaim disk
        run: |
          sudo rm -rf /usr/share/dotnet /opt/ghc /usr/local/share/boost || true
          sudo rm -rf "$ANDROID_HOME/ndk" "$ANDROID_HOME/cmake" "$ANDROID_HOME/system-images" "$ANDROID_HOME/platforms" "$ANDROID_HOME/build-tools" "$ANDROID_HOME/sources" "$ANDROID_HOME/extras" || true
          sudo apt-get clean || true
      - uses: android-actions/setup-android@v3
        with: {cmdline-tools-version: 15859902, packages: platform-tools}
      - uses: actions/download-artifact@v4
        with: {name: VideoFlow-UX-Step2-Runtime-Bundle, path: ux-step2-runtime}
      - name: Verify runtime integrity
        run: (cd ux-step2-runtime && sha256sum -c SHA256SUMS.txt)
      - name: Enable KVM
        run: test -e /dev/kvm && sudo chown "$USER":"$(id -gn)" /dev/kvm && sudo chmod 660 /dev/kvm
      - name: Run API 35 production editor regression
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 35
          target: google_apis
          arch: x86_64
          disable-animations: true
          disable-linux-hw-accel: false
          emulator-boot-timeout: 600
          disk-size: 1536M
          script: |
            set -e
            mkdir -p ux-step2-emulator-reports ux-step2-evidence
            adb install -r ux-step2-runtime/VideoFlow_UXStep2_Debug.apk
            adb install -r ux-step2-runtime/VideoFlow_UXStep2_Debug-androidTest.apk
            adb shell am instrument -w -r -e class 'com.videoflow.app.ui.UXStep1EditorShellComposeTest,com.videoflow.app.ui.UXStep1FontScaleComposeTest,com.videoflow.app.ui.EditorWorkspaceVisualCertificationTest,com.videoflow.app.ui.TrimOpenGeometryComposeTest,com.videoflow.app.ui.Step2ComplexPersistenceTest,com.videoflow.app.render.NativeRenderEngineInstrumentedTest' com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner > ux-step2-emulator-reports/instrumentation-output.txt 2>&1
            cat ux-step2-emulator-reports/instrumentation-output.txt
            ! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' ux-step2-emulator-reports/instrumentation-output.txt
            grep -E -q 'OK \([0-9]+ tests?\)' ux-step2-emulator-reports/instrumentation-output.txt
            adb exec-out run-as com.videoflow.app.debug sh -c 'cd files && tar -cf - ux-step1-evidence 2>/dev/null' | tar -xf - -C ux-step2-evidence || true
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: VideoFlow-UX-Step2-API35-Evidence
          path: |
            ux-step2-emulator-reports/
            ux-step2-evidence/
          if-no-files-found: warn
          retention-days: 30
''')

# 9) Truthful completion report; final CI metadata remains pending until exact-head run.
write("UX_STEP_2_TRIM_SPEED_CROP_COMPLETION_REPORT.md", r'''# UX Step 2 — Trim / Speed / Crop Completion Report

## Baseline
- Starting branch: `ux-step1-editor-shell-layout`
- Starting SHA: `0123b734f687dfdbf0589b6040aeb4d8642c5968`
- Working branch: `ux-step2-trim-speed-crop`
- Architecture reused: Step-1 Main Editor / Focused Tool workspace, pinned Cancel/Done action bar, safe-area handling, responsive shell, bounded timeline and track scrolling.

## Trim
Previous focused Trim committed through the shared direct-edge `trimClipStart`, which advances `timelineStartUs` as source trim-in moves. Corrected focused Trim uses `TimelineEngine.trimSourceRangeKeepingTimelineAnchor` through one atomic repository mutation: `sourceStartUs/sourceEndUs` change while `timelineStartUs` remains fixed. Direct timeline edge trim keeps its existing semantics. Normal labels are human-readable; `Precise` remains inside Trim with exact timecode. Filmstrip remains real, bounded, cached sampling and requests ten samples.

## Speed
The existing 0.25×–4× range is retained. Common presets include 0.5×, 0.75×, 1×, 1.25×, 1.5× and 2×. Original/Result durations are human-readable. Long-microsecond source duration remains authoritative. Slider/preset changes update transient preview speed; only Done persists. Audio/export keeps the existing Media3 speed path.

## Crop
Root cause: the old Compose preview divided X by crop width and Y by crop height independently. Corrected preview uses one uniform crop scale and source-content-aware translation. During Crop, the full undistorted source is shown beneath the normalized crop overlay. The interaction surface is fitted to the rotated source display rect, excluding letterbox/pillarbox bars. Original/Free and required ratio presets remain available. Final export retains Media3 `Crop` and uniform `aspectFitScale`.

## State / Undo / Persistence
Trim/Speed/Crop use focused draft sessions. Cancel does not persist drafts. Done persists once. Focused Trim now performs one atomic source-range mutation before one history entry. Existing Speed/Crop history and Room persistence paths remain in place.

## Tests / CI
Added `UXStep2TrimSpeedCropCoreTest` covering focused Trim anchoring, direct-edge non-regression, human duration, speed arithmetic, uniform crop geometry, and rotation-aware dimensions.
Dedicated workflow: `VideoFlow UX Step 2 Trim Speed Crop Certification`.

## Files changed
Use `git diff 0123b734f687dfdbf0589b6040aeb4d8642c5968...HEAD --name-only` as the authoritative list.

## Certification
- Step-2 CI run ID: **PENDING exact-head workflow**
- Exact certified SHA: **PENDING exact-head workflow**
- Step-1 regression: **PENDING exact-head workflow**
- Physical-device certification: **NOT RUN**

## Deferrals
- Effects / Enhance UX: deferred to Step 3.
- AI Watermark UX: deferred to Step 4.
- Final whole-app UX certification: NOT RUN.
- Do not proceed automatically to Step 3.
''')

print("UX Step 2 deterministic patch applied.")
