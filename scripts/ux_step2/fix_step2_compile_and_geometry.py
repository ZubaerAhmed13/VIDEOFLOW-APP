from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Compile correction: this codebase represents the full crop with CropRect().
replace_once(
    "app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt",
    "val crop = if (cropEditing) CropRect.FULL else activeVideoClip?.transform?.crop ?: CropRect.FULL",
    "val crop = if (cropEditing) CropRect() else activeVideoClip?.transform?.crop ?: CropRect()",
)

# Focused Trim must also keep duration-dependent fade bounds valid after shortening the clip.
replace_once(
    "app/src/main/java/com/videoflow/app/domain/editor/TimelineEngine.kt",
    "        require(end > start)\n        return clip.copy(sourceStartUs = start, sourceEndUs = end)",
    "        require(end > start)\n        val newDurationUs = timelineDurationUs(end - start, clip.speed)\n        return clip.copy(\n            sourceStartUs = start,\n            sourceEndUs = end,\n            fadeInUs = clip.fadeInUs.coerceAtMost(newDurationUs),\n            fadeOutUs = clip.fadeOutUs.coerceAtMost(newDurationUs)\n        )",
)

panels = Path("app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt")
text = panels.read_text()
old = "        update(centeredCrop(sw, sh, targetAspect), normalizedAspect)"
new = "        update(aspectCropAroundCurrentCenter(crop, sw, sh, targetAspect), normalizedAspect)"
if text.count(old) != 1:
    raise SystemExit(f"Expected one crop preset call, found {text.count(old)}")
text = text.replace(old, new, 1)

old = 'private fun formatMultiplier(value: Float): String = if (value % 1f == 0f) value.roundToInt().toString() else "%.2f".format(value).trimEnd(\'0\').trimEnd(\'.\')'
new = 'private fun formatMultiplier(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)'
if text.count(old) != 1:
    raise SystemExit(f"Expected one multiplier formatter, found {text.count(old)}")
text = text.replace(old, new, 1)

anchor = "private fun centeredCrop(sourceWidth: Int, sourceHeight: Int, targetAspect: Float): CropRect {"
if text.count(anchor) != 1:
    raise SystemExit("centeredCrop helper anchor not found")
helper = '''private fun aspectCropAroundCurrentCenter(current: CropRect, sourceWidth: Int, sourceHeight: Int, targetAspect: Float): CropRect {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(targetAspect.isFinite() && targetAspect > 0f)
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    val normalizedAspect = targetAspect / sourceAspect
    val width: Float
    val height: Float
    if (normalizedAspect >= 1f) {
        width = 1f
        height = (1f / normalizedAspect).coerceIn(0.0001f, 1f)
    } else {
        width = normalizedAspect.coerceIn(0.0001f, 1f)
        height = 1f
    }
    val centerX = ((current.left + current.right) / 2f).coerceIn(0f, 1f)
    val centerY = ((current.top + current.bottom) / 2f).coerceIn(0f, 1f)
    val left = (centerX - width / 2f).coerceIn(0f, 1f - width)
    val top = (centerY - height / 2f).coerceIn(0f, 1f - height)
    return CropRect(left, top, left + width, top + height)
}

'''
text = text.replace(anchor, helper + anchor, 1)
panels.write_text(text)

# Extend core tests for fade validity and crop-center-preserving preset math through observable invariants.
test_path = Path("app/src/test/java/com/videoflow/app/ui/editor/UXStep2TrimSpeedCropCoreTest.kt")
test = test_path.read_text()
old = '''    @Test fun focusedTrim_changesSourceRangeButKeepsTimelineAnchor() {
        val before = clip()
        val after = TimelineEngine.trimSourceRangeKeepingTimelineAnchor(before, 12_000_000L, 120_000_000L, 120_000_000L)
        assertEquals(7_000_000L, after.timelineStartUs)
        assertEquals(12_000_000L, after.sourceStartUs)
        assertEquals(120_000_000L, after.sourceEndUs)
        assertEquals(108_000_000L, after.timelineDurationUs)
    }
'''
new = '''    @Test fun focusedTrim_changesSourceRangeButKeepsTimelineAnchor() {
        val before = clip().copy(fadeInUs = 110_000_000L, fadeOutUs = 115_000_000L)
        val after = TimelineEngine.trimSourceRangeKeepingTimelineAnchor(before, 12_000_000L, 120_000_000L, 120_000_000L)
        assertEquals(7_000_000L, after.timelineStartUs)
        assertEquals(12_000_000L, after.sourceStartUs)
        assertEquals(120_000_000L, after.sourceEndUs)
        assertEquals(108_000_000L, after.timelineDurationUs)
        assertTrue(after.fadeInUs <= after.timelineDurationUs)
        assertTrue(after.fadeOutUs <= after.timelineDurationUs)
    }
'''
if test.count(old) != 1:
    raise SystemExit("Focused trim test anchor not found")
test_path.write_text(test.replace(old, new, 1))

print("Applied Step-2 compile/geometry correction")
