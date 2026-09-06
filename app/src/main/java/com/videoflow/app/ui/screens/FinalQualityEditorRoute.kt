package com.videoflow.app.ui.screens

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.history.ClipHistoryEntry
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.editor.TrimTimecode
import com.videoflow.app.ui.EditorViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun FinalQualityEditorRoute(
    id: String,
    onBack: () -> Unit,
    onExport: () -> Unit,
    editorVm: EditorViewModel,
    preciseVm: PreciseTrimViewModel = hiltViewModel()
) {
    val editor by editorVm.editor.collectAsState()
    val project by editorVm.project.collectAsState()
    val selectedId by editorVm.selectedClipId.collectAsState()
    val selected = editor?.timeline?.clips?.firstOrNull { it.id == selectedId }
    val asset = project?.mediaAssets?.firstOrNull { it.id == selected?.assetId }
    var dialogOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        EditorScreen(id = id, onBack = onBack, onExport = onExport, vm = editorVm)
        if (selected != null && asset != null) {
            ExtendedFloatingActionButton(
                onClick = { dialogOpen = true },
                icon = { androidx.compose.material3.Icon(Icons.Default.ContentCut, contentDescription = null) },
                text = { Text("Precise Trim") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 86.dp)
                    .semantics { contentDescription = "Precise Trim, exact From and To time entry" }
            )
        }
    }

    if (dialogOpen && selected != null && asset != null) {
        PreciseTrimDialog(
            sourceUri = asset.sourceUri,
            sourceDurationUs = asset.durationUs ?: selected.sourceEndUs,
            initialStartUs = selected.sourceStartUs,
            initialEndUs = selected.sourceEndUs,
            onDismiss = { dialogOpen = false },
            onApply = { startUs, endUs ->
                preciseVm.commit(
                    projectId = id,
                    clipId = selected.id,
                    sourceUri = asset.sourceUri,
                    requestedStartUs = startUs,
                    requestedEndUs = endUs,
                    sourceDurationUs = asset.durationUs ?: selected.sourceEndUs,
                    onDone = { normalizedStart, _ ->
                        dialogOpen = false
                        editorVm.load(id)
                        editorVm.setPlayheadUs(selected.timelineStartUs + ((normalizedStart - selected.sourceStartUs).coerceAtLeast(0L)))
                    }
                )
            }
        )
    }
}

