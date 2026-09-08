package com.videoflow.app.ui.editor

/** First-class contextual tool session; one active tool, never independent feature booleans. */
sealed interface ProfessionalEditorTool : java.io.Serializable {
    val clipId: String
    data class AudioExtract(override val clipId: String) : ProfessionalEditorTool
    data class Effects(override val clipId: String, val effectId: String? = null) : ProfessionalEditorTool
    data class Enhance(override val clipId: String) : ProfessionalEditorTool
    data class AiWatermark(override val clipId: String, val effectId: String? = null) : ProfessionalEditorTool
}
