package com.videoflow.app.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.videoflow.app.domain.editor.TrimTimecode
import kotlin.math.roundToLong

/** Integer time model, zoom-local normalized pointer coordinates; exact fields retain microseconds. */
@Composable
fun PreciseRangeControls(durationUs: Long, startUs: Long, endUs: Long, playheadUs: Long,
    onRange: (Long, Long) -> Unit, onSeek: (Long) -> Unit) {
    var zoom by remember { mutableIntStateOf(1) }
    var startText by remember(startUs) { mutableStateOf(TrimTimecode.formatUs(startUs)) }
    var endText by remember(endUs) { mutableStateOf(TrimTimecode.formatUs(endUs)) }
    val span = (durationUs / zoom).coerceAtLeast(1L)
    val left = (playheadUs - span / 2L).coerceIn(0L, (durationUs - span).coerceAtLeast(0L))
    val right = minOf(durationUs, left + span)
    val visibleStart = startUs.coerceIn(left, right)
    val visibleEnd = endUs.coerceIn(visibleStart, right)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(startText, { startText = it }, label = { Text("Start") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(endText, { endText = it }, label = { Text("End") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        val parsedStart = TrimTimecode.parseToUs(startText).getOrNull()
        val parsedEnd = TrimTimecode.parseToUs(endText).getOrNull()
        val valid = parsedStart != null && parsedEnd != null && parsedStart >= 0L && parsedEnd > parsedStart && parsedEnd <= durationUs
        TextButton(enabled = valid, onClick = { onRange(requireNotNull(parsedStart), requireNotNull(parsedEnd)) }) { Text("Preview range") }
        if (!valid) Text("Enter a start before the end, within this clip.", color = MaterialTheme.colorScheme.error)
        Text("Length ${TrimTimecode.formatUs(endUs - startUs)} · ${zoom}× zoom")
        RangeSlider(
            value = ((visibleStart-left).toDouble()/span).toFloat()..((visibleEnd-left).toDouble()/span).toFloat(),
            onValueChange = { value ->
                val a = left + (value.start.toDouble()*span).roundToLong()
                val b = left + (value.endInclusive.toDouble()*span).roundToLong()
                if (a < b) onRange(a, b)
            }, modifier = Modifier.semantics { contentDescription = "Effect range handles" }
        )
        val rangeLength = endUs-startUs
        val moveLimit = durationUs-rangeLength
        Text("Move entire range")
        Slider(value=if(moveLimit>0L) (startUs.toDouble()/moveLimit).toFloat() else 0f,
            enabled=moveLimit>0L, onValueChange={ value ->
                val nextStart=(value.toDouble()*moveLimit).roundToLong().coerceIn(0L,moveLimit)
                onRange(nextStart,nextStart+rangeLength)
            },modifier=Modifier.semantics { contentDescription="Move entire effect range" })
        Row(Modifier.fillMaxWidth()) {
            TextButton(modifier=Modifier.weight(1f),onClick = { zoom = (zoom * 2).coerceAtMost(4096) }) { Text("Zoom in") }
            TextButton(modifier=Modifier.weight(1f),onClick = { zoom = (zoom / 2).coerceAtLeast(1) }) { Text("Zoom out") }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(modifier=Modifier.weight(1f),onClick = { onSeek((playheadUs - span/4).coerceAtLeast(0)) }) { Text("Earlier") }
            TextButton(modifier=Modifier.weight(1f),onClick = { onSeek((playheadUs + span/4).coerceAtMost(durationUs-1)) }) { Text("Later") }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(modifier=Modifier.weight(1f),enabled = playheadUs < endUs, onClick = { onRange(playheadUs.coerceAtLeast(0), endUs) }) { Text("Set Start") }
            TextButton(modifier=Modifier.weight(1f),enabled = playheadUs > startUs, onClick = { onRange(startUs, playheadUs.coerceAtMost(durationUs)) }) { Text("Set End") }
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(modifier=Modifier.weight(1f),onClick = { onSeek(startUs) }) { Text("Jump to start") }
            TextButton(modifier=Modifier.weight(1f),onClick = { onSeek((endUs-1).coerceAtLeast(startUs)) }) { Text("Jump to end") }
        }
        TextButton(onClick = { onRange(0L, durationUs) }) { Text("Full clip") }
    }
}
