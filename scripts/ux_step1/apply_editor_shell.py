#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {count}")
    return updated


# ---------------------------------------------------------------------------
# Explicit workspace mode + saveable shell state.
# ---------------------------------------------------------------------------
models_path = "app/src/main/java/com/videoflow/app/ui/editor/EditorWorkspaceModels.kt"
models = read(models_path)
models = replace_once(
    models,
    "import com.videoflow.app.domain.editor.CropRect\n",
    "import androidx.compose.runtime.saveable.Saver\nimport com.videoflow.app.domain.editor.CropRect\n",
    "workspace-model Saver import",
)
models = replace_once(
    models,
    "/**\n * UI-only transform values used while a pointer/slider gesture is active.\n",
    r'''/** Explicit, centralized editor layout state. */
sealed interface EditorWorkspaceMode {
    data object Main : EditorWorkspaceMode
    data class FocusedTool(val tool: EditorTool) : EditorWorkspaceMode
}

private const val EditorStateSeparator = "\u001F"

val EditorSelectionSaver = Saver<EditorSelection, String>(
    save = { selection ->
        when (selection) {
            EditorSelection.None -> "none"
            is EditorSelection.Clip -> listOf("clip", selection.clipId).joinToString(EditorStateSeparator)
            is EditorSelection.Track -> listOf("track", selection.trackId).joinToString(EditorStateSeparator)
            is EditorSelection.TextOverlay -> listOf("text", selection.overlayId).joinToString(EditorStateSeparator)
            is EditorSelection.ImageOverlay -> listOf("image", selection.overlayId).joinToString(EditorStateSeparator)
        }
    },
    restore = { token ->
        val parts = token.split(EditorStateSeparator)
        when (parts.firstOrNull()) {
            "clip" -> parts.getOrNull(1)?.let(EditorSelection::Clip) ?: EditorSelection.None
            "track" -> parts.getOrNull(1)?.let(EditorSelection::Track) ?: EditorSelection.None
            "text" -> parts.getOrNull(1)?.let(EditorSelection::TextOverlay) ?: EditorSelection.None
            "image" -> parts.getOrNull(1)?.let(EditorSelection::ImageOverlay) ?: EditorSelection.None
            else -> EditorSelection.None
        }
    }
)

val EditorToolSaver = Saver<EditorTool?, String>(
    save = { tool ->
        when (tool) {
            null -> "none"
            is EditorTool.Trim -> listOf("trim", tool.clipId, tool.startPrecise.toString()).joinToString(EditorStateSeparator)
            is EditorTool.Speed -> listOf("speed", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.Crop -> listOf("crop", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.Transform -> listOf("transform", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.Opacity -> listOf("opacity", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.Volume -> listOf("volume", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.Fade -> listOf("fade", tool.clipId).joinToString(EditorStateSeparator)
            is EditorTool.TextEditor -> listOf("text-editor", tool.overlayId.orEmpty()).joinToString(EditorStateSeparator)
            is EditorTool.TextStyle -> listOf("text-style", tool.overlayId).joinToString(EditorStateSeparator)
            is EditorTool.Timing -> listOf("timing", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.Keyframes -> listOf("keyframes", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
            is EditorTool.More -> listOf("more", tool.ownerId, tool.ownerType.name).joinToString(EditorStateSeparator)
        }
    },
    restore = { token ->
        val p = token.split(EditorStateSeparator)
        when (p.firstOrNull()) {
            "trim" -> p.getOrNull(1)?.let { EditorTool.Trim(it, p.getOrNull(2).toBoolean()) }
            "speed" -> p.getOrNull(1)?.let(EditorTool::Speed)
            "crop" -> p.getOrNull(1)?.let(EditorTool::Crop)
            "transform" -> if (p.size >= 3) EditorTool.Transform(p[1], VisualOwnerType.valueOf(p[2])) else null
            "opacity" -> if (p.size >= 3) EditorTool.Opacity(p[1], VisualOwnerType.valueOf(p[2])) else null
            "volume" -> p.getOrNull(1)?.let(EditorTool::Volume)
            "fade" -> p.getOrNull(1)?.let(EditorTool::Fade)
            "text-editor" -> EditorTool.TextEditor(p.getOrNull(1)?.takeIf(String::isNotEmpty))
            "text-style" -> p.getOrNull(1)?.let(EditorTool::TextStyle)
            "timing" -> if (p.size >= 3) EditorTool.Timing(p[1], TimedOwnerType.valueOf(p[2])) else null
            "keyframes" -> if (p.size >= 3) EditorTool.Keyframes(p[1], VisualOwnerType.valueOf(p[2])) else null
            "more" -> if (p.size >= 3) EditorTool.More(p[1], VisualOwnerType.valueOf(p[2])) else null
            else -> null
        }
    }
)

/**
 * UI-only transform values used while a pointer/slider gesture is active.
''',
    "explicit workspace state",
)
write(models_path, models)


