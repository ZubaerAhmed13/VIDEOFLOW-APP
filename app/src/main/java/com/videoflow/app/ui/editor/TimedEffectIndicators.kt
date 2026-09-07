package com.videoflow.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.domain.editor.TimelineClip

private data class EffectIndicator(val id: String,val clip: TimelineClip,val label: String,val startUs: Long,val endUs: Long,val ai: Boolean,val enabled: Boolean)

/** Separate selectable edit rows, with a bounded viewport and no frame-dependent UI objects. */
@Composable
fun TimedEffectIndicators(clips: List<TimelineClip>, revision: Long, scroll: ScrollState, width: Dp, pixelsPerSecond: Float,
    onSelect: (EditorSelection)->Unit, onSeek: (Long)->Unit, onOpen: (ProfessionalEditorTool)->Unit) {
    val context=LocalContext.current
    val indicators by produceState<List<EffectIndicator>>(emptyList(),clips,revision) {
        val projectId=clips.firstOrNull()?.projectId
        value=if(projectId==null) emptyList() else {
            val repository=AiWatermarkRepository(context)
            val visual=repository.visualEdits.load(projectId)
            val ai=repository.load(projectId)
            clips.flatMap { clip ->
                visual.effects.filter { it.clipId==clip.id }.map { EffectIndicator(it.id,clip,"FX ${it.type.label}",it.startUs,it.endUs,false,it.enabled) }+
                    ai.filter { it.clipId==clip.id }.mapIndexed { index,it -> EffectIndicator(it.id,clip,"AI ${index+1}",it.clipLocalStartUs,it.clipLocalEndUs,true,it.enabled) }
            }
        }
    }
    if(indicators.isEmpty()) return
    Row(Modifier.fillMaxWidth().heightIn(max=144.dp).verticalScroll(rememberScrollState())) {
        Column(Modifier.width(84.dp)) {
            indicators.forEach { effect ->
                Text(effect.label, maxLines=1, modifier=Modifier.height(48.dp).fillMaxWidth().clickable {
                    onSelect(EditorSelection.Clip(effect.clip.id));onSeek(effect.clip.timelineStartUs+effect.startUs)
                    onOpen(if(effect.ai) ProfessionalEditorTool.AiWatermark(effect.clip.id,effect.id) else ProfessionalEditorTool.Effects(effect.clip.id,effect.id))
                }.padding(6.dp))
            }
        }
        Column(Modifier.weight(1f).horizontalScroll(scroll).width(width)) {
            indicators.forEach { effect ->
                val start=effect.clip.timelineStartUs+effect.startUs
                val end=effect.clip.timelineStartUs+minOf(effect.endUs,effect.clip.timelineDurationUs)
                Box(Modifier.width(width).height(48.dp)) {
                    if(end>start) Text(if(effect.enabled) effect.label else "${effect.label} (off)", maxLines=1,
                        modifier=Modifier.offset(x=(start.toDouble()/1_000_000*pixelsPerSecond).toFloat().dp)
                            .width(((end-start).toDouble()/1_000_000*pixelsPerSecond).toFloat().dp.coerceAtLeast(48.dp))
                            .height(48.dp).background(VideoFlowEditorColors.SelectionAccent.copy(alpha=if(effect.enabled) .35f else .15f))
                            .semantics { contentDescription="${effect.label} ${com.videoflow.app.domain.editor.TrimTimecode.formatUs(effect.startUs)} to ${com.videoflow.app.domain.editor.TrimTimecode.formatUs(effect.endUs)}" }
                            .clickable { onSelect(EditorSelection.Clip(effect.clip.id));onSeek(start)
                                onOpen(if(effect.ai) ProfessionalEditorTool.AiWatermark(effect.clip.id,effect.id) else ProfessionalEditorTool.Effects(effect.clip.id,effect.id)) }.padding(6.dp)
                    )
                }
            }
        }
    }
}
