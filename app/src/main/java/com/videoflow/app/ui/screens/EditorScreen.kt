package com.videoflow.app.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.ProxyStatus
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.model.ImportState
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.ui.ContextualEditingViewModel
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.OverlayAdvancedViewModel
import com.videoflow.app.ui.ProjectViewModel
import com.videoflow.app.ui.TrackLifecycleViewModel
import com.videoflow.app.ui.editor.ContextualToolHost
import com.videoflow.app.ui.editor.ContextualPreviewDraft
import com.videoflow.app.ui.editor.EditorBottomToolbar
import com.videoflow.app.ui.editor.EditorPanel
import com.videoflow.app.ui.editor.EditorPanelHost
import com.videoflow.app.ui.editor.EditorSelection
import com.videoflow.app.ui.editor.EditorTool
import com.videoflow.app.ui.editor.EditorTopBar
import com.videoflow.app.ui.editor.EditorWarningBanner
import com.videoflow.app.ui.editor.LandscapeInfoPane
import com.videoflow.app.ui.editor.PreviewWorkspace
import com.videoflow.app.ui.editor.PreviewTextStyleDraft
import com.videoflow.app.ui.editor.PreviewTransformDraft
import com.videoflow.app.ui.editor.snapNormalizedToCenter
import com.videoflow.app.ui.editor.TimelineWorkspace
import com.videoflow.app.ui.editor.TransportBar
import com.videoflow.app.ui.editor.VideoFlowEditorColors
import com.videoflow.app.ui.editor.VisualOwnerType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    id: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    vm: EditorViewModel
) {
    val projectVm: ProjectViewModel = hiltViewModel()
    val overlayVm: OverlayAdvancedViewModel = hiltViewModel()
    val trackVm: TrackLifecycleViewModel = hiltViewModel()
    val contextualVm: ContextualEditingViewModel = hiltViewModel()

    val project by vm.project.collectAsState()
    val editor by vm.editor.collectAsState()
    val playheadUs by vm.playheadUs.collectAsState()
    val selectedClipId by vm.selectedClipId.collectAsState()
    val message by vm.message.collectAsState()
    val saving by vm.saving.collectAsState()
    val history by vm.history.collectAsState()
    val snapshots by vm.snapshots.collectAsState()
    val waveforms by vm.waveforms.collectAsState()
    val thumbnails by vm.thumbnails.collectAsState()
    val proxyProgress by vm.proxyProgress.collectAsState()

    val importedProject by projectVm.project.collectAsState()
    val importState by projectVm.importState.collectAsState()
    val projectMessage by projectVm.message.collectAsState()
    val pendingDuplicate by projectVm.pendingDuplicate.collectAsState()
    val pendingWeakRelink by projectVm.pendingWeakRelink.collectAsState()

    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var pixelsPerSecond by rememberSaveable { mutableFloatStateOf(72f) }
    var selection by remember { mutableStateOf<EditorSelection>(EditorSelection.None) }
    var activePanel by remember { mutableStateOf<EditorPanel?>(null) }
    var activeTool by remember { mutableStateOf<EditorTool?>(null) }
    var previewDraft by remember { mutableStateOf(ContextualPreviewDraft()) }
    var initialToolClip by remember { mutableStateOf<TimelineClip?>(null) }
    var initialToolText by remember { mutableStateOf<TextOverlay?>(null) }
    var initialToolImage by remember { mutableStateOf<ImageOverlay?>(null) }
    var pendingDeleteTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var relinkAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) projectVm.addMedia(id, uri) else projectVm.pickerCancelled()
    }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val assetId = relinkAssetId
        if (uri != null && assetId != null) projectVm.relink(id, assetId, uri) else projectVm.pickerCancelled()
        relinkAssetId = null
    }

    LaunchedEffect(id) {
        vm.load(id)
        projectVm.load(id)
    }

    LaunchedEffect(importedProject?.mediaAssets?.map { it.id to it.updatedIdentityKey() }) {
        val importedIds = importedProject?.mediaAssets?.map { it.id }.orEmpty()
        val editorIds = project?.mediaAssets?.map { it.id }.orEmpty()
        if (importedIds.isNotEmpty() && importedIds != editorIds) vm.load(id)
    }

    LaunchedEffect(project?.mediaAssets?.map { it.id }) {
        project?.mediaAssets.orEmpty()
            .filter { it.mimeType?.startsWith("audio/") == true || it.audioTrackCount > 0 }
            .take(12)
            .forEach { vm.generateWaveform(it.id) }
    }

    LaunchedEffect(selectedClipId) {
        if (selectedClipId != null) selection = EditorSelection.Clip(requireNotNull(selectedClipId))
        else if (selection is EditorSelection.Clip) selection = EditorSelection.None
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }
    LaunchedEffect(projectMessage) {
        projectMessage?.let {
            snackbarHostState.showSnackbar(it)
            projectVm.clearMessage()
        }
    }

    val timeline = editor?.timeline
    val durationUs = (timeline?.durationUs ?: 0L).coerceAtLeast(0L)
    LaunchedEffect(isPlaying, durationUs) {
        if (!isPlaying || durationUs <= 0L) return@LaunchedEffect
        val startUs = playheadUs
        val startedNs = SystemClock.elapsedRealtimeNanos()
        while (isPlaying) {
            val elapsedUs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000L
            val next = startUs + elapsedUs
            if (next >= durationUs) {
                vm.setPlayheadUs(durationUs)
                isPlaying = false
                break
            }
            vm.setPlayheadUs(next)
            delay(16L)
        }
    }

    val tracks = timeline?.tracks.orEmpty()
    val clips = timeline?.clips.orEmpty()
    val mediaNames = project?.mediaAssets.orEmpty().associate { it.id to it.displayName }
    val selectedClip = (selection as? EditorSelection.Clip)?.let { selected -> clips.firstOrNull { it.id == selected.clipId } }
    val selectedClipMime = selectedClip?.let { clip -> project?.mediaAssets?.firstOrNull { it.id == clip.assetId }?.mimeType }
    val offlineCount = project?.mediaAssets.orEmpty().count { it.sourceStatus !in setOf(SourceStatus.AVAILABLE, SourceStatus.CHANGED) }
    val changedCount = project?.mediaAssets.orEmpty().count { it.sourceStatus == SourceStatus.CHANGED }

    fun clearToolSession() {
        activeTool = null
        previewDraft = ContextualPreviewDraft()
        initialToolClip = null
        initialToolText = null
        initialToolImage = null
    }

    fun clearSelection() {
        clearToolSession()
        selection = EditorSelection.None
        vm.selectClip(null)
    }

    fun select(item: EditorSelection) {
        selection = item
        when (item) {
            is EditorSelection.Clip -> vm.selectClip(item.clipId)
            else -> vm.selectClip(null)
        }
    }

    fun openTool(tool: EditorTool) {
        activePanel = null
        activeTool = tool
        previewDraft = ContextualPreviewDraft()
        initialToolClip = null
        initialToolText = null
        initialToolImage = null
        val ownerId = when (tool) {
            is EditorTool.Trim -> tool.clipId
            is EditorTool.Speed -> tool.clipId
            is EditorTool.Crop -> tool.clipId
            is EditorTool.Volume -> tool.clipId
            is EditorTool.Fade -> tool.clipId
            is EditorTool.Transform -> tool.ownerId
            is EditorTool.Opacity -> tool.ownerId
            is EditorTool.TextEditor -> tool.overlayId
            is EditorTool.TextStyle -> tool.overlayId
            is EditorTool.Timing -> tool.ownerId
            is EditorTool.Keyframes -> tool.ownerId
            is EditorTool.More -> tool.ownerId
        }
        when (tool) {
            is EditorTool.Trim, is EditorTool.Speed, is EditorTool.Crop, is EditorTool.Volume, is EditorTool.Fade -> {
                initialToolClip = ownerId?.let { idValue -> clips.firstOrNull { it.id == idValue } }
                initialToolClip?.let { clip ->
                    vm.selectClip(clip.id)
                    if (playheadUs !in clip.timelineStartUs until clip.timelineEndUs) vm.setPlayheadUs(clip.timelineStartUs)
                }
            }
            is EditorTool.Transform -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> initialToolClip = clips.firstOrNull { it.id == tool.ownerId }
                VisualOwnerType.TEXT -> initialToolText = timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }
                VisualOwnerType.IMAGE -> initialToolImage = timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }
            }
            is EditorTool.Opacity -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> initialToolClip = clips.firstOrNull { it.id == tool.ownerId }
                VisualOwnerType.TEXT -> initialToolText = timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }
                VisualOwnerType.IMAGE -> initialToolImage = timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }
            }
            is EditorTool.TextEditor -> initialToolText = tool.overlayId?.let { owner -> timeline?.textOverlays?.firstOrNull { it.id == owner } }
            is EditorTool.TextStyle -> initialToolText = timeline?.textOverlays?.firstOrNull { it.id == tool.overlayId }
            is EditorTool.Timing -> Unit
            is EditorTool.Keyframes -> Unit
            is EditorTool.More -> Unit
        }
        fun evaluated(ownerId: String, property: KeyframeProperty, base: Float, startUs: Long, durationUs: Long): Float {
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
        val ownerWindow = when (tool) {
            is EditorTool.Transform -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> clips.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
                VisualOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
                VisualOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
            }
            is EditorTool.Opacity -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> clips.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
                VisualOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
                VisualOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
            }
            is EditorTool.TextEditor -> tool.overlayId?.let { owner -> timeline?.textOverlays?.firstOrNull { it.id == owner }?.let { it.timelineStartUs to it.timelineEndUs } }
            is EditorTool.TextStyle -> timeline?.textOverlays?.firstOrNull { it.id == tool.overlayId }?.let { it.timelineStartUs to it.timelineEndUs }
            is EditorTool.Timing -> when (tool.ownerType) {
                com.videoflow.app.ui.editor.TimedOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
                com.videoflow.app.ui.editor.TimedOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
            }
            is EditorTool.Keyframes -> when (tool.ownerType) {
                VisualOwnerType.CLIP -> clips.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
                VisualOwnerType.TEXT -> timeline?.textOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
                VisualOwnerType.IMAGE -> timeline?.imageOverlays?.firstOrNull { it.id == tool.ownerId }?.let { it.timelineStartUs to it.timelineEndUs }
            }
            else -> null
        }
        ownerWindow?.let { (start, end) -> if (playheadUs !in start until end) vm.setPlayheadUs(start) }
    }

    fun cancelActiveTool() {
        clearToolSession()
    }

    fun closeOrBack() {
        when {
            activeTool != null -> cancelActiveTool()
            activePanel != null -> activePanel = null
            selection != EditorSelection.None -> clearSelection()
            else -> onBack()
        }
    }

    fun transformGesture(dx: Float, dy: Float, zoom: Float, rotation: Float) {
        if (activeTool !is EditorTool.Transform) return
        val current = previewDraft.transform ?: return
        val rawX = (current.x + dx).coerceIn(0f, 1f)
        val rawY = (current.y + dy).coerceIn(0f, 1f)
        previewDraft = previewDraft.copy(transform = current.copy(
            x = snapNormalizedToCenter(rawX),
            y = snapNormalizedToCenter(rawY),
            scaleX = (current.scaleX * zoom).coerceIn(0.05f, 10f),
            scaleY = (current.scaleY * zoom).coerceIn(0.05f, 10f),
            rotationDegrees = current.rotationDegrees + rotation
        ))
    }

    BackHandler(enabled = activeTool != null || activePanel != null || selection != EditorSelection.None, onBack = ::closeOrBack)

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
                onExport = onExport
            )
        },
        bottomBar = {
            EditorBottomToolbar(
                selection = selection,
                selectedClipMime = selectedClipMime,
                onPanel = { clearToolSession(); activePanel = it },
                onTool = ::openTool,
                onSplit = { vm.splitSelected() }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(VideoFlowEditorColors.EditorBackground)
        ) {
            val compactLandscape = maxWidth > maxHeight && maxHeight.value < 400f
            val wide = maxWidth > maxHeight || maxWidth.value >= 700f

            if (compactLandscape) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(0.46f)) {
                        PreviewWorkspace(
                            project, editor, playheadUs, isPlaying, Modifier.weight(1f), activeTool, previewDraft,
                            onCropChange = { crop -> previewDraft = previewDraft.copy(crop = crop) },
                            onCropCommit = { },
                            onTransformGesture = ::transformGesture,
                            onTransformGestureEnd = { }
                        )
                        EditorWarningBanner(offlineCount, changedCount) { activePanel = EditorPanel.Media }
                        TransportBar(
                            playheadUs = playheadUs,
                            durationUs = durationUs,
                            isPlaying = isPlaying,
                            onJumpStart = { isPlaying = false; vm.setPlayheadUs(0L) },
                            onPlayPause = { isPlaying = !isPlaying }
                        )
                    }
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
                        onMoveClip = { clipId, deltaUs -> vm.selectClip(clipId); selection = EditorSelection.Clip(clipId); vm.moveSelectedSnapped(deltaUs, pixelsPerSecond.toDouble()) },
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
                        modifier = Modifier.weight(0.54f)
                    )
                }
            } else if (wide) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(0.56f)) {
                        PreviewWorkspace(
                            project, editor, playheadUs, isPlaying, Modifier.weight(0.70f), activeTool, previewDraft,
                            onCropChange = { crop -> previewDraft = previewDraft.copy(crop = crop) },
                            onCropCommit = { },
                            onTransformGesture = ::transformGesture,
                            onTransformGestureEnd = { }
                        )
                        LandscapeInfoPane(
                            selection = selection,
                            projectName = project?.name ?: "VideoFlow",
                            resolution = editor?.settings?.let { "${it.width}×${it.height}" } ?: "Project canvas",
                            modifier = Modifier.weight(0.30f)
                        )
                    }
                    EditorWarningBanner(offlineCount, changedCount) { activePanel = EditorPanel.Media }
                    TransportBar(
                        playheadUs = playheadUs,
                        durationUs = durationUs,
                        isPlaying = isPlaying,
                        onJumpStart = { isPlaying = false; vm.setPlayheadUs(0L) },
                        onPlayPause = { isPlaying = !isPlaying }
                    )
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
                        onMoveClip = { clipId, deltaUs -> vm.selectClip(clipId); selection = EditorSelection.Clip(clipId); vm.moveSelectedSnapped(deltaUs, pixelsPerSecond.toDouble()) },
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
                        modifier = Modifier.weight(0.44f)
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PreviewWorkspace(
                        project, editor, playheadUs, isPlaying, Modifier.weight(0.42f), activeTool, previewDraft,
                            onCropChange = { crop -> previewDraft = previewDraft.copy(crop = crop) },
                            onCropCommit = { },
                            onTransformGesture = ::transformGesture,
                            onTransformGestureEnd = { }
                    )
                    EditorWarningBanner(offlineCount, changedCount) { activePanel = EditorPanel.Media }
                    TransportBar(
                        playheadUs = playheadUs,
                        durationUs = durationUs,
                        isPlaying = isPlaying,
                        onJumpStart = { isPlaying = false; vm.setPlayheadUs(0L) },
                        onPlayPause = { isPlaying = !isPlaying }
                    )
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
                        onMoveClip = { clipId, deltaUs -> vm.selectClip(clipId); selection = EditorSelection.Clip(clipId); vm.moveSelectedSnapped(deltaUs, pixelsPerSecond.toDouble()) },
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
                        modifier = Modifier.weight(0.58f)
                    )
                }
            }
        }
    }

    EditorPanelHost(
        panel = activePanel,
        projectId = id,
        project = project,
        editor = editor,
        snapshots = snapshots,
        thumbnails = thumbnails,
        waveforms = waveforms,
        proxyGeneratingAssetId = proxyProgress.assetId.takeIf { proxyProgress.status == ProxyStatus.GENERATING },
        proxyPercent = proxyProgress.percent,
        playheadUs = playheadUs,
        editorVm = vm,
        overlayVm = overlayVm,
        onDismiss = { activePanel = null },
        onImport = { types -> projectVm.pickerOpened(); importLauncher.launch(types) },
        onRelink = { assetId -> relinkAssetId = assetId; projectVm.pickerOpened(); relinkLauncher.launch(arrayOf("video/*", "audio/*", "image/*")) },
        onSelect = ::select,
        onDeleteTrack = { pendingDeleteTrackId = it },
        onOpenPanel = { activePanel = it }
    )

    ContextualToolHost(
        tool = activeTool,
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
        refresh = { vm.load(id) }
    )

    pendingDeleteTrackId?.let { trackId ->
        val track = tracks.firstOrNull { it.id == trackId }
        if (track != null) {
            val itemCount = clips.count { it.trackId == trackId } +
                timeline?.textOverlays.orEmpty().count { it.trackId == trackId } +
                timeline?.imageOverlays.orEmpty().count { it.trackId == trackId }
            AlertDialog(
                onDismissRequest = { pendingDeleteTrackId = null },
                title = { Text("Delete ${track.name}?") },
                text = { Text(if (itemCount > 0) "$itemCount timeline item(s) will be deleted with this track. The operation is undoable." else "This empty track will be deleted. The operation is undoable.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteTrackId = null
                            activePanel = null
                            trackVm.deleteConfirmed(id, trackId) { vm.load(id) }
                        },
                        enabled = !track.locked
                    ) { Text("Delete Track") }
                },
                dismissButton = { TextButton(onClick = { pendingDeleteTrackId = null }) { Text("Cancel") } }
            )
        }
    }

    pendingDuplicate?.let {
        AlertDialog(
            onDismissRequest = projectVm::cancelDuplicate,
            title = { Text("Media already in project") },
            text = { Text("The same source is already referenced. Add another reference only if you intentionally need a duplicate media-bin item.") },
            confirmButton = { TextButton(onClick = { projectVm.confirmDuplicate(id) }) { Text("Add Anyway") } },
            dismissButton = { TextButton(onClick = projectVm::cancelDuplicate) { Text("Cancel") } }
        )
    }

    pendingWeakRelink?.let { validation ->
        AlertDialog(
            onDismissRequest = projectVm::cancelWeakRelink,
            title = { Text("Weak source verification") },
            text = { Text(validation.reason) },
            confirmButton = { TextButton(onClick = { projectVm.confirmWeakRelink(id) }) { Text("Use This Source") } },
            dismissButton = { TextButton(onClick = projectVm::cancelWeakRelink) { Text("Cancel") } }
        )
    }
}

private fun com.videoflow.app.domain.model.MediaAsset.updatedIdentityKey(): String =
    "$id:${sourceStatus.name}:${fingerprintSha256.orEmpty()}"
