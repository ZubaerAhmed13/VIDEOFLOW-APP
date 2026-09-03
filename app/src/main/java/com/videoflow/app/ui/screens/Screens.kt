package com.videoflow.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.videoflow.app.BuildConfig
import com.videoflow.app.domain.model.ImportState
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.ui.DeviceViewModel
import com.videoflow.app.ui.HomeViewModel
import com.videoflow.app.ui.NativeVideoPlayer
import com.videoflow.app.ui.ProjectViewModel
import com.videoflow.app.util.formatBytes
import com.videoflow.app.util.formatDurationUs
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel
) {
    val projects by vm.projects.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Untitled Project") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VideoFlow") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = true },
                modifier = Modifier.semantics { contentDescription = "New Project" },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Project") }
            )
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Create a project to begin. Media stays on your device.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    Card(onClick = { onOpen(project.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.updatedAt))}"
                            )
                            Text("${project.mediaAssets.size} media item${if (project.mediaAssets.size == 1) "" else "s"}")
                            project.mediaAssets.firstOrNull()?.let { media ->
                                if (media.width != null && media.height != null) Text("${media.width}×${media.height}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("New Project") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Project name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.create(name) { id -> dialog = false; onOpen(id) } }) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(id: String, onBack: () -> Unit, vm: ProjectViewModel) {
    val project by vm.project.collectAsState()
    val importState by vm.importState.collectAsState()
    val message by vm.message.collectAsState()
    val isVerifying by vm.isVerifying.collectAsState()
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
    val isBusy = importState in setOf(
        ImportState.Selecting,
        ImportState.Opening,
        ImportState.ReadingMetadata,
        ImportState.Fingerprinting,
        ImportState.Saving
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(
                    onClick = { vm.pickerOpened(); add.launch(arrayOf("video/*")) },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Add media" }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(importStateLabel(importState))
                }
            }
            if (isVerifying) {
                item { Text("Checking source…", style = MaterialTheme.typography.bodyMedium) }
            }
            project?.mediaAssets?.forEach { media ->
                item(key = media.id) {
                    MediaCard(
                        media = media,
                        onLocate = {
                            relinkAsset = media.id
                            vm.pickerOpened()
                            relink.launch(arrayOf("video/*"))
                        }
                    )
                }
            }
        }
    }

    pendingDuplicate?.let {
        AlertDialog(
            onDismissRequest = vm::cancelDuplicate,
            title = { Text("Media already in project") },
            text = { Text("This media is already part of the project. No second reference has been saved yet.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.confirmDuplicate(id) },
                    modifier = Modifier.semantics { contentDescription = "Add duplicate media anyway" }
                ) { Text("Add Anyway") }
            },
            dismissButton = {
                TextButton(
                    onClick = vm::cancelDuplicate,
                    modifier = Modifier.semantics { contentDescription = "Cancel duplicate media" }
                ) { Text("Cancel") }
            }
        )
    }

    pendingWeakRelink?.let { validation ->
        AlertDialog(
            onDismissRequest = vm::cancelWeakRelink,
            title = { Text("VideoFlow cannot strongly verify this media") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("The selected document provider only allows weak identity verification. Technical characteristics match the saved source.")
                    validation.selectedName?.let { Text("Selected: $it") }
                    if (validation.selectedWidth != null && validation.selectedHeight != null) {
                        Text("Current: ${validation.selectedWidth}×${validation.selectedHeight} • ${formatDurationUs(validation.selectedDurationUs)} • ${formatBytes(validation.selectedSizeBytes)}")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.confirmWeakRelink(id) },
                    modifier = Modifier.semantics { contentDescription = "Use this weakly verified source" }
                ) { Text("Use This Source") }
            },
            dismissButton = {
                TextButton(
                    onClick = vm::cancelWeakRelink,
                    modifier = Modifier.semantics { contentDescription = "Cancel weak relink" }
                ) { Text("Cancel") }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
            text = { Text(text) }
        )
    }
}

