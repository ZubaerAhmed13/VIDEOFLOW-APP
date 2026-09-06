package com.videoflow.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoflow.app.ui.product.ProductHomeViewModel
import com.videoflow.app.util.formatDurationUs
import kotlin.math.abs

/** First-class consumer merge workflow backed by the normal VideoFlow project/timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeVideosScreen(
    onBack: () -> Unit,
    onProjectReady: (String) -> Unit,
    vm: ProductHomeViewModel
) {
    val candidates by vm.mergeCandidates.collectAsState()
    val busy by vm.mergeBusy.collectAsState()
    var name by rememberSaveable { mutableStateOf("Merged Video") }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val reorderThresholdPx = with(LocalDensity.current) { 52.dp.toPx() }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); error = null }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.prepareMergeSelection(uris, append = candidates.isNotEmpty()) { error = it }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Merge Videos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Select videos, drag to arrange their order, then create a normal VideoFlow project for preview, precise per-clip Trim, and export.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Merge project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (candidates.isEmpty()) "Select Videos" else "Add More Videos")
                }
            }
            if (candidates.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No videos selected", style = MaterialTheme.typography.titleMedium)
                            Text("Choose at least two videos. Originals remain referenced through Android; they are not copied into VideoFlow.")
                        }
                    }
                }
            } else {
                itemsIndexed(candidates, key = { _, item -> item.selectionId }) { index, item ->
                    var dragging by remember(item.selectionId) { mutableStateOf(false) }
                    var dragAccumulatedY by remember(item.selectionId) { mutableFloatStateOf(0f) }
                    var dragIndex by remember(item.selectionId) { mutableIntStateOf(index) }

                    LaunchedEffect(index, dragging) {
                        if (!dragging) dragIndex = index
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = if (dragging) 0.92f else 1f
                                scaleX = if (dragging) 1.01f else 1f
                                scaleY = if (dragging) 1.01f else 1f
                            }
                            .semantics {
                                contentDescription = "Video ${index + 1} of ${candidates.size}, ${item.displayName}"
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 1.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .semantics {
                                        contentDescription = "Drag video ${index + 1} to reorder. Long press, then drag up or down."
                                    }
                                    .pointerInput(item.selectionId, busy, candidates.size) {
                                        if (!busy) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dragging = true
                                                    dragAccumulatedY = 0f
                                                    dragIndex = index
                                                },
                                                onDragCancel = {
                                                    dragging = false
                                                    dragAccumulatedY = 0f
                                                },
                                                onDragEnd = {
                                                    dragging = false
                                                    dragAccumulatedY = 0f
                                                },
                                                onDrag = { _, dragAmount ->
                                                    dragAccumulatedY += dragAmount.y
                                                    while (abs(dragAccumulatedY) >= reorderThresholdPx) {
                                                        val direction = if (dragAccumulatedY > 0f) 1 else -1
                                                        val nextIndex = dragIndex + direction
                                                        if (nextIndex !in candidates.indices) {
                                                            dragAccumulatedY = 0f
                                                            break
                                                        }
                                                        vm.moveMergeCandidate(dragIndex, direction)
                                                        dragIndex = nextIndex
                                                        dragAccumulatedY -= reorderThresholdPx * direction
                                                    }
                                                }
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DragHandle, contentDescription = null)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                val details = buildList {
                                    item.durationUs?.let { add(formatDurationUs(it)) }
                                    if (item.width != null && item.height != null) add("${item.width}×${item.height}")
                                    item.videoCodecMime?.let { add(it.removePrefix("video/")) }
                                }.joinToString(" • ")
                                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(
                                onClick = { vm.moveMergeCandidate(index, -1) },
                                enabled = index > 0 && !busy,
                                modifier = Modifier.semantics { contentDescription = "Move video ${index + 1} up" }
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                            IconButton(
                                onClick = { vm.moveMergeCandidate(index, 1) },
                                enabled = index < candidates.lastIndex && !busy,
                                modifier = Modifier.semantics { contentDescription = "Move video ${index + 1} down" }
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                            IconButton(
                                onClick = { vm.removeMergeCandidate(index) },
                                enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "Remove video ${index + 1}" }
                            ) { Icon(Icons.Default.Close, contentDescription = null) }
                        }
                    }
                }
            }
            item {
                Text(
                    "Drag the handle to reorder quickly. Up/down controls remain available for keyboard, accessibility and one-step precision. After creation, preview the complete order in the editor. Tap any clip and use Trim for visual handles plus exact From/To time entry. Compatible projects can use Smart Copy at export; otherwise Match Source renders at high fidelity.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Button(
                    onClick = {
                        vm.createMergeProject(
                            name = name,
                            onDone = { onProjectReady(it.projectId) },
                            onError = { error = it }
                        )
                    },
                    enabled = candidates.size >= 2 && name.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Preparing Merge…" else "Create & Preview Merge")
                }
            }
        }
    }
}
