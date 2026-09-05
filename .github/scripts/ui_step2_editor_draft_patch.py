from pathlib import Path

# Temporary one-shot audit patch. Removed before the final certified SHA.
p = Path('.github/workflows/android-ui-step2-ci.yml')
s = p.read_text()

s = s.replace("          PREVIEW='app/src/main/java/com/videoflow/app/ui/editor/PreviewInteraction.kt'\n", "          PREVIEW='app/src/main/java/com/videoflow/app/ui/editor/PreviewInteraction.kt'\n          WORKSPACE='app/src/main/java/com/videoflow/app/ui/editor/TimelineWorkspace.kt'\n          CONTEXTUAL='app/src/main/java/com/videoflow/app/ui/ContextualEditingViewModel.kt'\n", 1)
s = s.replace('          test -s "$PREVIEW"\n', '          test -s "$PREVIEW"\n          test -s "$WORKSPACE"\n          test -s "$CONTEXTUAL"\n', 1)
s = s.replace("          grep -q 'detectTransformGestures' \"$PREVIEW\"\n", "          grep -q 'awaitEachGesture' \"$PREVIEW\"\n", 1)
anchor = "          grep -q 'ContextualToolHost' \"$SCREEN\"\n"
extra = anchor + '''          grep -q 'ContextualPreviewDraft' "$MODELS"
          grep -q 'PreviewTransformDraft' "$MODELS"
          grep -q 'previewDraft = previewDraft.copy' "$SCREEN"
          grep -q 'addUniformScaleKeyframe' "$CONTEXTUAL"
          grep -q 'TimelineTrimHandle' "$WORKSPACE"
          grep -q 'dispatchRawDelta' "$WORKSPACE"
          grep -q 'ownerDurationUs' "$WORKSPACE"
          grep -q 'Silence (-60 dB)' "$TOOLS"
          ! grep -q 'ModalBottomSheet' "$TOOLS"
          ! grep -Fq 'onClick = { }' "$TOOLS"
          ! grep -q 'contextualVm.setClipCrop(id, tool.clipId, crop)' "$SCREEN"
'''
if anchor not in s:
    raise SystemExit('audit anchor missing')
s = s.replace(anchor, extra, 1)
p.write_text(s)
