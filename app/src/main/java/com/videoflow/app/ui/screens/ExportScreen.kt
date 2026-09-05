package com.videoflow.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.editor.FrameRate
import com.videoflow.app.domain.export.BitrateMode
import com.videoflow.app.domain.export.ExportJob
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportQuality
import com.videoflow.app.domain.export.ExportResolutionPreset
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.VideoCodec
import com.videoflow.app.ui.ExportUiState
import com.videoflow.app.ui.ExportViewModel
import com.videoflow.app.util.formatBytes

private val EXPORT_RESOLUTIONS = listOf(
    ExportResolutionPreset.SOURCE,
    ExportResolutionPreset.P480,
    ExportResolutionPreset.P720,
    ExportResolutionPreset.P1080,
    ExportResolutionPreset.P1440,
    ExportResolutionPreset.DCI_2K,
    ExportResolutionPreset.UHD_4K,
    ExportResolutionPreset.DCI_4K,
    ExportResolutionPreset.CUSTOM
)

private val EXPORT_FRAME_RATES: List<Pair<String, FrameRate?>> = listOf(
    "Project" to null,
    "23.976" to FrameRate(24_000, 1_001),
    "24" to FrameRate.FPS_24,
    "25" to FrameRate.FPS_25,
    "29.97" to FrameRate.FPS_2997,
    "30" to FrameRate.FPS_30,
    "50" to FrameRate(50, 1),
    "59.94" to FrameRate.FPS_5994,
    "60" to FrameRate.FPS_60
)

