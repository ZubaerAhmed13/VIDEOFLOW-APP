package com.videoflow.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.model.ImportState
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.ui.ProjectViewModel
import com.videoflow.app.util.formatBytes
import com.videoflow.app.util.formatDurationUs

private val STEP2_MEDIA_MIME_TYPES = arrayOf("video/*", "audio/*", "image/*")
private val Step2Background = Color(0xFF0A0D12)
private val Step2Panel = Color(0xFF11161E)
private val Step2Panel2 = Color(0xFF171D27)
private val Step2Line = Color(0xFF293340)
private val Step2Muted = Color(0xFF8994A4)
private val Step2Green = Color(0xFF32D583)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step2ProjectScreen(
    id: String,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
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
        ImportState.Saving,
    )

    Scaffold(
        containerColor = Step2Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0E131A),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(27.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Step2Green,
                            contentColor = Color(0xFF06130C),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("V", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("PROJECT MEDIA", style = MaterialTheme.typography.labelSmall, color = Step2Green)
                            Text(project?.name ?: "VideoFlow", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier.padding(end = 8.dp),
                        color = Color(0xFF11271E),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, Color(0xFF244C3C)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.size(6.dp).background(Step2Green, RoundedCornerShape(99.dp)))
                            Text("LOCAL", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FE9BA))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Step2Panel),
                    border = BorderStroke(1.dp, Step2Line),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("MEDIA BIN", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667282))
                                Text("Referenced local sources", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "${project?.mediaAssets?.size ?: 0} ITEMS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Step2Muted,
                            )
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Button(
                                onClick = { vm.pickerOpened(); add.launch(STEP2_MEDIA_MIME_TYPES) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Add media" },
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (busy) "Working…" else "Import Media")
                            }
                            OutlinedButton(
                                onClick = onOpenEditor,
                                enabled = project != null,
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Open Editor" },
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Open Editor")
                            }
                        }

                        Text(
                            "Video, audio and image sources stay referenced through Android's document framework. Originals are never overwritten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Step2Muted,
                        )
                    }
                }
            }

            items(project?.mediaAssets.orEmpty(), key = { it.id }) { media ->
                Step2MediaCard(
                    media = media,
                    onRelink = {
                        relinkAsset = media.id
                        vm.pickerOpened()
                        relink.launch(STEP2_MEDIA_MIME_TYPES)
                    },
                )
            }

            if (project?.mediaAssets.isNullOrEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Step2Panel),
                        border = BorderStroke(1.dp, Step2Line),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                color = Color(0xFF173126),
                                shape = RoundedCornerShape(7.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Step2Green)
                                }
                            }
                            Text("Media bin is empty", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Import video, audio or an image to begin editing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Step2Muted,
                            )
                        }
                    }
                }
            }
        }
    }

    message?.let { current ->
        AlertDialog(
            containerColor = Step2Panel2,
            onDismissRequest = vm::clearMessage,
            title = { Text("VideoFlow") },
            text = { Text(current) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
        )
    }

    pendingDuplicate?.let {
        AlertDialog(
            containerColor = Step2Panel2,
            onDismissRequest = vm::cancelDuplicate,
            title = { Text("Media already in project") },
            text = { Text("The same source is already referenced. Add another reference only if you intentionally need a duplicate media-bin item.") },
            confirmButton = { TextButton(onClick = { vm.confirmDuplicate(id) }) { Text("Add Anyway") } },
            dismissButton = { TextButton(onClick = vm::cancelDuplicate) { Text("Cancel") } },
        )
    }

    pendingWeakRelink?.let { validation ->
        AlertDialog(
            containerColor = Step2Panel2,
            onDismissRequest = vm::cancelWeakRelink,
            title = { Text("Weak source verification") },
            text = { Text(validation.reason) },
            confirmButton = { TextButton(onClick = { vm.confirmWeakRelink(id) }) { Text("Use This Source") } },
            dismissButton = { TextButton(onClick = vm::cancelWeakRelink) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Step2MediaCard(media: MediaAsset, onRelink: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Step2Panel),
        border = BorderStroke(1.dp, Step2Line),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(media.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(mediaTypeLabel(media.mimeType).uppercase(), style = MaterialTheme.typography.labelSmall, color = Step2Green)
                }
                val sourceAvailable = media.sourceStatus.name == "AVAILABLE"
                Surface(
                    color = if (sourceAvailable) Color(0xFF173126) else Color(0xFF3A1719),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        media.sourceStatus.name.replace('_', ' '),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sourceAvailable) Color(0xFFB5EFCE) else MaterialTheme.colorScheme.error,
                    )
                }
            }

            val technical = buildList {
                if (media.width != null && media.height != null) add("${media.width}×${media.height}")
                media.durationUs?.let { add(formatDurationUs(it)) }
                media.sizeBytes?.let { add(formatBytes(it)) }
            }.joinToString(" • ")
            if (technical.isNotBlank()) {
                Text(technical, style = MaterialTheme.typography.bodySmall, color = Step2Muted)
            }

            Surface(color = Step2Panel2, shape = RoundedCornerShape(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("SOURCE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667282))
                    Text(media.sourceStatus.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(color = Step2Panel2, shape = RoundedCornerShape(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("PROXY", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667282))
                    Text("NOT GENERATED", style = MaterialTheme.typography.labelSmall, color = Step2Muted)
                }
            }

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
