package com.videoflow.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoflow.app.BuildConfig
import com.videoflow.app.domain.model.VideoFlowProject
import com.videoflow.app.ui.ProjectThumbnailPreview
import com.videoflow.app.ui.product.AppAppearance
import com.videoflow.app.ui.product.PreferencesViewModel
import com.videoflow.app.ui.product.ProductHomeViewModel
import com.videoflow.app.ui.product.ProjectAspectPreset
import com.videoflow.app.util.formatDurationUs
import java.text.DateFormat
import java.util.Date

private val PRODUCT_MEDIA_TYPES = arrayOf("video/*", "audio/*", "image/*")

private data class OnboardingPage(
    val title: String,
    val body: String,
    val icon: @Composable () -> Unit
)

@Composable
fun ProductOnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    markComplete: Boolean,
    preferencesViewModel: PreferencesViewModel
) {
    var page by rememberSaveable { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            "Edit directly on your device",
            "VideoFlow works with the videos, photos and audio you choose through Android.",
            { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(72.dp)) }
        ),
        OnboardingPage(
            "Built for large media",
            "VideoFlow references your original files instead of copying entire videos into the app.",
            { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(72.dp)) }
        ),
        OnboardingPage(
            "Start creating",
            "Create a project, add media, edit, and export locally with professional controls available when you need them.",
            { Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(72.dp)) }
        )
    )
    val current = pages[page]

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < pages.lastIndex) {
                    TextButton(onClick = {
                        if (markComplete) preferencesViewModel.completeOnboarding()
                        onSkip()
                    }) { Text("Skip") }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Spacer(Modifier.height(36.dp))
                current.icon()
                Text(current.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(current.body, style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${page + 1} of ${pages.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (page < pages.lastIndex) page += 1 else {
                        if (markComplete) preferencesViewModel.completeOnboarding()
                        onComplete()
                    }
                }) {
                    Text(if (page == pages.lastIndex) "New Project" else "Next")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductHomeScreen(
    onOpen: (String) -> Unit,
    onProjectDetails: (String) -> Unit,
    onSettings: () -> Unit,
    vm: ProductHomeViewModel
) {
    val projects by vm.projects.collectAsState()
    var createDialog by rememberSaveable { mutableStateOf(false) }
    var pendingMediaName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPreset by rememberSaveable { mutableStateOf(ProjectAspectPreset.LANDSCAPE) }
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val name = pendingMediaName
        if (uri != null && name != null) {
            vm.createFromMedia(
                name = name,
                uri = uri,
                fallbackPreset = pendingPreset,
                onDone = { onOpen(it.projectId) },
                onError = { message = it }
            )
        }
        pendingMediaName = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VideoFlow", maxLines = 1)
                        Text("Create. Edit. Export locally.", style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val horizontal = if (maxWidth >= 600.dp) 32.dp else 16.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = horizontal, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Button(
                        onClick = { createDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "New Project" }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Project")
                    }
                }
                item { Text("Recent Projects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                if (projects.isEmpty()) {
                    item { EmptyProjects(onNewProject = { createDialog = true }) }
                } else {
                    items(projects, key = { it.id }) { project ->
                        ProductProjectCard(
                            project = project,
                            onOpen = { onOpen(project.id) },
                            onDetails = { onProjectDetails(project.id) },
                            onRename = { name -> vm.renameProject(project.id, name) { message = it } },
                            onDelete = { vm.deleteProject(project.id) { message = it } }
                        )
                    }
                }
            }
        }
    }

    if (createDialog) {
        NewProjectDialog(
            onDismiss = { createDialog = false },
            onCreate = { name, preset ->
                vm.createProject(
                    name,
                    preset,
                    onDone = { createDialog = false; onOpen(it.projectId) },
                    onError = { message = it }
                )
            },
            onStartFromMedia = { name, preset ->
                val safe = name.trim()
                if (safe.isBlank()) message = "Enter a project name."
                else {
                    pendingMediaName = safe.take(80)
                    pendingPreset = preset
                    createDialog = false
                    mediaPicker.launch(PRODUCT_MEDIA_TYPES)
                }
            }
        )
    }
}

@Composable
private fun EmptyProjects(onNewProject: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(48.dp))
            Text("Your edits start here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Create a project and add videos, photos or audio.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onNewProject) { Text("New Project") }
        }
    }
}

