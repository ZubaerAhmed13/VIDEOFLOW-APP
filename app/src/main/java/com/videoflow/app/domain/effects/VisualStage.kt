package com.videoflow.app.domain.effects

/** Immutable stage configuration shared by preview and export, including the source time origin. */
data class VisualStage(val node: VideoEffectNode? = null, val enhance: EnhanceParameters = EnhanceParameters(),
    val sourceOffsetUs: Long = 0L, val speed: Double = 1.0) {
    init { require(speed.isFinite() && speed > 0.0) }
    fun localTimeUs(presentationTimeUs: Long) = ((presentationTimeUs-sourceOffsetUs).toDouble()/speed).toLong()
    fun amountAt(presentationTimeUs: Long): Float = node?.takeIf { it.activeAt(localTimeUs(presentationTimeUs)) }?.intensity ?: 0f
    companion object {
        fun ordered(edits: VisualEdits, clipId: String, sourceOffsetUs: Long = 0L, speed: Double = 1.0): List<VisualStage> = buildList {
            edits.enhance[clipId]?.takeUnless { it.isIdentity }?.let { add(VisualStage(enhance=it,sourceOffsetUs=sourceOffsetUs,speed=speed)) }
            edits.ordered(clipId).forEach { add(VisualStage(node=it,sourceOffsetUs=sourceOffsetUs,speed=speed)) }
        }
    }
}
