package com.videoflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.videoflow.app.ui.editor.VideoFlowEditorColors

/** Truthful bounded Trim filmstrip: every cell is decoded at a distinct sampled source timestamp. */
@Composable
fun TrimFilmstripPreview(
    sampledPaths: List<String>,
    fallbackPath: String?,
    modifier: Modifier = Modifier
) {
    val frames = sampledPaths.take(8)
    Box(
        modifier.semantics { contentDescription = "Trim sampled filmstrip ${frames.size} frames" },
        contentAlignment = Alignment.Center
    ) {
        if (frames.isEmpty()) {
            if (fallbackPath != null) {
                CachedThumbnailPreview(fallbackPath, Modifier.fillMaxWidth().fillMaxHeight())
            } else {
                Box(Modifier.fillMaxWidth().fillMaxHeight().background(VideoFlowEditorColors.TimelineBackground), contentAlignment = Alignment.Center) {
                    Text("Sampling frames…", color = VideoFlowEditorColors.SecondaryText)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                frames.forEach { path ->
                    CachedThumbnailPreview(path, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
