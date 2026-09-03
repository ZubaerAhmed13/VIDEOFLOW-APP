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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

private val WorkstationGreen = Color(0xFF32D583)
private val WorkstationPanel = Color(0xFF11161E)
private val WorkstationPanel2 = Color(0xFF171D27)
private val WorkstationLine = Color(0xFF293340)
private val WorkstationMuted = Color(0xFF8994A4)
private val WorkstationBackground = Color(0xFF0A0D12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfessionalTopBar(
    title: String,
    eyebrow: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF0E131A),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(27.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = WorkstationGreen,
                    contentColor = Color(0xFF06130C),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("V", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = WorkstationGreen)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
    )
}

@Composable
private fun LocalStatusPill() {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color(0xFF11271E),
        border = BorderStroke(1.dp, Color(0xFF244C3C)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).background(WorkstationGreen, RoundedCornerShape(99.dp)))
            Text("LOCAL", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FE9BA))
        }
    }
}

@Composable
private fun SectionHeading(eyebrow: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Color(0xFF667282))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        trailing?.invoke()
    }
}

@Composable
private fun WorkstationCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = WorkstationPanel),
            border = BorderStroke(1.dp, WorkstationLine),
            shape = RoundedCornerShape(6.dp),
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = WorkstationPanel),
            border = BorderStroke(1.dp, WorkstationLine),
            shape = RoundedCornerShape(6.dp),
        ) { content() }
    }
}

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
        containerColor = WorkstationBackground,
        topBar = {
            ProfessionalTopBar(
                title = "VideoFlow",
                eyebrow = "PROFESSIONAL WORKSTATION",
                actions = {
                    LocalStatusPill()
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                SectionHeading(
                    eyebrow = "LOCAL PROJECTS",
                    title = "Your workspace",
                    trailing = {
                        Button(
                            onClick = { dialog = true },
                            modifier = Modifier.semantics { contentDescription = "New Project" },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Project")
                        }
                    },
                )
            }

            if (projects.isEmpty()) {
                item {
                    WorkstationCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF173126),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = WorkstationGreen)
                                }
                            }
                            Text("Create your first project", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Media stays on this device. VideoFlow keeps project references and editing state local.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WorkstationMuted,
                            )
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    WorkstationCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpen(project.id) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                color = WorkstationPanel2,
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, WorkstationLine),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("VF", color = WorkstationGreen, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(project.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.updatedAt))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WorkstationMuted,
                                )
                                Text(
                                    "${project.mediaAssets.size} media item${if (project.mediaAssets.size == 1) "" else "s"}" +
                                        (project.mediaAssets.firstOrNull()?.let { media ->
                                            if (media.width != null && media.height != null) " • ${media.width}×${media.height}" else ""
                                        } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                color = Color(0xFF173126),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "OPEN",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB5EFCE),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(5.dp))
                Text(
                    "PRIVACY-FIRST • NON-DESTRUCTIVE • LOCAL MEDIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF5F6B79),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }

    if (dialog) {
        AlertDialog(
            containerColor = WorkstationPanel2,
            onDismissRequest = { dialog = false },
            title = { Text("New Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create a local, non-destructive VideoFlow workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WorkstationMuted,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Project name") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.create(name) { id -> dialog = false; onOpen(id) } }) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
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
        ImportState.Saving,
    )

    Scaffold(
        containerColor = WorkstationBackground,
        topBar = {
            ProfessionalTopBar(
                title = project?.name ?: "Project",
                eyebrow = "MEDIA WORKSPACE",
                onBack = onBack,
                actions = { LocalStatusPill() },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                WorkstationCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("MEDIA BIN", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667282))
                            Text(
                                "Original sources remain referenced",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Button(
                            onClick = { vm.pickerOpened(); add.launch(arrayOf("video/*")) },
                            enabled = !isBusy,
                            modifier = Modifier.semantics { contentDescription = "Add media" },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(importStateLabel(importState))
                        }
                    }
                }
            }

            if (isVerifying) {
                item {
                    Surface(
                        color = Color(0xFF11271E),
                        border = BorderStroke(1.dp, Color(0xFF244C3C)),
                        shape = RoundedCornerShape(5.dp),
                    ) {
                        Text(
                            "Checking source identity…",
                            modifier = Modifier.fillMaxWidth().padding(9.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8FE9BA),
                        )
                    }
                }
            }

            if (project?.mediaAssets.isNullOrEmpty()) {
                item {
                    WorkstationCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("No media in this project", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Use Add Media to reference a source without copying the full file into the project.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WorkstationMuted,
                            )
                        }
                    }
                }
            }

            project?.mediaAssets?.forEach { media ->
                item(key = media.id) {
                    MediaCard(
                        media = media,
                        onLocate = {
                            relinkAsset = media.id
                            vm.pickerOpened()
                            relink.launch(arrayOf("video/*"))
                        },
                    )
                }
            }
        }
    }

    pendingDuplicate?.let {
        AlertDialog(
            containerColor = WorkstationPanel2,
            onDismissRequest = vm::cancelDuplicate,
            title = { Text("Media already in project") },
            text = { Text("This media is already part of the project. No second reference has been saved yet.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.confirmDuplicate(id) },
                    modifier = Modifier.semantics { contentDescription = "Add duplicate media anyway" },
                ) { Text("Add Anyway") }
            },
            dismissButton = {
                TextButton(
                    onClick = vm::cancelDuplicate,
                    modifier = Modifier.semantics { contentDescription = "Cancel duplicate media" },
                ) { Text("Cancel") }
            },
        )
    }

    pendingWeakRelink?.let { validation ->
        AlertDialog(
            containerColor = WorkstationPanel2,
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
                    modifier = Modifier.semantics { contentDescription = "Use this weakly verified source" },
                ) { Text("Use This Source") }
            },
            dismissButton = {
                TextButton(
                    onClick = vm::cancelWeakRelink,
                    modifier = Modifier.semantics { contentDescription = "Cancel weak relink" },
                ) { Text("Cancel") }
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            containerColor = WorkstationPanel2,
            onDismissRequest = vm::clearMessage,
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
            text = { Text(text) },
        )
    }
}

