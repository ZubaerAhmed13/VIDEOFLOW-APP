package com.videoflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.export.ExportMode
import com.videoflow.app.ui.ExportViewModel

@Composable
fun FinalQualityExportRoute(
    id: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onReviewSource: () -> Unit,
    vm: ExportViewModel
) {
    val state by vm.state.collectAsState()
    var modeDialog by remember(id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ProfessionalExportRoute(id, onBack, onDone, onReviewSource, vm)
        if (state.activeJob == null && !state.loading) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
            ) {
                TextButton(onClick = { modeDialog = true }) {
                    Text("Export Mode: ${modeLabel(state.requested.mode)}")
                }
            }
        }
    }

    if (modeDialog) {
        AlertDialog(
            onDismissRequest = { modeDialog = false },
            title = { Text("Export Mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeChoice(
                        title = "Recommended",
                        body = "Balanced VideoFlow export settings for this project.",
                        selected = state.requested.mode == ExportMode.RECOMMENDED,
                        onClick = { vm.setExportMode(ExportMode.RECOMMENDED); modeDialog = false }
                    )
                    ModeChoice(
                        title = "Match Source",
                        body = "Keeps source/project resolution, rational frame rate, codec family and audio characteristics as closely as this device and renderer allow. Quality takes priority over matching file size.",
                        selected = state.requested.mode == ExportMode.MATCH_SOURCE,
                        onClick = { vm.setExportMode(ExportMode.MATCH_SOURCE); modeDialog = false }
                    )
                    if (state.smartCopyAvailable) {
                        ModeChoice(
                            title = "Smart Copy — No re-encoding",
                            body = "Available because this exact edit can preserve the original encoded video/audio packets without video re-encoding.",
                            selected = state.requested.mode == ExportMode.SMART_COPY,
                            onClick = { vm.setExportMode(ExportMode.SMART_COPY); modeDialog = false }
                        )
                    } else {
                        Text(
                            "Smart Copy is not available for this edit${state.smartCopyReason?.let { ": $it" } ?: "."}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    ModeChoice(
                        title = "Smaller File",
                        body = "Prioritizes a smaller rendered output.",
                        selected = state.requested.mode == ExportMode.SMALLER_FILE,
                        onClick = { vm.setExportMode(ExportMode.SMALLER_FILE); modeDialog = false }
                    )
                    ModeChoice(
                        title = "High Quality",
                        body = "Prioritizes rendered visual quality; output size may increase.",
                        selected = state.requested.mode == ExportMode.HIGH_QUALITY,
                        onClick = { vm.setExportMode(ExportMode.HIGH_QUALITY); modeDialog = false }
                    )
                    Text(
                        "Rendered edits cannot guarantee identical file size or mathematically lossless output.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { Button(onClick = { modeDialog = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun ModeChoice(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Column {
            Text(if (selected) "✓ $title" else title)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun modeLabel(mode: ExportMode): String = when (mode) {
    ExportMode.RECOMMENDED -> "Recommended"
    ExportMode.MATCH_SOURCE -> "Match Source"
    ExportMode.SMART_COPY -> "Smart Copy"
    ExportMode.SMALLER_FILE -> "Smaller File"
    ExportMode.HIGH_QUALITY -> "High Quality"
}
