package com.videoflow.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.model.ImportState
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.ui.ProjectViewModel
import com.videoflow.app.util.formatBytes
import com.videoflow.app.util.formatDurationUs

private val STEP2_MEDIA_MIME_TYPES = arrayOf("video/*", "audio/*", "image/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step2ProjectScreen(
    id: String,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onExport: () -> Unit,
    vm: ProjectViewModel
) {
    val project by vm.project.collectAsState()
    val importState by vm.importState.collectAsState()
    val message by vm.message.collectAsState()
    val pendingDuplicate by vm.pendingDuplicate.collectAsState()
    val pendingWeakRelink by vm.pendingWeakRelink.collectAsState()
    var relinkAsset by remember { mutableStateOf<String?>(null) }

    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addMedia(id, uri) else vm.pickerCancelled()
    }
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val assetId = relinkAsset
        if (uri != null && assetId != null) vm.relink(id, assetId, uri) else vm.pickerCancelled()
        relinkAsset = null
    }

    LaunchedEffect(id) { vm.load(id) }
    val busy = importState in setOf(
        ImportState.Selecting,
        ImportState.Opening,
        ImportState.ReadingMetadata,
        ImportState.Fingerprinting,
        ImportState.Saving
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Project Media") },
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.pickerOpened(); add.launch(STEP2_MEDIA_MIME_TYPES) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (busy) "Working…" else "Import Media")
                        }
                        Button(
                            onClick = onOpenEditor,
                            enabled = project != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open Editor")
                        }
                    }
                    OutlinedButton(
                        onClick = onExport,
                        enabled = project != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Professional Export")
                    }
                }
            }

            item {
                Text(
                    "Project Media Bin",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Video, audio and image sources remain referenced through Android's document framework. Originals are never overwritten.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            items(project?.mediaAssets.orEmpty(), key = { it.id }) { media ->
                Step2MediaCard(
                    media = media,
                    onRelink = {
                        relinkAsset = media.id
                        vm.pickerOpened()
                        relink.launch(STEP2_MEDIA_MIME_TYPES)
                    }
                )
            }

            if (project?.mediaAssets.isNullOrEmpty()) {
                item { Text("No media yet. Import video, audio or an image to begin editing.") }
            }
        }
    }

    message?.let { current ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("VideoFlow") },
            text = { Text(current) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } }
        )
    }

    pendingDuplicate?.let {
        AlertDialog(
            onDismissRequest = vm::cancelDuplicate,
            title = { Text("Media already in project") },
            text = { Text("The same source is already referenced. Add another reference only if you intentionally need a duplicate media-bin item.") },
            confirmButton = { TextButton(onClick = { vm.confirmDuplicate(id) }) { Text("Add Anyway") } },
            dismissButton = { TextButton(onClick = vm::cancelDuplicate) { Text("Cancel") } }
        )
    }

    pendingWeakRelink?.let { validation ->
        AlertDialog(
            onDismissRequest = vm::cancelWeakRelink,
            title = { Text("Weak source verification") },
            text = { Text(validation.reason) },
            confirmButton = { TextButton(onClick = { vm.confirmWeakRelink(id) }) { Text("Use This Source") } },
            dismissButton = { TextButton(onClick = vm::cancelWeakRelink) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Step2MediaCard(media: MediaAsset, onRelink: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(media.displayName, style = MaterialTheme.typography.titleSmall)
            Text(mediaTypeLabel(media.mimeType))
            val technical = buildList {
                if (media.width != null && media.height != null) add("${media.width}×${media.height}")
                media.durationUs?.let { add(formatDurationUs(it)) }
                media.sizeBytes?.let { add(formatBytes(it)) }
            }.joinToString(" • ")
            if (technical.isNotBlank()) Text(technical, style = MaterialTheme.typography.bodySmall)
            Text("Source: ${media.sourceStatus.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
            Text("Proxy generation and status are managed in the Editor; final export always resolves the original source.", style = MaterialTheme.typography.bodySmall)
            if (media.sourceStatus.name != "AVAILABLE") {
                OutlinedButton(onClick = onRelink) { Text("Locate Source") }
            }
        }
    }
}

private fun mediaTypeLabel(mime: String?): String = when {
    mime?.startsWith("video/") == true -> "Video"
    mime?.startsWith("audio/") == true -> "Audio"
    mime?.startsWith("image/") == true -> "Image"
    else -> mime ?: "Media"
}