@Composable
private fun MediaCard(media: MediaAsset, onLocate: () -> Unit) {
    WorkstationCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(media.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${media.width ?: "?"}×${media.height ?: "?"} • ${formatDurationUs(media.durationUs)} • ${formatBytes(media.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WorkstationMuted,
                    )
                }
                Surface(
                    color = when (media.sourceStatus) {
                        SourceStatus.AVAILABLE -> Color(0xFF173126)
                        else -> Color(0xFF3A1719)
                    },
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        media.sourceStatus.name.replace('_', ' '),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (media.sourceStatus == SourceStatus.AVAILABLE) Color(0xFFB5EFCE) else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                "Video ${media.videoCodecMime ?: "none"} • Audio ${media.audioCodecMime ?: "none"} • ${media.videoTrackCount}V/${media.audioTrackCount}A",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (media.sourceStatus) {
                SourceStatus.AVAILABLE -> {
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, WorkstationLine),
                    ) {
                        NativeVideoPlayer(media.sourceUri, Modifier.fillMaxWidth())
                    }
                }
                SourceStatus.CHANGED -> {
                    Text("Original media appears to have changed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "VideoFlow can access this location, but the current media no longer matches the source saved with this project. Playback is blocked until the original is safely located.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WorkstationMuted,
                    )
                    Text(
                        "Saved: ${media.displayName} • ${media.width ?: "?"}×${media.height ?: "?"} • ${formatDurationUs(media.durationUs)} • ${formatBytes(media.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LocateOriginalButton(onLocate)
                }
                SourceStatus.UNKNOWN -> {
                    Text("Source identity could not be verified", color = MaterialTheme.colorScheme.error)
                    Text(
                        "VideoFlow can access this source but cannot currently verify it with the saved identity. Playback is blocked for safety.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WorkstationMuted,
                    )
                    LocateOriginalButton(onLocate)
                }
                else -> {
                    Text(
                        "Original media unavailable\nVideoFlow cannot currently access this source (${media.sourceStatus.name}).",
                        color = MaterialTheme.colorScheme.error,
                    )
                    LocateOriginalButton(onLocate)
                }
            }

            Surface(color = WorkstationPanel2, shape = RoundedCornerShape(4.dp)) {
                Column(Modifier.fillMaxWidth().padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "ACCESS  ${if (media.permissionPersisted) "PERSISTED URI" else "SESSION/PROVIDER"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8F9BAA),
                    )
                    Text(
                        "FINGERPRINT  ${media.fingerprintStrength.name} • ${formatBytes(media.fingerprintSampledBytes)} sampled",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8F9BAA),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocateOriginalButton(onLocate: () -> Unit) {
    Button(
        onClick = onLocate,
        modifier = Modifier.semantics { contentDescription = "Locate Original" },
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
        containerColor = WorkstationBackground,
        topBar = { ProfessionalTopBar(title = "Settings", eyebrow = "VIDEOFLOW SYSTEM", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SectionHeading("SYSTEM", "Local workstation settings")
            WorkstationCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDevice,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Device Capability" },
                    ) { Text("Device Capability") }
                    OutlinedButton(
                        onClick = onDiagnostics,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Diagnostics" },
                    ) { Text("Diagnostics") }
                    Text(
                        "Privacy: media analysis and playback run locally. No telemetry or upload SDK is included, and project metadata is excluded from Android backup/transfer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WorkstationMuted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCapabilityScreen(onBack: () -> Unit, vm: DeviceViewModel) {
    val device = vm.profile
    Scaffold(
        containerColor = WorkstationBackground,
        topBar = { ProfessionalTopBar(title = "Device Capability", eyebrow = "SYSTEM PROFILE", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                WorkstationCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("DEVICE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF667282))
                        Text("${device.manufacturer} ${device.model}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Android API ${device.apiLevel} • ${device.abis.joinToString()} • ${device.cpuCores} CPU cores",
                            style = MaterialTheme.typography.bodySmall,
                            color = WorkstationMuted,
                        )
                        Text(
                            "RAM ${formatBytes(device.totalRamBytes)} • Free storage ${formatBytes(device.freeInternalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WorkstationMuted,
                        )
                    }
                }
            }
            items(device.codecs) { codec ->
                WorkstationCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(codec.mime, style = MaterialTheme.typography.titleSmall)
                        Text("Decode ${support(codec.decoder, codec.hardwareDecoder)} • Encode ${support(codec.encoder, codec.hardwareEncoder)}", style = MaterialTheme.typography.bodySmall)
                        Text("4K30 Decode ${yesNo(codec.decode4k30)} • Encode ${yesNo(codec.encode4k30)}", style = MaterialTheme.typography.bodySmall, color = WorkstationMuted)
                        Text("4K60 Decode ${yesNo(codec.decode4k60)} • Encode ${yesNo(codec.encode4k60)}", style = MaterialTheme.typography.bodySmall, color = WorkstationMuted)
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
        containerColor = WorkstationBackground,
        topBar = { ProfessionalTopBar(title = "Diagnostics", eyebrow = "LOCAL SYSTEM", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading("RUNTIME", "VideoFlow diagnostics")
            WorkstationCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val rows = listOf(
                        "App" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        "Build" to BuildConfig.BUILD_TYPE,
                        "Android" to "API ${device.apiLevel}",
                        "ABI" to device.abis.joinToString(),
                        "RAM" to formatBytes(device.totalRamBytes),
                        "Free storage" to formatBytes(device.freeInternalBytes),
                        "Database" to "version 1",
                        "Media permissions" to device.persistedReadPermissionCount.toString(),
                        "Codec entries" to device.codecs.size.toString(),
                        "Diagnostic events" to vm.diagnostics.size.toString(),
                        "Network permission" to "not requested",
                        "Backup / transfer" to "disabled",
                    )
                    rows.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = WorkstationMuted)
                            Text(value, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