@Composable
private fun ProductProjectCard(
    project: VideoFlowProject,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val first = project.mediaAssets.firstOrNull()
    val metadata = buildList {
        first?.durationUs?.let { add(formatDurationUs(it)) }
        val width = first?.width
        val height = first?.height
        if (width != null && height != null) add("${width}×${height}")
        if (project.mediaAssets.isNotEmpty()) add("${project.mediaAssets.size} media")
    }.joinToString(" • ").ifBlank { "Empty project" }
    val edited = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.updatedAt))
    val semantics = "${project.name}, $metadata, edited $edited"

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics { contentDescription = semantics }) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectThumbnailPreview(
                sourceUri = first?.sourceUri,
                mimeType = first?.mimeType,
                modifier = Modifier.size(width = 88.dp, height = 56.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(metadata, style = MaterialTheme.typography.bodySmall)
                Text("Edited $edited", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Project actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menu = false; renameDialog = true }
                    )
                    DropdownMenuItem(text = { Text("Project Details") }, onClick = { menu = false; onDetails() })
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menu = false; deleteDialog = true }
                    )
                }
            }
        }
    }

    if (renameDialog) {
        var value by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it.take(80) }, singleLine = true, label = { Text("Project name") }) },
            confirmButton = { TextButton(onClick = { if (value.isNotBlank()) { onRename(value); renameDialog = false } }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } }
        )
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete project?") },
            text = { Text("Your original videos, photos and audio will not be deleted. VideoFlow will remove this project and its saved editing data.") },
            confirmButton = { TextButton(onClick = { onDelete(); deleteDialog = false }) { Text("Delete Project") } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Keep Project") } }
        )
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, ProjectAspectPreset) -> Unit,
    onStartFromMedia: (String, ProjectAspectPreset) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("My Project") }
    var preset by rememberSaveable { mutableStateOf(ProjectAspectPreset.LANDSCAPE) }
    val valid = name.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    singleLine = true,
                    label = { Text("Project Name") },
                    isError = !valid
                )
                Text("Canvas", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProjectAspectPreset.entries) { option ->
                        FilterChip(
                            selected = preset == option,
                            onClick = { preset = option },
                            label = { Text("${option.label} ${option.supporting}") }
                        )
                    }
                }
                Text("1080p-class canvas. Start from Media adapts the canvas to the selected source when possible.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { if (valid) onStartFromMedia(name, preset) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Start from Media") }
            }
        },
        confirmButton = { Button(onClick = { if (valid) onCreate(name, preset) }, enabled = valid) { Text("Create Project") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSettingsScreen(
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onDiagnostics: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onIntroduction: () -> Unit,
    preferencesViewModel: PreferencesViewModel
) {
    val preferences by preferencesViewModel.state.collectAsState()
    var appearanceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SettingsHeader("Editing") }
            item { SettingsInfo("Timeline and tool controls", "Snapping, track controls and contextual editing stay with each project in the editor.") }
            item { SettingsHeader("Performance") }
            item { SettingsInfo("Proxy editing", "VideoFlow can use smaller editing media for smoother previews. Final export continues to resolve original sources.") }
            item { SettingsHeader("Storage") }
            item { SettingsInfo("Original media", "Original files remain referenced through Android's document framework; VideoFlow does not copy them during import.") }
            item { SettingsHeader("Appearance") }
            item {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(appearanceLabel(preferences.appearance)) },
                    trailingContent = { TextButton(onClick = { appearanceDialog = true }) { Text("Change") } }
                )
            }
            item { SettingsHeader("Privacy & Support") }
            item { SettingsAction("Privacy", "Local-first media processing and Android document access", Icons.Default.Security, onPrivacy) }
            item { SettingsAction("Device Capability", "Inspect codec and hardware support", Icons.Default.Videocam, onDevice) }
            item { SettingsAction("Diagnostics", "Technical details and local diagnostic state", Icons.Default.Info, onDiagnostics) }
            item { SettingsAction("Show Introduction", "Review the three-screen introduction", Icons.Default.AddCircle, onIntroduction) }
            item { SettingsAction("About", "Version, build and privacy summary", Icons.Default.Info, onAbout) }
        }
    }

    if (appearanceDialog) {
        AlertDialog(
            onDismissRequest = { appearanceDialog = false },
            title = { Text("Appearance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppAppearance.entries.forEach { option ->
                        FilterChip(
                            selected = preferences.appearance == option,
                            onClick = { preferencesViewModel.setAppearance(option); appearanceDialog = false },
                            label = { Text(appearanceLabel(option)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun SettingsHeader(value: String) {
    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun SettingsInfo(title: String, body: String) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(body) })
    HorizontalDivider()
}

@Composable
private fun SettingsAction(title: String, supporting: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { TextButton(onClick = onClick) { Text("Open") } },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title. $supporting" }
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    SimpleProductPage(onBack, "Privacy") {
        Text("VideoFlow processes media locally on this device.", style = MaterialTheme.typography.titleMedium)
        Text("VideoFlow does not upload your video to a VideoFlow server.")
        Text("Files are selected through Android's document picker. A document provider you choose may itself be cloud-backed; VideoFlow does not add its own upload step.")
        Text("The app does not include network telemetry or analytics SDKs, and the Android manifest does not request INTERNET permission.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    SimpleProductPage(onBack, "About") {
        Text("VideoFlow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Version ${BuildConfig.VERSION_NAME}")
        Text("Build: ${BuildConfig.BUILD_TYPE}")
        Text("Native Android video editing with local-first media access and original-source final export.")
        Text("Open-source dependency notices are included with the project documentation and release package.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleProductPage(onBack: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

private fun appearanceLabel(value: AppAppearance): String = when (value) {
    AppAppearance.SYSTEM -> "System"
    AppAppearance.LIGHT -> "Light"
    AppAppearance.DARK -> "Dark"
}