# ---------------------------------------------------------------------------
# Focused tool host: inline scaffold, fixed action registry, no bottom overlay.
# ---------------------------------------------------------------------------
contextual_path = "app/src/main/java/com/videoflow/app/ui/editor/ContextualToolPanels.kt"
contextual = read(contextual_path)
contextual = replace_once(
    contextual,
    "    refresh: () -> Unit\n) {\n",
    "    refresh: () -> Unit,\n    modifier: Modifier = Modifier\n) {\n",
    "ContextualToolHost modifier",
)
contextual = replace_once(
    contextual,
    "    ContextualToolPanelSurface {\n",
    "    FocusedToolScaffold(\n        modifier = modifier,\n        toolKey = tool,\n        fallbackCancel = onDismiss\n    ) {\n",
    "inline focused host",
)
contextual = replace_once(
    contextual,
    "        Spacer(Modifier.height(24.dp))\n    }\n}\n\n@Composable\nfun ContextualToolPanelSurface",
    "    }\n}\n\n@Composable\nfun ContextualToolPanelSurface",
    "remove host spacer",
)
old_action_row = '''@Composable
private fun ActionRow(onCancel: () -> Unit, onReset: (() -> Unit)? = null, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        if (onReset != null) OutlinedButton(onClick = onReset) { Text("Reset") }
        Button(onClick = onDone) { Text("Done") }
    }
}
'''
new_action_row = '''@Composable
private fun ActionRow(onCancel: () -> Unit, onReset: (() -> Unit)? = null, onDone: () -> Unit) {
    // In the production focused workspace, bind feature-owned callbacks to the shell's
    // non-scrolling action bar. Standalone previews/tests retain the legacy inline row.
    if (registerFocusedToolActions(onCancel = onCancel, onReset = onReset, onDone = onDone)) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        if (onReset != null) OutlinedButton(onClick = onReset) { Text("Reset") }
        Button(onClick = onDone) { Text("Done") }
    }
}
'''
contextual = replace_once(contextual, old_action_row, new_action_row, "fixed focused actions")
write(contextual_path, contextual)


# ---------------------------------------------------------------------------
# Preview semantic/test contract. Existing aspect-fit math is intentionally unchanged.
# ---------------------------------------------------------------------------
preview_path = "app/src/main/java/com/videoflow/app/ui/editor/PreviewWorkspace.kt"
preview = read(preview_path)
preview = replace_once(
    preview,
    "import androidx.compose.ui.graphics.graphicsLayer\n",
    "import androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.platform.testTag\nimport androidx.compose.ui.semantics.contentDescription\nimport androidx.compose.ui.semantics.semantics\n",
    "preview semantics imports",
)
preview = replace_once(
    preview,
    "            .background(VideoFlowEditorColors.EditorBackground)\n            .clipToBounds(),\n",
    "            .background(VideoFlowEditorColors.EditorBackground)\n            .clipToBounds()\n            .testTag(\"editor-preview-host\")\n            .semantics { contentDescription = \"Video preview\" },\n",
    "preview host contract",
)
write(preview_path, preview)


