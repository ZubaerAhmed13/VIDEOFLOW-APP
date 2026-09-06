package com.videoflow.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.videoflow.app.ui.product.ProductHomeViewModel

@Composable
fun FinalQualityHomeRoute(
    onOpen: (String) -> Unit,
    onProjectDetails: (String) -> Unit,
    onSettings: () -> Unit,
    onMerge: () -> Unit,
    vm: ProductHomeViewModel
) {
    Box(Modifier.fillMaxSize()) {
        ProductHomeScreen(
            onOpen = onOpen,
            onProjectDetails = onProjectDetails,
            onSettings = onSettings,
            vm = vm
        )
        ExtendedFloatingActionButton(
            onClick = onMerge,
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
            text = { Text("Merge Videos") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .semantics { contentDescription = "Merge Videos" }
        )
    }
}
