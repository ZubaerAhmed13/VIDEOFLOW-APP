package com.videoflow.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.domain.editor.TimelineClip

private data class EffectIndicator(val clip: TimelineClip,val label: String,val startUs: Long,val endUs: Long,val ai: Boolean)

/** One region per edit, never one view per video frame. Shares the timeline's scroll and zoom. */
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
                visual.effects.filter { it.clipId==clip.id }.map { EffectIndicator(clip,"FX ${it.type.label}",it.startUs,it.endUs,false) }+
                    ai.filter { it.clipId==clip.id }.map { EffectIndicator(clip,"AI removal",it.clipLocalStartUs,it.clipLocalEndUs,true) }
            }
        }
    }
    if(indicators.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(28.dp)) {
        Text("Effects",modifier=Modifier.width(84.dp).padding(4.dp))
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Box(Modifier.width(width).height(28.dp)) {
                indicators.forEach { effect ->
                    val start=effect.clip.timelineStartUs+effect.startUs
                    val end=effect.clip.timelineStartUs+minOf(effect.endUs,effect.clip.timelineDurationUs)
                    if(end>start) Text(effect.label, maxLines=1,
                        modifier=Modifier.offset(x=(start.toDouble()/1_000_000*pixelsPerSecond).toFloat().dp)
                            .width(((end-start).toDouble()/1_000_000*pixelsPerSecond).toFloat().dp.coerceAtLeast(8.dp))
                            .height(26.dp).background(VideoFlowEditorColors.SelectionAccent.copy(alpha=.35f))
                            .clickable { onSelect(EditorSelection.Clip(effect.clip.id));onSeek(start)
                                onOpen(if(effect.ai) ProfessionalEditorTool.AiWatermark(effect.clip.id) else ProfessionalEditorTool.Effects(effect.clip.id)) }
                    )
                }
            }
        }
    }
}
