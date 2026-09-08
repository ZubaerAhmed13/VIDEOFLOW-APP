package com.videoflow.app.ui.screens

import androidx.compose.runtime.Composable
import com.videoflow.app.ui.EditorViewModel
import com.videoflow.app.ui.editor.ProfessionalEditorTool

/** Final editor route. Precise trim is intentionally part of the normal Trim tool. */
@Composable
fun FinalQualityEditorRoute(
    id: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    editorVm: EditorViewModel,
    onProfessionalTool: (ProfessionalEditorTool) -> Unit = {}
) {
    EditorScreen(
        id = id,
        onBack = onBack,
        onExport = onExport,
        vm = editorVm,
        onProfessionalTool = onProfessionalTool
    )
}