# ---------------------------------------------------------------------------
# Top/bottom chrome: focused Export ambiguity, toolbar geometry/test semantics, font scale.
# ---------------------------------------------------------------------------
chrome_path = "app/src/main/java/com/videoflow/app/ui/editor/EditorChrome.kt"
chrome = read(chrome_path)
chrome = replace_once(
    chrome,
    "import androidx.compose.ui.platform.LocalDensity\n",
    "import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.testTag\n",
    "chrome testTag import",
)
chrome = replace_once(
    chrome,
    "    onRedo: () -> Unit,\n    onExport: () -> Unit\n) {\n",
    "    onRedo: () -> Unit,\n    onExport: () -> Unit,\n    showExport: Boolean = true\n) {\n",
    "top bar focused export option",
)
chrome = replace_once(
    chrome,
    '''            TextButton(onClick = onExport) {
                Text("Export", color = VideoFlowEditorColors.PrimaryText, fontWeight = FontWeight.SemiBold)
            }
''',
    '''            if (showExport) {
                TextButton(onClick = onExport) {
                    Text("Export", color = VideoFlowEditorColors.PrimaryText, fontWeight = FontWeight.SemiBold)
                }
            }
''',
    "hide export while draft tool open",
)
chrome = replace_once(
    chrome,
    "        modifier = Modifier.navigationBarsPadding()\n",
    "        modifier = Modifier.navigationBarsPadding().testTag(\"editor-bottom-toolbar\")\n",
    "bottom toolbar safe tag",
)
chrome = replace_once(
    chrome,
    "            .height(72.dp)\n            .horizontalScroll(rememberScrollState())\n",
    "            .height(72.dp)\n            .horizontalScroll(rememberScrollState())\n            .testTag(\"editor-bottom-tool-carousel\")\n",
    "tool carousel tag",
)
chrome = replace_once(
    chrome,
    '''    val fontScale = LocalDensity.current.fontScale
    val compactLabelSize = if (fontScale >= 1.3f) 9.sp else MaterialTheme.typography.labelSmall.fontSize
    val cellWidth = when {
        label.length >= 11 -> 96.dp
        label.length >= 8 -> 84.dp
        else -> 72.dp
    }
''',
    '''    val fontScale = LocalDensity.current.fontScale
    val baseCellWidth = when {
        label.length >= 11 -> 96.dp
        label.length >= 8 -> 84.dp
        else -> 72.dp
    }
    // Preserve readable typography at large font scales; grow the horizontally scrollable cell
    // instead of forcing labels down to a microscopic fixed 9sp size.
    val cellWidth = baseCellWidth + if (fontScale >= 1.3f) 12.dp else 0.dp
''',
    "font scale toolbar policy",
)
chrome = replace_once(
    chrome,
    "            fontSize = compactLabelSize,\n",
    "            fontSize = MaterialTheme.typography.labelSmall.fontSize,\n",
    "toolbar readable font size",
)
write(chrome_path, chrome)


