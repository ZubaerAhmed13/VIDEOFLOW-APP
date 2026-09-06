package com.videoflow.app.domain.ai

import com.videoflow.app.domain.editor.TimelineClip

/** Export invariants for local AI reconstruction edits. */
object AiReconstructionExportPolicy {
    const val SMART_COPY_BLOCK_REASON = "Local AI reconstruction changes decoded pixels and therefore requires rendering. Smart Copy is not allowed."

    fun activeForProject(effects: List<AiWatermarkEffect>, clips: List<TimelineClip>): List<AiWatermarkEffect> {
        val enabledClipIds = clips.filter { it.enabled }.map { it.id }.toSet()
        return effects.filter { it.enabled && it.clipId in enabledClipIds }
    }

    fun validationProblems(effects: List<AiWatermarkEffect>, clips: List<TimelineClip>): List<String> {
        val byId = clips.associateBy { it.id }
        return buildList {
            effects.filter { it.enabled }.forEach { effect ->
                val clip = byId[effect.clipId]
                if (clip == null) {
                    add("AI effect ${effect.id} refers to a clip that no longer exists.")
                    return@forEach
                }
                if (effect.clipLocalStartUs >= clip.timelineDurationUs) {
                    add("AI effect ${effect.id} begins after its clip ends.")
                }
                if (AiModelCatalog.byId(effect.modelId)?.role != AiModelRole.FINAL) {
                    add("AI effect ${effect.id} is not pinned to the final-quality LaMa model.")
                }
            }
        }.distinct()
    }
}
