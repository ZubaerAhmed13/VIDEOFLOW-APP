from pathlib import Path
import re

p = Path('app/src/main/java/com/videoflow/app/ui/screens/EditorScreen.kt')
s = p.read_text()

def add_after(anchor: str, text: str):
    global s
    if anchor not in s:
        raise SystemExit(f'missing anchor: {anchor[:50]}')
    s = s.replace(anchor, anchor + text, 1)

add_after('import com.videoflow.app.domain.editor.ImageOverlay\n', 'import com.videoflow.app.domain.editor.KeyframeEvaluator\nimport com.videoflow.app.domain.editor.KeyframeProperty\n')
add_after('import com.videoflow.app.ui.editor.ContextualToolHost\n', 'import com.videoflow.app.ui.editor.ContextualPreviewDraft\n')
add_after('import com.videoflow.app.ui.editor.PreviewWorkspace\n', 'import com.videoflow.app.ui.editor.PreviewTextStyleDraft\nimport com.videoflow.app.ui.editor.PreviewTransformDraft\n')
add_after('import kotlinx.coroutines.delay\n', 'import kotlin.math.abs\n')
add_after('    var activeTool by remember { mutableStateOf<EditorTool?>(null) }\n', '    var previewDraft by remember { mutableStateOf(ContextualPreviewDraft()) }\n')
s = s.replace('        activeTool = null\n        initialToolClip = null', '        activeTool = null\n        previewDraft = ContextualPreviewDraft()\n        initialToolClip = null', 1)
s = s.replace('        activeTool = tool\n        initialToolClip = null', '        activeTool = tool\n        previewDraft = ContextualPreviewDraft()\n        initialToolClip = null', 1)

draft = '''        fun evaluated(ownerId: String, property: KeyframeProperty, base: Float, startUs: Long, durationUs: Long): Float {
            val localUs = (playheadUs - startUs).coerceIn(0L, durationUs.coerceAtLeast(1L))
            return KeyframeEvaluator.evaluate(base, localUs, timeline?.keyframes.orEmpty().filter { it.ownerId == ownerId && it.property == property })
        }
        previewDraft = when (tool) {
            is EditorTool.Crop -> clips.firstOrNull { it.id == tool.clipId }?.let { ContextualPreviewDraft(crop = it.transform.crop) } ?: ContextualPreviewDraft()
            is EditorTool.Volume -> clips.firstOrNull { it.id == tool.clipId }?.let { clip -> ContextualPreviewDraft(gainDb = evaluated(clip.id, KeyframeProperty.AUDIO_GAIN, clip.gainDb, clip.timelineStartUs, clip.timelineDurationUs)) } ?: ContextualPreviewDraft()
            is EditorTool.Fade -> clips.firstOrNull { it.id == tool.clipId }?.let { ContextualPreviewDraft(fadeInUs = it.fadeInUs, fadeOutUs = it.fadeOutUs) } ?: ContextualPreviewDraft()
            is EditorTool.Transform -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> clips.firstOrNull { it.id == tool.ownerId }?.let { clip ->
                    val d = clip.timelineDurationUs
                    ContextualPreviewDraft(transform = PreviewTransformDraft(
                        evaluated(clip.id, KeyframeProperty.POSITION_X, clip.transform.x, clip.timelineStartUs, d),
                        evaluated(clip.id, KeyframeProperty.POSITION_Y, clip.transform.y, clip.timelineStartUs, d),
                        evaluated(clip.id, KeyframeProperty.SCALE_X, clip.transform.scaleX, clip.timelineStartUs, d),
                        evaluated(clip.id, KeyframeProperty.SCALE_Y, clip.transform.scaleY, clip.timelineStartUs, d),
                        evaluated(clip.id, KeyframeProperty.ROTATION, clip.transform.rotationDegrees, clip.timelineStartUs, d),
                        clip.transform.flipHorizontal, clip.transform.flipVertical
                    ))
                } ?: ContextualPreviewDraft()
                VisualOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.let { overlay ->
                    val d = overlay.timelineEndUs - overlay.timelineStartUs
                    ContextualPreviewDraft(transform = PreviewTransformDraft(
                        evaluated(overlay.id, KeyframeProperty.POSITION_X, overlay.transform.x, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.POSITION_Y, overlay.transform.y, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.SCALE_X, overlay.transform.scaleX, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.SCALE_Y, overlay.transform.scaleY, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.ROTATION, overlay.transform.rotationDegrees, overlay.timelineStartUs, d)
                    ))
                } ?: ContextualPreviewDraft()
                VisualOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.let { overlay ->
                    val d = overlay.timelineEndUs - overlay.timelineStartUs
                    ContextualPreviewDraft(transform = PreviewTransformDraft(
                        evaluated(overlay.id, KeyframeProperty.POSITION_X, overlay.transform.x, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.POSITION_Y, overlay.transform.y, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.SCALE_X, overlay.transform.scaleX, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.SCALE_Y, overlay.transform.scaleY, overlay.timelineStartUs, d),
                        evaluated(overlay.id, KeyframeProperty.ROTATION, overlay.transform.rotationDegrees, overlay.timelineStartUs, d)
                    ))
                } ?: ContextualPreviewDraft()
            }
            is EditorTool.Opacity -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> clips.firstOrNull { it.id == tool.ownerId }?.let { c -> ContextualPreviewDraft(opacity = evaluated(c.id, KeyframeProperty.OPACITY, c.opacity, c.timelineStartUs, c.timelineDurationUs)) } ?: ContextualPreviewDraft()
                VisualOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.let { o -> ContextualPreviewDraft(opacity = evaluated(o.id, KeyframeProperty.OPACITY, o.opacity, o.timelineStartUs, o.timelineEndUs - o.timelineStartUs)) } ?: ContextualPreviewDraft()
                VisualOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.let { o -> ContextualPreviewDraft(opacity = evaluated(o.id, KeyframeProperty.OPACITY, o.transform.opacity, o.timelineStartUs, o.timelineEndUs - o.timelineStartUs)) } ?: ContextualPreviewDraft()
            }
            is EditorTool.TextEditor -> ContextualPreviewDraft(textContent = tool.overlayId?.let { oid -> timeline?.textOverlays?.firstOrNull { it.id == oid }?.content }.orEmpty())
            is EditorTool.TextStyle -> timeline?.textOverlays?.firstOrNull { it.id == tool.overlayId }?.let { o -> ContextualPreviewDraft(textStyle = PreviewTextStyleDraft(o.fontSizeSp, o.fontWeight, o.italic, o.alignment, o.colorArgb)) } ?: ContextualPreviewDraft()
            else -> ContextualPreviewDraft()
        }
        when (tool) {
            is EditorTool.Trim, is EditorTool.Crop, is EditorTool.TextEditor, is EditorTool.TextStyle, is EditorTool.Transform -> isPlaying = false
            else -> Unit
        }
        val ownerWindow = when (tool) {'''
