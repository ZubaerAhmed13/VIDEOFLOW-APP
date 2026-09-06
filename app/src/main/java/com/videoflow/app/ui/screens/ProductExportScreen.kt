package com.videoflow.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.videoflow.app.ui.product.codecLabel
import com.videoflow.app.ui.product.codecSupporting
import com.videoflow.app.ui.product.exportFailurePresentation
import com.videoflow.app.ui.product.exportStatusLabel
import com.videoflow.app.ui.product.hdrPolicyLabel
import com.videoflow.app.ui.product.qualityLabel
import com.videoflow.app.ui.product.resolutionLabel
import com.videoflow.app.ui.product.sanitizeExportFileName
import com.videoflow.app.util.formatBytes
import com.videoflow.app.util.formatDurationUs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SIMPLE_RESOLUTIONS = listOf(
    ExportResolutionPreset.SOURCE,
    ExportResolutionPreset.P720,
    ExportResolutionPreset.P1080,
    ExportResolutionPreset.P1440,
    ExportResolutionPreset.UHD_4K
)
private val ADVANCED_RESOLUTIONS = ExportResolutionPreset.entries
private val FRAME_RATES: List<Pair<String, FrameRate?>> = listOf(
    "Same as Project" to null,
    "23.976" to FrameRate(24_000, 1_001),
    "24" to FrameRate.FPS_24,
    "25" to FrameRate.FPS_25,
    "29.97" to FrameRate.FPS_2997,
    "30" to FrameRate.FPS_30,
    "50" to FrameRate(50, 1),
    "59.94" to FrameRate.FPS_5994,
    "60" to FrameRate.FPS_60
)
private val AUDIO_BITRATES = listOf(128_000, 192_000, 256_000, 320_000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductExportScreen(
    id: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: ExportViewModel
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var cancelDialog by rememberSaveable { mutableStateOf(false) }
    var fileName by rememberSaveable {
        mutableStateOf("VideoFlow_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.mp4")
    }
    var sessionJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingSince by rememberSaveable { mutableStateOf<Long?>(null) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        vm.setDestination(uri)
    }

    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(state.jobs, waitingSince) {
        val since = waitingSince
        if (since != null && sessionJobId == null) {
            state.jobs.firstOrNull { it.createdAt >= since - 1_000L }?.let { newest ->
                sessionJobId = newest.id
                waitingSince = null
            }
        }
    }

    val sessionJob = sessionJobId?.let { wanted -> state.jobs.firstOrNull { it.id == wanted } }
    LaunchedEffect(sessionJob?.status) {
        if (sessionJob?.status == ExportJobStatus.CANCELLED) sessionJobId = null
    }

    if (sessionJob?.status == ExportJobStatus.COMPLETED) {
        ExportSuccessScreen(sessionJob, state, context, onDone)
        return
    }
    if (sessionJob?.status == ExportJobStatus.FAILED || sessionJob?.status == ExportJobStatus.INTERRUPTED) {
        ExportFailureScreen(
            job = sessionJob,
            onChangeSettings = { sessionJobId = null; advancedOpen = true },
            onRetry = {
                waitingSince = System.currentTimeMillis()
                sessionJobId = null
                vm.startExport(fileName)
            },
            onBack = { sessionJobId = null }
        )
        return
    }

    val active = state.activeJob
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (active == null) "Export Video" else "Exporting Video") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (active != null) {
            ExportProgressContent(
                job = active,
                modifier = Modifier.fillMaxSize().padding(padding),
                onCancel = { cancelDialog = true }
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val horizontal = if (maxWidth >= 600.dp) 32.dp else 16.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = horizontal, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Recommended export", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("Simple by default. Advanced codec, bitrate and colour controls stay available when you need them.", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = fileName,
                                    onValueChange = { fileName = it.take(96) },
                                    label = { Text("File name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    item {
                        SimpleChoice("Resolution", SIMPLE_RESOLUTIONS, state.requested.resolutionPreset, ::resolutionLabel, vm::setResolution)
                        if (state.requested.resolutionPreset == ExportResolutionPreset.SOURCE) {
                            Text("Recommended • preserves the project canvas size", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { SimpleChoice("Quality", ExportQuality.entries, state.requested.quality, ::qualityLabel, vm::setQuality) }
                    item { ExportSummaryCard(state) }
                    if (state.warnings.isNotEmpty()) {
                        item {
                            NoticeCard(
                                title = "Before exporting",
                                lines = state.warnings.map { warning ->
                                    if (warning.code == "UPSCALE") "This will enlarge the video but cannot create additional source detail." else warning.message
                                }
                            )
                        }
                    }
                    if (state.problems.isNotEmpty()) {
                        item {
                            val presentations = state.problems.map { exportFailurePresentation(it.code, it.message) }
                            NoticeCard("Export needs attention", presentations.flatMap { listOf(it.title, it.message) })
                        }
                    }
                    item {
                        OutlinedButton(onClick = { advancedOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Advanced Settings")
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Save location", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (state.destinationUri == null) "Choose where Android should create the MP4." else "Destination selected through Android's document picker.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = { createDocument.launch(sanitizeExportFileName(fileName)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (state.destinationUri == null) "Choose Save Location" else "Change Save Location") }
                                Button(
                                    onClick = {
                                        fileName = sanitizeExportFileName(fileName)
                                        waitingSince = System.currentTimeMillis()
                                        vm.startExport(fileName)
                                    },
                                    enabled = state.canStart,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Export Video") }
                            }
                        }
                    }
                    item { Text("Export History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    if (state.jobs.isEmpty()) item { Text("No exports yet.", style = MaterialTheme.typography.bodySmall) }
                    else items(state.jobs.take(20), key = { it.id }) { job -> ProductExportHistoryCard(job, context) }
                }
            }
        }
    }

    if (advancedOpen) AdvancedExportSheet(state, vm, onDismiss = { advancedOpen = false })

    if (cancelDialog) {
        AlertDialog(
            onDismissRequest = { cancelDialog = false },
            title = { Text("Cancel export?") },
            text = { Text("The unfinished video will be removed when possible.") },
            confirmButton = { TextButton(onClick = { cancelDialog = false; vm.cancelActiveExport() }) { Text("Cancel Export") } },
            dismissButton = { Button(onClick = { cancelDialog = false }) { Text("Keep Exporting") } }
        )
    }
}

@Composable
private fun ExportSummaryCard(state: ExportUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Export summary", style = MaterialTheme.typography.titleMedium)
            state.resolved?.let { resolved ->
                Text("${resolved.size.width} × ${resolved.size.height}")
                Text("Frame Rate • ${formatFps(resolved.frameRate)} fps")
                Text("Codec • ${codecLabel(resolved.videoCodec)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Quality • ${qualityLabel(state.requested.quality)}")
            Text(
                "Estimated Size • ${state.estimate?.let { "Approximately ${formatBytes(it.payloadBytes)}" } ?: "Size estimate unavailable"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Final export resolves original media, not editing proxies.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun <T> SimpleChoice(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option -> FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) }) }
        }
    }
}

@Composable
private fun NoticeCard(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            lines.distinct().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedExportSheet(state: ExportUiState, vm: ExportViewModel, onDismiss: () -> Unit) {
    var customWidth by rememberSaveable { mutableStateOf((state.requested.customWidth ?: 1920).toString()) }
    var customHeight by rememberSaveable { mutableStateOf((state.requested.customHeight ?: 1080).toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("Advanced Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item { Text("Video", style = MaterialTheme.typography.titleMedium) }
            item {
                SimpleChoice("Codec", VideoCodec.entries, state.requested.videoCodec, ::codecLabel, vm::setVideoCodec)
                Text(codecSupporting(state.requested.videoCodec), style = MaterialTheme.typography.bodySmall)
            }
            item { SimpleChoice("Resolution", ADVANCED_RESOLUTIONS, state.requested.resolutionPreset, ::resolutionLabel, vm::setResolution) }
            if (state.requested.resolutionPreset == ExportResolutionPreset.CUSTOM) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                val selected = FRAME_RATES.firstOrNull { it.second == state.requested.frameRate } ?: FRAME_RATES.first()
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Frame Rate", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FRAME_RATES) { option ->
                            FilterChip(selected = option.second == selected.second, onClick = { vm.setFrameRate(option.second) }, label = { Text(option.first) })
                        }
                    }
                }
            }
            item { SimpleChoice("Quality", ExportQuality.entries, state.requested.quality, ::qualityLabel, vm::setQuality) }
            item { SimpleChoice("Bitrate Mode", BitrateMode.entries, state.requested.bitrateMode, { it.name }, vm::setBitrateMode) }
            item { state.resolved?.let { Text("Resolved video bitrate • ${formatMbps(it.videoBitrate)} Mbps", style = MaterialTheme.typography.bodySmall) } }
            item { HorizontalDivider() }
            item { Text("Audio", style = MaterialTheme.typography.titleMedium) }
            item { SimpleChoice("AAC Bitrate", AUDIO_BITRATES, state.requested.audioBitrate, { "${it / 1000} kbps" }, vm::setAudioBitrate) }
            item { Text("48 kHz • Stereo", style = MaterialTheme.typography.bodySmall) }
            item { HorizontalDivider() }
            item { Text("Colour", style = MaterialTheme.typography.titleMedium) }
            item { SimpleChoice("Colour / HDR", HdrPolicy.entries, state.requested.hdrPolicy, ::hdrPolicyLabel, vm::setHdrPolicy) }
            if (state.problems.isNotEmpty()) {
                item { NoticeCard("Current combination is not available", state.problems.map { exportFailurePresentation(it.code, it.message).message }) }
            }
            item { OutlinedButton(onClick = vm::resetRecommended, modifier = Modifier.fillMaxWidth()) { Text("Reset to Recommended") } }
            item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
        }
    }
}

@Composable
private fun ExportProgressContent(job: ExportJob, modifier: Modifier, onCancel: () -> Unit) {
    val progress = job.progress.coerceIn(0f, 1f)
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.2f))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
        )
        Text(exportStatusLabel(job.status), style = MaterialTheme.typography.titleMedium)
        Text("You can leave this screen. VideoFlow will continue when Android allows the active export service to run.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Cancel Export")
        }
        Spacer(Modifier.weight(0.8f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSuccessScreen(job: ExportJob, state: ExportUiState, context: Context, onDone: () -> Unit) {
    val uri = remember(job.destinationUri) { Uri.parse(job.destinationUri) }
    Scaffold(topBar = { TopAppBar(title = { Text("Export Complete") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Text("Export Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(job.displayName, style = MaterialTheme.typography.titleMedium)
            state.resolved?.let { Text("${it.size.width}×${it.size.height} • ${formatFps(it.frameRate)} fps") }
            if (state.durationUs > 0L) Text(formatDurationUs(state.durationUs))
            Spacer(Modifier.padding(10.dp))
            Button(onClick = { openVideo(context, uri) }, modifier = Modifier.fillMaxWidth()) { Text("Open Video") }
            OutlinedButton(onClick = { shareVideo(context, uri) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFailureScreen(job: ExportJob, onChangeSettings: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    val presentation = exportFailurePresentation(job.failureCode, job.failureMessage)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(presentation.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(presentation.message)
            if (presentation.suggestions.isNotEmpty()) {
                Text("Try:", style = MaterialTheme.typography.titleSmall)
                presentation.suggestions.forEach { Text("• $it") }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onChangeSettings, modifier = Modifier.fillMaxWidth()) { Text("Change Settings") }
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
        }
    }
}

@Composable
private fun ProductExportHistoryCard(job: ExportJob, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(job.displayName, style = MaterialTheme.typography.titleSmall)
            Text(exportStatusLabel(job.status), style = MaterialTheme.typography.bodySmall)
            if (job.status in ExportUiState.ACTIVE_STATUSES) {
                LinearProgressIndicator(progress = { job.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${(job.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            if (job.status == ExportJobStatus.FAILED || job.status == ExportJobStatus.INTERRUPTED) {
                Text(exportFailurePresentation(job.failureCode, job.failureMessage).message, style = MaterialTheme.typography.bodySmall)
            }
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

private fun formatFps(rate: FrameRate): String =
    if (rate.denominator == 1) rate.numerator.toString() else String.format(Locale.US, "%.3f", rate.fps)
private fun formatMbps(value: Int): String = String.format(Locale.US, "%.1f", value / 1_000_000.0)

private fun openVideo(context: Context, uri: Uri) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
