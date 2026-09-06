package com.videoflow.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.ui.ExportViewModel
import com.videoflow.app.ui.product.exportFailurePresentation

private val SOURCE_RECOVERY_CODES = setOf(
    ExportFailureCode.SOURCE_MISSING,
    ExportFailureCode.SOURCE_CHANGED,
    ExportFailureCode.PERMISSION_LOST
)

@Composable
fun ProfessionalExportRoute(
    id: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onReviewSource: () -> Unit,
    vm: ExportViewModel
) {
    val state by vm.state.collectAsState()
    val sourceProblem = state.problems.firstOrNull { it.code in SOURCE_RECOVERY_CODES }
    var dismissedCode by remember(id) { mutableStateOf<ExportFailureCode?>(null) }

    ProductExportScreen(id = id, onBack = onBack, onDone = onDone, vm = vm)

    if (sourceProblem != null && sourceProblem.code != dismissedCode && state.activeJob == null) {
        val presentation = exportFailurePresentation(sourceProblem.code, sourceProblem.message)
        AlertDialog(
            onDismissRequest = { dismissedCode = sourceProblem.code },
            title = { Text(presentation.title) },
            text = { Text(presentation.message) },
            confirmButton = {
                Button(onClick = onReviewSource) {
                    Text(if (sourceProblem.code == ExportFailureCode.SOURCE_CHANGED) "Review Source" else "Locate Original")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissedCode = sourceProblem.code }) { Text("Not Now") }
            }
        )
    }
}
