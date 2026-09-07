package com.videoflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.ai.WatermarkStudioPanel
import com.videoflow.app.ui.editor.VideoFlowEditorColors

/**
 * AI Watermark Studio and professional tools share the contextual rail.
 * Step-4 product integration around the approved editor. The AI entry is contextual: it exists only
 * while a real video clip is selected, and opens the functional local Watermark Studio workflow.
 */
@Composable
fun Step4WatermarkEditorRoute(
    id: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    editorVm: EditorViewModel
) {
    val editor by editorVm.editor.collectAsState()
    val project by editorVm.project.collectAsState()
    val selectedId by editorVm.selectedClipId.collectAsState()
    val playheadUs by editorVm.playheadUs.collectAsState()
    val currentEditor = editor
    val selected = currentEditor?.timeline?.clips?.firstOrNull { it.id == selectedId }
    val asset = project?.mediaAssets?.firstOrNull { it.id == selected?.assetId }
    val isVideoSelection = selected != null && asset != null &&
        (asset.videoCodecMime != null || asset.mimeType?.startsWith("video/") == true)
    var activeTool by androidx.compose.runtime.remember { mutableStateOf<com.videoflow.app.ui.editor.ProfessionalEditorTool?>(null) }

    LaunchedEffect(selectedId, isVideoSelection) {
        if (!isVideoSelection || activeTool?.clipId != selectedId) activeTool = null
    }

    Box(Modifier.fillMaxSize()) {
        FinalQualityEditorRoute(
            id = id,
            onBack = onBack,
            onExport = onExport,
            editorVm = editorVm,
            onProfessionalTool = { activeTool = it }
        )

        if (activeTool != null && selected != null && currentEditor != null && asset != null) {
            // Registered after the base editor's BackHandler so Back closes the AI surface first.
            BackHandler { activeTool = null }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight || maxWidth.value >= 700f
                val panelModifier = if (wide) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(min = 360.dp, max = 440.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.82f)
                }
                com.videoflow.app.ui.theme.VideoFlowTheme(com.videoflow.app.ui.product.AppAppearance.DARK) {
                Surface(
                    modifier = panelModifier,
                    contentColor = VideoFlowEditorColors.PrimaryText,
                    color = VideoFlowEditorColors.EditorSurfaceElevated,
                    tonalElevation = 12.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (activeTool is com.videoflow.app.ui.editor.ProfessionalEditorTool.AiWatermark) {
                        WatermarkStudioPanel(
                            projectId = id,
                            clipId = selected.id,
                            project = project,
                            editor = currentEditor,
                            playheadUs = playheadUs,
                            onDismiss = { activeTool = null },
                            refreshEditor = { editorVm.load(id) },
                            initialEffectId = (activeTool as? com.videoflow.app.ui.editor.ProfessionalEditorTool.AiWatermark)?.effectId
                        )
                        } else {
                            com.videoflow.app.ui.effects.ProfessionalToolPanel(id, requireNotNull(activeTool), selected, asset,
                                onClose = { activeTool = null }, onApplied = { editorVm.load(id) })
                        }
                    }
                }
                }
            }
        }
    }
}