private val EXPORT_AUDIO_BITRATES = listOf(128_000, 192_000, 256_000, 320_000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    id: String,
    onBack: () -> Unit,
    vm: ExportViewModel
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var customWidth by rememberSaveable { mutableStateOf("1920") }
    var customHeight by rememberSaveable { mutableStateOf("1080") }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        vm.setDestination(uri)
    }

    LaunchedEffect(id) { vm.load(id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Professional Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Final render", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Exports are compiled from the original Android document sources. Editing proxies are never used as final-render media.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        state.resolved?.let { resolved ->
                            Text("Output: ${resolved.size.width}×${resolved.size.height} • ${formatFps(resolved.frameRate)} fps • ${resolved.videoCodec.name}")
                            Text("Video bitrate: ${resolved.videoBitrate / 1_000_000.0} Mb/s • Audio: ${resolved.audioBitrate / 1000} kb/s", style = MaterialTheme.typography.bodySmall)
                        }
                        state.estimate?.let { estimate ->
                            Text("Estimated space with safety margin: ${formatBytes(estimate.requiredBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                ChoiceSection(
                    title = "Resolution",
                    options = EXPORT_RESOLUTIONS,
                    selected = state.requested.resolutionPreset,
                    label = ::resolutionLabel,
                    onSelect = vm::setResolution
                )
            }

            if (state.requested.resolutionPreset == ExportResolutionPreset.CUSTOM) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customWidth,
                            onValueChange = { text ->
                                customWidth = text.filter(Char::isDigit).take(5)
                                customWidth.toIntOrNull()?.takeIf { it >= 2 }?.let(vm::setCustomWidth)
                            },
                            label = { Text("Width") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = { text ->
                                customHeight = text.filter(Char::isDigit).take(5)
                                customHeight.toIntOrNull()?.takeIf { it >= 2 }?.let(vm::setCustomHeight)
                            },
                            label = { Text("Height") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                val selectedFps = EXPORT_FRAME_RATES.firstOrNull { it.second == state.requested.frameRate } ?: EXPORT_FRAME_RATES.first()
                PairChoiceSection(
                    title = "Frame rate",
                    options = EXPORT_FRAME_RATES,
                    selected = selectedFps,
                    onSelect = { vm.setFrameRate(it.second) }
                )
            }

            item {
                ChoiceSection("Video codec", VideoCodec.entries, state.requested.videoCodec, { it.name }, vm::setVideoCodec)
            }
            item {
                ChoiceSection("Quality", ExportQuality.entries, state.requested.quality, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, vm::setQuality)
            }
            item {
                ChoiceSection("Bitrate mode", BitrateMode.entries, state.requested.bitrateMode, { it.name }, vm::setBitrateMode)
            }
            item {
                ChoiceSection(
                    "Audio",
                    EXPORT_AUDIO_BITRATES,
                    state.requested.audioBitrate,
                    { "${it / 1000} kbps AAC" },
                    vm::setAudioBitrate
                )
            }
            item {
                ChoiceSection("Colour / HDR", HdrPolicy.entries, state.requested.hdrPolicy, ::hdrLabel, vm::setHdrPolicy)
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Preflight", style = MaterialTheme.typography.titleMedium)
                        state.resolved?.let { resolved ->
                            Text("${resolved.size.width}×${resolved.size.height} • ${formatFps(resolved.frameRate)} fps • ${resolved.videoCodec.name}", style = MaterialTheme.typography.bodySmall)
                            Text("${resolved.videoBitrate / 1_000_000.0} Mb/s video • ${resolved.audioBitrate / 1000} kbps AAC", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.problems.isEmpty()) Text("Current settings pass software/device capability preflight.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.warnings.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Compatibility notes", style = MaterialTheme.typography.titleSmall)
                            state.warnings.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            if (state.problems.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Export blocked", style = MaterialTheme.typography.titleSmall)
                            state.problems.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Destination", style = MaterialTheme.typography.titleMedium)
                        Text(state.destinationUri?.toString() ?: "Choose where the MP4 should be created.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { createDocument.launch("VideoFlow_${System.currentTimeMillis()}.mp4") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.destinationUri == null) "Choose MP4 Destination" else "Change Destination") }
                        Button(
                            onClick = vm::startExport,
                            enabled = state.canStart,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start Native Export") }
                        if (state.jobs.any { it.status in ExportUiState.ACTIVE_STATUSES }) {
                            OutlinedButton(onClick = vm::cancelActiveExport, modifier = Modifier.fillMaxWidth()) {
                                Text("Cancel Active Export")
                            }
                        }
                    }
                }
            }

            item { Text("Export history", style = MaterialTheme.typography.titleMedium) }
            if (state.jobs.isEmpty()) {
                item { Text("No exports yet.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.jobs, key = { it.id }) { job ->
                ExportHistoryCard(job, context)
            }
        }
    }
}

@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
private fun PairChoiceSection(
    title: String,
    options: List<Pair<String, FrameRate?>>,
    selected: Pair<String, FrameRate?>,
    onSelect: (Pair<String, FrameRate?>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                FilterChip(selected = option.second == selected.second, onClick = { onSelect(option) }, label = { Text(option.first) })
            }
        }
    }
}

@Composable
private fun ExportHistoryCard(job: ExportJob, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(job.displayName, style = MaterialTheme.typography.titleSmall)
            Text(job.status.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
            if (job.status in ExportUiState.ACTIVE_STATUSES) {
                LinearProgressIndicator(progress = { job.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${(job.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            job.failureMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (job.status == ExportJobStatus.COMPLETED) {
                val uri = remember(job.destinationUri) { Uri.parse(job.destinationUri) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { openVideo(context, uri) }) { Text("Open") }
                    TextButton(onClick = { shareVideo(context, uri) }) { Text("Share") }
                }
            }
        }
    }
}

private fun resolutionLabel(value: ExportResolutionPreset): String = when (value) {
    ExportResolutionPreset.SOURCE -> "Project"
    ExportResolutionPreset.P480 -> "480p"
    ExportResolutionPreset.P720 -> "720p"
    ExportResolutionPreset.P1080 -> "1080p"
    ExportResolutionPreset.P1440 -> "1440p"
    ExportResolutionPreset.DCI_2K -> "2K DCI"
    ExportResolutionPreset.UHD_4K -> "4K UHD"
    ExportResolutionPreset.DCI_4K -> "4K DCI"
    ExportResolutionPreset.CUSTOM -> "Custom"
}

private fun hdrLabel(value: HdrPolicy): String = when (value) {
    HdrPolicy.PRESERVE_WHEN_COMPATIBLE -> "Preserve"
    HdrPolicy.REQUIRE_PRESERVE -> "Require HDR"
    HdrPolicy.CONVERT_TO_SDR -> "Convert to SDR"
}

private fun formatFps(rate: FrameRate): String =
    if (rate.denominator == 1) rate.numerator.toString() else String.format(java.util.Locale.US, "%.3f", rate.fps)

private fun openVideo(context: Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}

private fun shareVideo(context: Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share exported video"
            )
        )
    }
}