# ---------------------------------------------------------------------------
# Timeline: bounded vertical viewport, responsive row height, compact header, stable preview.
# ---------------------------------------------------------------------------
timeline_path = "app/src/main/java/com/videoflow/app/ui/editor/TimelineWorkspace.kt"
timeline = read(timeline_path)
timeline = replace_once(
    timeline,
    "import androidx.compose.ui.platform.LocalDensity\n",
    "import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.testTag\n",
    "timeline testTag import",
)
timeline = replace_once(
    timeline,
    "private val TrackHeaderWidth = 96.dp\n",
    "// Two independent 48dp actions require 96dp; compactness is achieved by removing the\n// old expanding name/action row rather than shrinking accessible touch targets.\nprivate val TrackHeaderWidth = 96.dp\n",
    "track header rationale",
)
timeline = replace_once(
    timeline,
    "    onTrackSettings: (TimelineTrack) -> Unit,\n    modifier: Modifier = Modifier,\n",
    "    onTrackSettings: (TimelineTrack) -> Unit,\n    trackRowHeight: Dp = 72.dp,\n    modifier: Modifier = Modifier,\n",
    "timeline row height parameter",
)
timeline = replace_once(
    timeline,
    "    val horizontal = rememberScrollState()\n",
    "    val horizontal = rememberScrollState()\n    val verticalTracks = rememberScrollState()\n",
    "vertical track scroll state",
)
timeline = replace_once(
    timeline,
    "    Surface(modifier = modifier, color = VideoFlowEditorColors.TimelineBackground) {\n",
    "    Surface(modifier = modifier.testTag(\"timeline-workspace\"), color = VideoFlowEditorColors.TimelineBackground) {\n",
    "timeline workspace tag",
)
timeline = replace_once(
    timeline,
    "                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {\n",
    "                Column(\n                    Modifier\n                        .fillMaxWidth()\n                        .weight(1f)\n                        .verticalScroll(verticalTracks)\n                        .testTag(\"timeline-track-viewport\")\n                        .semantics { contentDescription = \"Timeline track viewport\" }\n                ) {\n",
    "bounded vertical track viewport",
)
timeline = replace_once(
    timeline,
    "                            onTrackSettings = { onTrackSettings(track) }\n                        )\n",
    "                            onTrackSettings = { onTrackSettings(track) },\n                            laneHeight = trackRowHeight\n                        )\n",
    "pass responsive row height",
)
timeline = replace_once(
    timeline,
    "    onTrackSettings: () -> Unit\n) {\n    val density = LocalDensity.current\n    val laneHeight = 88.dp\n",
    "    onTrackSettings: () -> Unit,\n    laneHeight: Dp\n) {\n    val density = LocalDensity.current\n",
    "track row signature",
)
old_header = '''        Surface(color = VideoFlowEditorColors.TimelineTrackHeader, modifier = Modifier.width(TrackHeaderWidth).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().padding(start = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    track.name.take(7),
                    color = VideoFlowEditorColors.PrimaryText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (track.type == TrackType.AUDIO) {
                    IconButton(onClick = onToggleMute, modifier = Modifier.width(48.dp).height(48.dp)) {
                        Icon(
                            if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}",
                            tint = VideoFlowEditorColors.SecondaryText
                        )
                    }
                } else {
                    IconButton(onClick = onToggleVisible, modifier = Modifier.width(48.dp).height(48.dp)) {
                        Icon(
                            if (track.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (track.visible) "Hide ${track.name}" else "Show ${track.name}",
                            tint = VideoFlowEditorColors.SecondaryText
                        )
                    }
                }
                IconButton(onClick = onTrackSettings, modifier = Modifier.width(48.dp).height(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Open ${track.name} settings", tint = VideoFlowEditorColors.SecondaryText)
                }
            }
        }
'''
new_header = '''        Surface(color = VideoFlowEditorColors.TimelineTrackHeader, modifier = Modifier.width(TrackHeaderWidth).fillMaxHeight()) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    track.name.take(10),
                    color = VideoFlowEditorColors.SecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 2.dp, vertical = 1.dp)
                )
                Row(
                    Modifier.align(Alignment.BottomCenter).height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (track.type == TrackType.AUDIO) {
                        IconButton(onClick = onToggleMute, modifier = Modifier.width(48.dp).height(48.dp)) {
                            Icon(
                                if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}",
                                tint = VideoFlowEditorColors.SecondaryText
                            )
                        }
                    } else {
                        IconButton(onClick = onToggleVisible, modifier = Modifier.width(48.dp).height(48.dp)) {
                            Icon(
                                if (track.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (track.visible) "Hide ${track.name}" else "Show ${track.name}",
                                tint = VideoFlowEditorColors.SecondaryText
                            )
                        }
                    }
                    IconButton(onClick = onTrackSettings, modifier = Modifier.width(48.dp).height(48.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options for ${track.name}", tint = VideoFlowEditorColors.SecondaryText)
                    }
                }
            }
        }
'''
timeline = replace_once(timeline, old_header, new_header, "compact track header")
timeline = replace_once(
    timeline,
    "                            onTrimEnd = { onTrimClipEnd(clip.id, it) }\n                        )\n",
    "                            onTrimEnd = { onTrimClipEnd(clip.id, it) },\n                            laneHeight = laneHeight\n                        )\n",
    "clip receives lane height",
)
timeline = replace_once(
    timeline,
    "    onTrimEnd: (Long) -> Unit\n) {\n",
    "    onTrimEnd: (Long) -> Unit,\n    laneHeight: Dp\n) {\n",
    "clip card lane height signature",
)
timeline = replace_once(
    timeline,
    "            .height(68.dp)\n",
    "            .height((laneHeight - 10.dp).coerceAtLeast(54.dp))\n",
    "clip card responsive height",
)
timeline = replace_once(
    timeline,
    "            .offset(x = timeWidth(startUs, pixelsPerSecond), y = 10.dp)\n",
    "            .offset(x = timeWidth(startUs, pixelsPerSecond), y = 6.dp)\n",
    "overlay vertical offset",
)
timeline = replace_once(
    timeline,
    "            .height(56.dp)\n",
    "            .height(52.dp)\n",
    "overlay compact height",
)
write(timeline_path, timeline)