@Composable
private fun PreciseTrimDialog(
    sourceUri: String,
    sourceDurationUs: Long,
    initialStartUs: Long,
    initialEndUs: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    var startText by remember(sourceUri, initialStartUs) { mutableStateOf(TrimTimecode.formatUs(initialStartUs)) }
    var endText by remember(sourceUri, initialEndUs) { mutableStateOf(TrimTimecode.formatUs(initialEndUs)) }
    val parsedStart = TrimTimecode.parseToUs(startText).getOrNull()
    val parsedEnd = TrimTimecode.parseToUs(endText).getOrNull()
    val validation = if (parsedStart != null && parsedEnd != null) {
        TrimTimecode.validationMessage(parsedStart, parsedEnd, sourceDurationUs)
    } else null
    val parseError = when {
        parsedStart == null -> TrimTimecode.parseToUs(startText).exceptionOrNull()?.message ?: "Invalid From time."
        parsedEnd == null -> TrimTimecode.parseToUs(endText).exceptionOrNull()?.message ?: "Invalid To time."
        else -> null
    }
    val canApply = parsedStart != null && parsedEnd != null && validation == null
    val maxSeconds = (sourceDurationUs.coerceAtLeast(1L) / 1_000_000f).coerceAtLeast(0.001f)
    val rangeStart = ((parsedStart ?: initialStartUs) / 1_000_000f).coerceIn(0f, maxSeconds)
    val rangeEnd = ((parsedEnd ?: initialEndUs) / 1_000_000f).coerceIn(rangeStart.coerceAtMost(maxSeconds), maxSeconds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Precise Trim") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Drag the visual range or type exact From / To times. Values normalize to HH:MM:SS.mmm.")
                RangeSlider(
                    value = rangeStart..rangeEnd,
                    onValueChange = { range ->
                        val startUs = (range.start * 1_000_000.0).toLong().coerceAtLeast(0L)
                        val endUs = (range.endInclusive * 1_000_000.0).toLong().coerceAtMost(sourceDurationUs)
                        if (endUs > startUs) {
                            startText = TrimTimecode.formatUs(startUs)
                            endText = TrimTimecode.formatUs(endUs)
                        }
                    },
                    valueRange = 0f..maxSeconds,
                    modifier = Modifier.semantics { contentDescription = "Visual trim range" }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.take(16) },
                        label = { Text("From") },
                        supportingText = { Text("e.g. 00:00:03.500") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = parsedStart == null || validation != null,
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = "Trim start, ${parsedStart?.let(TrimTimecode::formatUs) ?: startText}"
                        }
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.take(16) },
                        label = { Text("To") },
                        supportingText = { Text("e.g. 00:00:18.250") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = parsedEnd == null || validation != null,
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = "Trim end, ${parsedEnd?.let(TrimTimecode::formatUs) ?: endText}"
                        }
                    )
                }
                if (parsedStart != null && parsedEnd != null && parsedEnd > parsedStart) {
                    Text("Duration  ${TrimTimecode.formatUs(parsedEnd - parsedStart)}", style = MaterialTheme.typography.bodyMedium)
                }
                (parseError ?: validation)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("Video boundaries are normalized to the nearest real source sample timestamp. VFR media uses actual sample timestamps, not rounded FPS.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onApply(parsedStart!!, parsedEnd!!) }, enabled = canApply) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@HiltViewModel
class PreciseTrimViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editorRepository: EditorRepository,
    private val projectRepository: ProjectRepository,
    private val historyService: EditHistoryService
) : ViewModel() {
    fun commit(
        projectId: String,
        clipId: String,
        sourceUri: String,
        requestedStartUs: Long,
        requestedEndUs: Long,
        sourceDurationUs: Long,
        onDone: (Long, Long) -> Unit
    ) {
        viewModelScope.launch {
            val validation = TrimTimecode.validationMessage(requestedStartUs, requestedEndUs, sourceDurationUs)
            if (validation != null) return@launch
            runCatching {
                val project = editorRepository.load(projectId)
                val before = project.timeline.clips.first { it.id == clipId }
                val beforeFrames = project.timeline.keyframes.filter { it.ownerId == clipId }
                val asset = projectRepository.getProject(projectId)?.mediaAssets?.firstOrNull { it.id == before.assetId }
                val isVideo = asset?.videoCodecMime != null || asset?.mimeType?.startsWith("video/") == true
                val normalizedStart = if (isVideo) nearestVideoSample(sourceUri, requestedStartUs, sourceDurationUs) else requestedStartUs
                val normalizedEnd = if (isVideo && requestedEndUs < sourceDurationUs) nearestVideoSample(sourceUri, requestedEndUs, sourceDurationUs) else requestedEndUs
                require(TrimTimecode.validationMessage(normalizedStart, normalizedEnd, sourceDurationUs) == null)

                var current = before
                // Choose the safe mutation order so expanding an existing trim remains possible.
                if (normalizedEnd <= current.sourceStartUs) {
                    if (normalizedStart != current.sourceStartUs) current = editorRepository.trimClipStart(projectId, clipId, normalizedStart)
                    if (normalizedEnd != current.sourceEndUs) current = editorRepository.trimClipEnd(projectId, clipId, normalizedEnd)
                } else {
                    if (normalizedEnd != current.sourceEndUs) current = editorRepository.trimClipEnd(projectId, clipId, normalizedEnd)
                    if (normalizedStart != current.sourceStartUs) current = editorRepository.trimClipStart(projectId, clipId, normalizedStart)
                }
                val fresh = editorRepository.load(projectId)
                val after = fresh.timeline.clips.first { it.id == clipId }
                val afterFrames = fresh.timeline.keyframes.filter { it.ownerId == clipId }
                if (after != before) {
                    historyService.record(
                        ClipHistoryEntry(projectId, "Precise Trim", listOf(before), listOf(after), beforeFrames, afterFrames)
                    )
                }
                normalizedStart to normalizedEnd
            }.onSuccess { (start, end) -> onDone(start, end) }
        }
    }

    private suspend fun nearestVideoSample(sourceUri: String, requestedUs: Long, sourceDurationUs: Long): Long = withContext(Dispatchers.IO) {
        if (requestedUs <= 0L) return@withContext 0L
        if (requestedUs >= sourceDurationUs) return@withContext sourceDurationUs
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(sourceUri), null)
            val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return@withContext requestedUs
            extractor.selectTrack(videoTrack)
            extractor.seekTo(requestedUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var previous = extractor.sampleTime.takeIf { it >= 0L } ?: return@withContext requestedUs
            var count = 0
            while (count++ < 20_000) {
                val current = extractor.sampleTime
                if (current < 0L) break
                if (current >= requestedUs) {
                    return@withContext if (abs(requestedUs - previous) <= abs(current - requestedUs)) previous else current
                }
                previous = current
                if (!extractor.advance()) break
            }
            previous.coerceIn(0L, sourceDurationUs)
        } finally {
            extractor.release()
        }
    }
}
