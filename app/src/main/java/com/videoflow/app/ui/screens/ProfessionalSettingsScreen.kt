package com.videoflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoflow.app.ui.product.AppAppearance
import com.videoflow.app.ui.product.PreferencesViewModel
import com.videoflow.app.ui.product.ProductSettingsViewModel
import com.videoflow.app.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalSettingsScreen(
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onDiagnostics: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onIntroduction: () -> Unit,
    preferencesViewModel: PreferencesViewModel,
    settingsViewModel: ProductSettingsViewModel
) {
    val preferences by preferencesViewModel.state.collectAsState()
    val storage by settingsViewModel.storage.collectAsState()
    var appearanceDialog by remember { mutableStateOf(false) }
    var clearProxyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { settingsViewModel.refresh() }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { SectionHeader("Editing") }
            item {
                SettingsReadOnly(
                    "Timeline controls",
                    "Snapping, track controls, trim handles and contextual tools remain project-specific in the editor.",
                    Icons.Default.Tune
                )
            }

            item { SectionHeader("Performance") }
            item {
                SettingsReadOnly(
                    "Proxy editing",
                    "VideoFlow can use smaller editing media for smoother preview performance. Final export continues to resolve original files.",
                    Icons.Default.Videocam
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Editing proxies") },
                    supportingContent = {
                        Text(
                            if (storage.proxyCount == 0) "No editing proxies stored"
                            else "${storage.proxyCount} ${if (storage.proxyCount == 1) "proxy" else "proxies"} • ${formatBytes(storage.proxyBytes)}"
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = { clearProxyDialog = true }, enabled = storage.proxyCount > 0 && !storage.clearing) {
                            Text(if (storage.clearing) "Clearing…" else "Clear")
                        }
                    }
                )
                HorizontalDivider()
            }

            item { SectionHeader("Storage") }
            item {
                SettingsReadOnly(
                    "Original media",
                    "Original videos, photos and audio remain referenced through Android's document framework. Import does not copy the entire original into VideoFlow.",
                    Icons.Default.Storage
                )
            }
            item {
                SettingsReadOnly(
                    "Project data",
                    "Edits, timeline state, snapshots and derived editing data are stored separately from your original media.",
                    Icons.Default.Storage
                )
            }

            item { SectionHeader("Export") }
            item {
                SettingsReadOnly(
                    "Recommended defaults",
                    "Export starts with Match Project, project frame rate, H.264 compatibility and a quality-oriented preset. Advanced settings remain available per export.",
                    Icons.Default.Tune
                )
            }

            item { SectionHeader("Appearance") }
            item {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(appearanceLabelProfessional(preferences.appearance)) },
                    trailingContent = { TextButton(onClick = { appearanceDialog = true }) { Text("Change") } }
                )
                HorizontalDivider()
            }

            item { SectionHeader("Accessibility") }
            item {
                SettingsReadOnly(
                    "System accessibility",
                    "VideoFlow follows Android font scaling and animation settings. Product controls use semantic labels and real progress semantics.",
                    Icons.Default.Accessibility
                )
            }

            item { SectionHeader("Privacy & Support") }
            item { SettingsAction("Privacy", "Local-first processing and Android document access", Icons.Default.Security, onPrivacy) }
            item { SettingsAction("Device Capability", "Inspect codec and hardware support", Icons.Default.Videocam, onDevice) }
            item { SettingsAction("Diagnostics", "Technical details belong here, not on Home", Icons.Default.Info, onDiagnostics) }
            item { SettingsAction("Show Introduction", "Review the three-screen introduction", Icons.Default.Info, onIntroduction) }
            item { SettingsAction("About", "Version, build and privacy summary", Icons.Default.Info, onAbout) }

            storage.message?.let { message ->
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = settingsViewModel::clearMessage) { Text("Dismiss") }
                    }
                }
            }
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
                            onClick = {
                                preferencesViewModel.setAppearance(option)
                                appearanceDialog = false
                            },
                            label = { Text(appearanceLabelProfessional(option)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (clearProxyDialog) {
        AlertDialog(
            onDismissRequest = { clearProxyDialog = false },
            title = { Text("Delete editing proxies?") },
            text = { Text("Original media and project edits will stay unchanged. VideoFlow can generate editing proxies again later.") },
            confirmButton = {
                TextButton(onClick = {
                    clearProxyDialog = false
                    settingsViewModel.clearAllProxies()
                }) { Text("Delete Proxies") }
            },
            dismissButton = { Button(onClick = { clearProxyDialog = false }) { Text("Keep Proxies") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsReadOnly(title: String, supporting: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
    HorizontalDivider()
}

@Composable
private fun SettingsAction(title: String, supporting: String, icon: ImageVector, action: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { TextButton(onClick = action) { Text("Open") } }
    )
    HorizontalDivider()
}

private fun appearanceLabelProfessional(value: AppAppearance): String = when (value) {
    AppAppearance.SYSTEM -> "System"
    AppAppearance.LIGHT -> "Light"
    AppAppearance.DARK -> "Dark"
}