# ---------------------------------------------------------------------------
# EditorScreen: central mode decision; reusable content lambdas; focused host participates in layout.
# ---------------------------------------------------------------------------
screen_path = "app/src/main/java/com/videoflow/app/ui/screens/EditorScreen.kt"
screen = read(screen_path)
screen = replace_once(
    screen,
    "import androidx.compose.ui.Modifier\n",
    "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.unit.Dp\n",
    "screen Dp import",
)
screen = replace_once(
    screen,
    "import com.videoflow.app.ui.editor.EditorSelection\nimport com.videoflow.app.ui.editor.EditorTool\n",
    "import com.videoflow.app.ui.editor.EditorSelection\nimport com.videoflow.app.ui.editor.EditorSelectionSaver\nimport com.videoflow.app.ui.editor.EditorTool\nimport com.videoflow.app.ui.editor.EditorToolSaver\nimport com.videoflow.app.ui.editor.EditorWorkspaceMode\nimport com.videoflow.app.ui.editor.FocusedEditorWorkspace\nimport com.videoflow.app.ui.editor.MainEditorPortraitScaffold\nimport com.videoflow.app.ui.editor.editorShellMetrics\n",
    "screen workspace imports",
)
screen = replace_once(
    screen,
    "    var selection by remember { mutableStateOf<EditorSelection>(EditorSelection.None) }\n",
    "    var selection by rememberSaveable(stateSaver = EditorSelectionSaver) { mutableStateOf<EditorSelection>(EditorSelection.None) }\n",
    "saveable selection",
)
screen = replace_once(
    screen,
    "    var activeTool by remember { mutableStateOf<EditorTool?>(null) }\n",
    "    var activeTool by rememberSaveable(stateSaver = EditorToolSaver) { mutableStateOf<EditorTool?>(null) }\n",
    "saveable focused tool mode",
)
screen = replace_once(
    screen,
    "    val changedCount = project?.mediaAssets.orEmpty().count { it.sourceStatus == SourceStatus.CHANGED }\n",
    "    val changedCount = project?.mediaAssets.orEmpty().count { it.sourceStatus == SourceStatus.CHANGED }\n    val workspaceMode: EditorWorkspaceMode = activeTool?.let { EditorWorkspaceMode.FocusedTool(it) } ?: EditorWorkspaceMode.Main\n",
    "central workspace mode",
)