pattern = r'''        when \(tool\) \{\n\s*is EditorTool\.Trim, is EditorTool\.Crop, is EditorTool\.TextEditor, is EditorTool\.TextStyle -> isPlaying = false\n\s*is EditorTool\.Transform -> isPlaying = false\n\s*else -> Unit\n\s*}\n\s*val ownerWindow = when \(tool\) \{'''
s, n = re.subn(pattern, draft, s, count=1)
if n != 1:
    raise SystemExit('draft initialization replacement failed')

s, n = re.subn(r'''    fun cancelActiveTool\(\) \{.*?\n    \}\n\n    fun closeOrBack''', '''    fun cancelActiveTool() {
        clearToolSession()
    }

    fun closeOrBack''', s, count=1, flags=re.S)
if n != 1:
    raise SystemExit('cancel replacement failed')

s, n = re.subn(r'''    fun transformGesture\(dx: Float, dy: Float, zoom: Float, rotation: Float\) \{.*?\n    \}\n\n    BackHandler''', '''    fun transformGesture(dx: Float, dy: Float, zoom: Float, rotation: Float) {
        if (activeTool !is EditorTool.Transform) return
        val current = previewDraft.transform ?: return
        val rawX = (current.x + dx).coerceIn(0f, 1f)
        val rawY = (current.y + dy).coerceIn(0f, 1f)
        previewDraft = previewDraft.copy(transform = current.copy(
            x = if (abs(rawX - 0.5f) < 0.018f) 0.5f else rawX,
            y = if (abs(rawY - 0.5f) < 0.018f) 0.5f else rawY,
            scaleX = (current.scaleX * zoom).coerceIn(0.05f, 10f),
            scaleY = (current.scaleY * zoom).coerceIn(0.05f, 10f),
            rotationDegrees = current.rotationDegrees + rotation
        ))
    }

    BackHandler''', s, count=1, flags=re.S)
if n != 1:
    raise SystemExit('transform replacement failed')

pattern = re.compile(r'''(project, editor, playheadUs, isPlaying, Modifier\.weight\([^\n]+\), activeTool),\n\s*onCropChange = \{ crop ->\n\s*\(activeTool as\? EditorTool\.Crop\)\?\.let \{ tool -> contextualVm\.setClipCrop\(id, tool\.clipId, crop\) \{ vm\.load\(id\) \} \}\n\s*\},\n\s*onTransformGesture = ::transformGesture''')
s, n = pattern.subn(r'''\1, previewDraft,
                            onCropChange = { crop -> previewDraft = previewDraft.copy(crop = crop) },
                            onCropCommit = { },
                            onTransformGesture = ::transformGesture,
                            onTransformGestureEnd = { }''', s)
if n != 3:
    raise SystemExit(f'expected 3 preview replacements, got {n}')

host = '        overlayVm = overlayVm,\n        onDismiss = ::clearToolSession,'
if host not in s:
    raise SystemExit('host marker missing')
s = s.replace(host, '        overlayVm = overlayVm,\n        previewDraft = previewDraft,\n        onPreviewDraftChange = { previewDraft = it },\n        onDismiss = ::clearToolSession,', 1)

p.write_text(s)
