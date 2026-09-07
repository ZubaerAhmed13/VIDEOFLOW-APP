@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.ui.effects

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.videoflow.app.ui.editor.hostActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.effects.*
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.ui.NativeVideoPlayer
import com.videoflow.app.ui.editor.PreciseRangeControls
import com.videoflow.app.ui.editor.ProfessionalEditorTool
import com.videoflow.app.render.effects.VisualEffectPipeline
import java.util.UUID

@Composable
fun ProfessionalToolPanel(projectId: String, tool: ProfessionalEditorTool, clip: TimelineClip, asset: MediaAsset,
    onClose: () -> Unit, onApplied: () -> Unit, vm: ProfessionalToolsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val activity=androidx.compose.ui.platform.LocalContext.current.hostActivity()
    val previewHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .25f).dp.coerceIn(72.dp,220.dp)
    var selectedId by rememberSaveable(tool) { mutableStateOf((tool as? ProfessionalEditorTool.Effects)?.effectId) }
    var positionUs by rememberSaveable(tool) { mutableLongStateOf(0L) }
    var playing by rememberSaveable(tool) { mutableStateOf(false) }
    var before by rememberSaveable(tool) { mutableStateOf(false) }
    var mute by rememberSaveable(tool) { mutableStateOf(false) }
    var category by rememberSaveable(tool) { mutableStateOf("Basic") }
    var adjustment by rememberSaveable(tool) { mutableStateOf(Adjustment.EXPOSURE) }
    LaunchedEffect(projectId, tool) { vm.open(projectId,tool.toString()) }
    var positioned by rememberSaveable(tool) { mutableStateOf(false) }
    LaunchedEffect(state.loaded,tool) {
        if(state.loaded && !positioned) (tool as? ProfessionalEditorTool.Effects)?.effectId?.let { id ->
            state.draft.effects.firstOrNull { it.id==id }?.let { positionUs=it.startUs.coerceAtMost(clip.timelineDurationUs-1L); positioned=true }
        }
    }
    LaunchedEffect(playing) {
        var previous=android.os.SystemClock.elapsedRealtimeNanos()
        while(playing) {
            kotlinx.coroutines.delay(33)
            val now=android.os.SystemClock.elapsedRealtimeNanos()
            positionUs=(positionUs+(now-previous)/1000L).coerceAtMost(clip.timelineDurationUs-1L)
            previous=now
            if(positionUs>=clip.timelineDurationUs-1L) playing=false
        }
    }
    DisposableEffect(tool) { onDispose { if(activity?.isChangingConfigurations != true) vm.cancel() } }
    val isAudio = tool is ProfessionalEditorTool.AudioExtract
    val isEnhance = tool is ProfessionalEditorTool.Enhance
    val title = if (isAudio) "Audio" else if (isEnhance) "Enhance" else "Effects"
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { vm.cancel(); onClose() }) { Text("Cancel") }
        }
        val effects = remember(state.draft, before, clip.id, clip.sourceStartUs, clip.speed) {
            if (before) emptyList() else VisualEffectPipeline.create(state.draft, clip.id, clip.sourceStartUs, clip.speed)
        }
        NativeVideoPlayer(asset.sourceUri, Modifier.fillMaxWidth().height(previewHeight),
            startPositionMs = (clip.sourceStartUs + (positionUs.toDouble()*clip.speed).toLong())/1000,
            showControls = isAudio, playWhenReady=playing, speed=clip.speed.toFloat(), videoEffects = effects)
        if (!isAudio) {
            Row {
                TextButton(onClick={ if(positionUs>=clip.timelineDurationUs-1L) positionUs=0L; playing=!playing }) { Text(if(playing) "Pause" else "Play") }
                FilterChip(before, { playing=false; before = true }, { Text("Before") })
                Spacer(Modifier.width(8.dp))
                FilterChip(!before, { playing=false; before = false }, { Text("After") })
            }
            Slider(value = (positionUs.toDouble()/clip.timelineDurationUs).toFloat().coerceIn(0f,1f),
                onValueChange = { playing=false; positionUs = (it.toDouble()*clip.timelineDurationUs).toLong().coerceAtMost(clip.timelineDurationUs-1) })
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (isAudio) {
            Text("Extracted audio becomes its own editable timeline clip. The original video stays linked to its source.")
            Row { Checkbox(mute, { mute = it }, enabled = !state.busy); Text("Mute original video audio") }
            Button(enabled = state.loaded && !state.busy && asset.audioTrackCount > 0,
                onClick = { vm.extract(projectId, clip.id, mute) { onApplied(); onClose() } }) { Text("Extract audio") }
            if (asset.audioTrackCount == 0) Text("This video has no audio track.")
        } else if (isEnhance) {
            val parameters = state.draft.enhance[clip.id] ?: EnhanceParameters()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Adjustment.entries.forEach { key -> FilterChip(adjustment == key, { adjustment = key }, { Text(key.label) }) }
            }
            Text("${adjustment.label} ${(parameters[adjustment]*100).toInt()}")
            Slider(parameters[adjustment], { value -> vm.draft(state.draft.copy(enhance = state.draft.enhance + (clip.id to parameters.with(adjustment,value)))) },
                modifier=Modifier.semantics { contentDescription="${adjustment.label} adjustment" },
                enabled = !state.busy, valueRange = if (adjustment in setOf(Adjustment.SHARPEN, Adjustment.VIGNETTE)) 0f..1f else -1f..1f)
            Row {
                TextButton(enabled = !state.busy, onClick = { vm.autoEnhance(clip.id,asset.sourceUri,clip.sourceStartUs,clip.sourceEndUs) }) { Text("Auto Enhance") }
                TextButton(onClick = { vm.draft(state.draft.copy(enhance = state.draft.enhance - clip.id)) }) { Text("Reset") }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VisualEffectType.entries.map { it.category }.distinct().forEach { name -> FilterChip(category == name, { category = name }, { Text(name) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VisualEffectType.entries.filter { it.category == category }.forEach { type ->
                    OutlinedButton(enabled = !state.busy, onClick = {
                        val node = VideoEffectNode(UUID.randomUUID().toString(),clip.id,type,0L,clip.timelineDurationUs,
                            order = (state.draft.effects.maxOfOrNull { it.order } ?: -1)+1)
                        selectedId = node.id; vm.draft(state.draft.copy(effects = state.draft.effects + node))
                    }) { Text(type.label) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.draft.effects.filter { it.clipId == clip.id }.forEach { effect ->
                    FilterChip(selectedId == effect.id, { selectedId = effect.id }, { Text("${effect.type.label} ${if (effect.enabled) "" else "(off)"}") })
                }
            }
            state.draft.effects.firstOrNull { it.id == selectedId }?.let { selected ->
                fun update(next: VideoEffectNode) = vm.draft(state.draft.copy(effects = state.draft.effects.map { if (it.id == next.id) next else it }))
                Text("Intensity ${(selected.intensity*100).toInt()}%")
                Slider(selected.intensity, { update(selected.copy(intensity = it)) }, enabled = !state.busy)
                PreciseRangeControls(clip.timelineDurationUs, selected.startUs, selected.endUs, positionUs,
                    { a,b -> update(selected.copy(startUs = a,endUs = b)) }, { positionUs = it })
                Row {
                    TextButton(onClick = { update(selected.copy(enabled = !selected.enabled)) }) { Text(if (selected.enabled) "Disable" else "Enable") }
                    TextButton(onClick = { vm.draft(state.draft.copy(effects = state.draft.effects - selected)); selectedId = null }) { Text("Remove") }
                    TextButton(onClick = { update(selected.copy(intensity = .5f, startUs = 0L, endUs = clip.timelineDurationUs)) }) { Text("Reset") }
                    TextButton(onClick = {
                        val copy = selected.copy(id=UUID.randomUUID().toString(),order=(state.draft.effects.maxOfOrNull { it.order } ?: 0)+1)
                        vm.draft(state.draft.copy(effects=state.draft.effects+copy));selectedId=copy.id
                    }) { Text("Duplicate") }
                }
            }
        }
        }
        if (!isAudio) Button(enabled = state.loaded && !state.busy,
            onClick = { vm.apply(projectId,title) { onApplied(); onClose() } }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