new_layout = r'''    val previewContent: @Composable (Modifier) -> Unit = { previewModifier ->
        PreviewWorkspace(
            project = project,
            editor = editor,
            playheadUs = playheadUs,
            isPlaying = isPlaying,
            modifier = previewModifier,
            activeTool = activeTool,
            previewDraft = previewDraft,
            onCropChange = { crop -> previewDraft = previewDraft.copy(crop = crop) },
            onCropCommit = { },
            onTransformGesture = ::transformGesture,
            onTransformGestureEnd = { }
        )
    }
    val warningContent: @Composable () -> Unit = {
        EditorWarningBanner(offlineCount, changedCount) { activePanel = EditorPanel.Media }
    }
    val transportContent: @Composable () -> Unit = {
        TransportBar(
            playheadUs = playheadUs,
            durationUs = durationUs,
            isPlaying = isPlaying,
            onJumpStart = { isPlaying = false; vm.setPlayheadUs(0L) },
            onPlayPause = { isPlaying = !isPlaying }
        )
    }
    val timelineContent: @Composable (Modifier, Dp) -> Unit = { timelineModifier, rowHeight ->
        TimelineWorkspace(
            tracks = tracks,
            clips = clips,
            textOverlays = timeline?.textOverlays.orEmpty(),
            imageOverlays = timeline?.imageOverlays.orEmpty(),
            keyframes = timeline?.keyframes.orEmpty(),
            playheadUs = playheadUs,
            durationUs = durationUs,
            pixelsPerSecond = pixelsPerSecond,
            selection = selection,
            mediaNames = mediaNames,
            thumbnails = thumbnails,
            waveforms = waveforms,
            onZoom = { pixelsPerSecond = it },
            onSeek = { isPlaying = false; vm.setPlayheadUs(it.coerceAtMost(durationUs)) },
            onSelect = ::select,
            onClearSelection = ::clearSelection,
            onMoveClip = { clipId, deltaUs ->
                vm.selectClip(clipId)
                selection = EditorSelection.Clip(clipId)
                vm.moveSelectedSnapped(deltaUs, pixelsPerSecond.toDouble())
            },
            onTrimClipStart = { clipId, deltaTimelineUs ->
                clips.firstOrNull { it.id == clipId }?.let { clip ->
                    vm.selectClip(clipId)
                    selection = EditorSelection.Clip(clipId)
                    vm.trimSelectedStart((deltaTimelineUs.toDouble() * clip.speed).roundToLong())
                }
            },
            onTrimClipEnd = { clipId, deltaTimelineUs ->
                clips.firstOrNull { it.id == clipId }?.let { clip ->
                    vm.selectClip(clipId)
                    selection = EditorSelection.Clip(clipId)
                    vm.trimSelectedEnd((deltaTimelineUs.toDouble() * clip.speed).roundToLong())
                }
            },
            onToggleMute = { vm.toggleTrackMute(it.id, !it.muted) },
            onToggleVisible = { vm.toggleTrackVisible(it.id, !it.visible) },
            onToggleLock = { vm.toggleTrackLock(it.id, !it.locked) },
            onTrackSettings = { activePanel = EditorPanel.TrackSettings(it.id) },
            trackRowHeight = rowHeight,
            revision = project?.updatedAt ?: 0L,
            onProfessionalTool = onProfessionalTool,
            modifier = timelineModifier
        )
    }

    Scaffold(
        containerColor = VideoFlowEditorColors.EditorBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditorTopBar(
                projectName = project?.name ?: "VideoFlow",
                saving = saving || importState in setOf(ImportState.Opening, ImportState.ReadingMetadata, ImportState.Fingerprinting, ImportState.Saving),
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                onBack = ::closeOrBack,
                onUndo = vm::undo,
                onRedo = vm::redo,
                onExport = onExport,
                showExport = workspaceMode == EditorWorkspaceMode.Main
            )
        },
        bottomBar = {
            if (workspaceMode == EditorWorkspaceMode.Main) {
                EditorBottomToolbar(
                    selection = selection,
                    selectedClipMime = selectedClipMime,
                    onPanel = { clearToolSession(); activePanel = it },
                    onTool = ::openTool,
                    onSplit = { vm.splitSelected() },
                    onProfessionalTool = { clearToolSession(); activePanel = null; onProfessionalTool(it) }
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(VideoFlowEditorColors.EditorBackground)
        ) {
            when (val mode = workspaceMode) {
                is EditorWorkspaceMode.FocusedTool -> {
                    FocusedEditorWorkspace(
                        preview = { previewContent(Modifier.fillMaxSize()) },
                        warning = warningContent,
                        transport = transportContent,
                        focusedTool = {
                            ContextualToolHost(
                                tool = mode.tool,
                                projectId = id,
                                project = project,
                                editor = editor,
                                playheadUs = playheadUs,
                                thumbnails = thumbnails,
                                waveforms = waveforms,
                                editorVm = vm,
                                contextualVm = contextualVm,
                                overlayVm = overlayVm,
                                previewDraft = previewDraft,
                                onPreviewDraftChange = { previewDraft = it },
                                onDismiss = ::clearToolSession,
                                onSelect = ::select,
                                onOpenTool = ::openTool,
                                onPreviewSeek = { vm.setPlayheadUs(it) },
                                refresh = { vm.load(id) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                }
                EditorWorkspaceMode.Main -> {
                    val compactLandscape = maxWidth > maxHeight && maxHeight.value < 400f
                    val wide = maxWidth > maxHeight || maxWidth.value >= 700f
                    val rowHeight = editorShellMetrics(maxHeight).trackRowHeight

                    if (compactLandscape) {
                        Row(Modifier.fillMaxSize()) {
                            Column(Modifier.weight(0.46f)) {
                                previewContent(Modifier.weight(1f))
                                warningContent()
                                transportContent()
                            }
                            timelineContent(Modifier.weight(0.54f), rowHeight)
                        }
                    } else if (wide) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.weight(0.56f)) {
                                previewContent(Modifier.weight(0.70f))
                                LandscapeInfoPane(
                                    selection = selection,
                                    projectName = project?.name ?: "VideoFlow",
                                    resolution = editor?.settings?.let { "${it.width}×${it.height}" } ?: "Project canvas",
                                    modifier = Modifier.weight(0.30f)
                                )
                            }
                            warningContent()
                            transportContent()
                            timelineContent(Modifier.weight(0.44f), rowHeight)
                        }
                    } else {
                        MainEditorPortraitScaffold(
                            modifier = Modifier.fillMaxSize(),
                            preview = { previewContent(Modifier.fillMaxSize()) },
                            warning = warningContent,
                            transport = transportContent,
                            timeline = { responsiveRowHeight ->
                                timelineContent(Modifier.fillMaxSize(), responsiveRowHeight)
                            }
                        )
                    }
                }
            }
        }
    }
'''

screen = regex_once(
    screen,
    r"    Scaffold\(\n.*?\n    \}\n\n    EditorPanelHost\(",
    new_layout + "\n    EditorPanelHost(",
    "central editor scaffold replacement",
)
# The major contextual host now lives inside FocusedEditorWorkspace; remove the legacy overlay call.
screen = regex_once(
    screen,
    r'''\n    ContextualToolHost\(\n        tool = activeTool,.*?\n    \)\n''',
    "\n",
    "remove external contextual overlay",
)
write(screen_path, screen)

print("UX Step 1 editor-shell source patch applied successfully")