@Composable
private fun MediaCard(media: MediaAsset, onLocate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(media.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${media.width ?: "?"}×${media.height ?: "?"} • ${formatDurationUs(media.durationUs)} • ${formatBytes(media.sizeBytes)}")
            Text("Video: ${media.videoCodecMime ?: "none"} • Audio: ${media.audioCodecMime ?: "none"}")
            Text("Tracks: ${media.videoTrackCount} video • ${media.audioTrackCount} audio")
            when (media.sourceStatus) {
                SourceStatus.AVAILABLE -> NativeVideoPlayer(media.sourceUri, Modifier.fillMaxWidth())
                SourceStatus.CHANGED -> {
                    Text(
                        "Original media appears to have changed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("VideoFlow can access this location, but the current media no longer matches the source saved with this project. Playback is blocked until the original is safely located.")
                    Text("Saved: ${media.displayName} • ${media.width ?: "?"}×${media.height ?: "?"} • ${formatDurationUs(media.durationUs)} • ${formatBytes(media.sizeBytes)}")
                    LocateOriginalButton(onLocate)
                }
                SourceStatus.UNKNOWN -> {
                    Text(
                        "Source identity could not be verified",
                        color = MaterialTheme.colorScheme.error
                    )
                    Text("VideoFlow can access this source but cannot currently verify it with the saved identity. Playback is blocked for safety.")
                    LocateOriginalButton(onLocate)
                }
                else -> {
                    Text(
                        "Original media unavailable\nVideoFlow cannot currently access this source (${media.sourceStatus.name}).",
                        color = MaterialTheme.colorScheme.error
                    )
                    LocateOriginalButton(onLocate)
                }
            }
            Text(
                "Access: ${if (media.permissionPersisted) "persisted URI permission" else "provider did not confirm persistent access"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Fingerprint: ${media.fingerprintStrength.name} • ${formatBytes(media.fingerprintSampledBytes)} sampled",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LocateOriginalButton(onLocate: () -> Unit) {
    Button(
        onClick = onLocate,
        modifier = Modifier.semantics { contentDescription = "Locate Original" }
    ) { Text("Locate Original") }
}

private fun importStateLabel(state: ImportState): String = when (state) {
    ImportState.Idle, ImportState.Ready, ImportState.Error, ImportState.Cancelled -> "Add Media"
    ImportState.Selecting -> "Selecting…"
    ImportState.Opening -> "Opening…"
    ImportState.ReadingMetadata -> "Reading metadata…"
    ImportState.Fingerprinting -> "Fingerprinting…"
    ImportState.Saving -> "Saving…"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onDevice: () -> Unit, onDiagnostics: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDevice,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Device Capability" }
            ) { Text("Device Capability") }
            OutlinedButton(
                onClick = onDiagnostics,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Diagnostics" }
            ) { Text("Diagnostics") }
            Text("Privacy: Step 1 performs media analysis and playback locally. No telemetry or upload SDK is included, and project metadata is excluded from Android backup/transfer.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCapabilityScreen(onBack: () -> Unit, vm: DeviceViewModel) {
    val device = vm.profile
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Capability") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Android API ${device.apiLevel}\n${device.manufacturer} ${device.model}\nArchitecture ${device.abis.joinToString()}\nCPU cores ${device.cpuCores}\nRAM ${formatBytes(device.totalRamBytes)}\nAvailable storage ${formatBytes(device.freeInternalBytes)}"
                )
            }
            items(device.codecs) { codec ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(codec.mime, style = MaterialTheme.typography.titleSmall)
                        Text("Decode ${support(codec.decoder, codec.hardwareDecoder)} • Encode ${support(codec.encoder, codec.hardwareEncoder)}")
                        Text("4K30 Decode ${yesNo(codec.decode4k30)} • Encode ${yesNo(codec.encode4k30)}")
                        Text("4K60 Decode ${yesNo(codec.decode4k60)} • Encode ${yesNo(codec.encode4k60)}")
                    }
                }
            }
        }
    }
}

private fun support(value: Boolean, hardware: Boolean?): String = if (!value) {
    "Not detected"
} else when (hardware) {
    true -> "Hardware supported"
    false -> "Software/vendor reported"
    null -> "Supported"
}

private fun yesNo(value: Boolean?): String = when (value) {
    true -> "Supported"
    false -> "Not supported"
    null -> "Not detected"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, vm: DeviceViewModel) {
    val device = vm.profile
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text("Build type ${BuildConfig.BUILD_TYPE}")
            Text("Android API ${device.apiLevel}")
            Text("ABI ${device.abis.joinToString()}")
            Text("RAM ${formatBytes(device.totalRamBytes)}")
            Text("Free storage ${formatBytes(device.freeInternalBytes)}")
            Text("Database version 1")
            Text("Persisted media read permissions ${device.persistedReadPermissionCount}")
            Text("Codec entries ${device.codecs.size}")
            Text("Bounded local diagnostic events ${vm.diagnostics.size}")
            Text("Runtime network permission: not requested")
            Text("Android backup/transfer: disabled")
        }
    }
}
