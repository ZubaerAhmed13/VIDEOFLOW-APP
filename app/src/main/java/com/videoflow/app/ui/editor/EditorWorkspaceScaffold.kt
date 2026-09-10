package com.videoflow.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.rememberScrollState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry policy for the reusable editor shell. No media or feature logic belongs here. */
data class EditorShellMetrics(
    val trackRowHeight: Dp,
    val timelineViewportHeight: Dp,
    val minimumPreviewHeight: Dp
)

/**
 * Responsive portrait policy used by the production editor and deterministic geometry tests.
 * The timeline height is derived from exactly three track rows plus compact navigation/ruler
 * chrome, so track four and later never grow the editor vertically.
 */
fun editorShellMetrics(availableHeight: Dp): EditorShellMetrics {
    val trackRowHeight = when {
        availableHeight < 560.dp -> 64.dp
        availableHeight < 720.dp -> 72.dp
        else -> 76.dp
    }
    val timelineChrome = 36.dp + 48.dp
    val minimumPreviewHeight = when {
        availableHeight < 560.dp -> 150.dp
        availableHeight < 720.dp -> 190.dp
        else -> 220.dp
    }
    return EditorShellMetrics(
        trackRowHeight = trackRowHeight,
        timelineViewportHeight = timelineChrome + trackRowHeight * 3f,
        minimumPreviewHeight = minimumPreviewHeight
    )
}

/** Main portrait hierarchy: dominant preview, compact transport, bounded three-track timeline. */
@Composable
fun MainEditorPortraitScaffold(
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
    warning: @Composable () -> Unit = {},
    transport: @Composable () -> Unit,
    timeline: @Composable (trackRowHeight: Dp) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("editor-main-workspace")) {
        val metrics = editorShellMetrics(maxHeight)
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.minimumPreviewHeight)
                    .weight(1f)
                    .testTag("editor-preview-slot")
            ) { preview() }
            warning()
            transport()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(metrics.timelineViewportHeight)
                    .testTag("editor-timeline-slot")
            ) {
                timeline(metrics.trackRowHeight)
            }
        }
    }
}

/**
 * Whole-editor focused state. It replaces the normal timeline/tool carousel region instead of
 * stacking an overlay over it. On large/landscape layouts the focused controls move beside the
 * authoritative preview; portrait keeps the preview above a bounded focused-control region.
 */
@Composable
fun FocusedEditorWorkspace(
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
    warning: @Composable () -> Unit = {},
    transport: @Composable () -> Unit,
    focusedTool: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("editor-focused-workspace")) {
        val wide = maxWidth > maxHeight || maxWidth >= 700.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.64f).fillMaxHeight()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("editor-preview-slot")
                    ) { preview() }
                    warning()
                    transport()
                }
                Box(
                    Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                        .testTag("editor-focused-tool-slot")
                ) { focusedTool() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.54f)
                        .testTag("editor-preview-slot")
                ) { preview() }
                warning()
                transport()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.46f)
                        .testTag("editor-focused-tool-slot")
                ) { focusedTool() }
            }
        }
    }
}

/** Mutable callback registry scoped to one focused-tool composition. It contains no editor data. */
internal class FocusedToolActionRegistry {
    var onCancel: (() -> Unit)? = null
        private set
    var onReset: (() -> Unit)? = null
        private set
    var onDone: (() -> Unit)? = null
        private set

    fun clear() {
        onCancel = null
        onReset = null
        onDone = null
    }

    fun bind(onCancel: () -> Unit, onReset: (() -> Unit)?, onDone: () -> Unit) {
        this.onCancel = onCancel
        this.onReset = onReset
        this.onDone = onDone
    }
}

internal val LocalFocusedToolActionRegistry = staticCompositionLocalOf<FocusedToolActionRegistry?> { null }

/** Called by existing feature panels so their current commit/cancel semantics remain authoritative. */
@Composable
internal fun registerFocusedToolActions(
    onCancel: () -> Unit,
    onReset: (() -> Unit)?,
    onDone: () -> Unit
): Boolean {
    val registry = LocalFocusedToolActionRegistry.current ?: return false
    registry.bind(onCancel = onCancel, onReset = onReset, onDone = onDone)
    return true
}

/**
 * Reusable lower focused-tool scaffold for Trim/Speed/Crop/Effects/Enhance/AI and later tools.
 * Essential X/Done actions live outside the scrollable controls region and therefore never
 * disappear when secondary/advanced controls need vertical scrolling.
 */
@Composable
fun FocusedToolScaffold(
    modifier: Modifier = Modifier,
    toolKey: Any? = null,
    fallbackCancel: () -> Unit,
    timingSlot: (@Composable () -> Unit)? = null,
    advancedControls: (@Composable () -> Unit)? = null,
    primaryControls: @Composable () -> Unit
) {
    val registry = remember(toolKey) { FocusedToolActionRegistry() }
    registry.clear()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .semantics { contentDescription = "Focused tool workspace" }
            .testTag("focused-tool-scaffold"),
        color = VideoFlowEditorColors.EditorSurfaceElevated,
        tonalElevation = 4.dp
    ) {
        CompositionLocalProvider(LocalFocusedToolActionRegistry provides registry) {
            Column(Modifier.fillMaxSize()) {
                timingSlot?.invoke()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag("focused-tool-primary-controls")
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        primaryControls()
                        advancedControls?.invoke()
                    }
                }
                FocusedToolActionBar(
                    onCancel = registry.onCancel ?: fallbackCancel,
                    onReset = registry.onReset,
                    onDone = registry.onDone
                )
            }
        }
    }
}

@Composable
private fun FocusedToolActionBar(
    onCancel: () -> Unit,
    onReset: (() -> Unit)?,
    onDone: (() -> Unit)?
) {
    Surface(
        color = VideoFlowEditorColors.EditorSurface,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("focused-tool-action-bar")
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Cancel" }
                    .testTag("focused-tool-cancel")
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
            IconButton(
                onClick = { onDone?.invoke() },
                enabled = onDone != null,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Done" }
                    .testTag("focused-tool-done")
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
            }
        }
    }
}
