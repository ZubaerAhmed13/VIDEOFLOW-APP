package com.videoflow.app.domain.ai

data class AiSegment(val index: Long, val startUs: Long, val endUs: Long) {
    init { require(index >= 0 && startUs >= 0 && endUs > startUs) }
    val durationUs get() = endUs-startUs
}

/** Lazy production segmentation: memory never scales with frames or decoded duration. */
class AiLongJobPlan(val durationUs: Long, val segmentUs: Long = 60_000_000L) {
    init { require(durationUs > 0 && segmentUs > 0) }
    val count: Long get() = 1L+(durationUs-1L)/segmentUs
    fun segment(index: Long): AiSegment {
        require(index in 0 until count)
        val start = Math.multiplyExact(index,segmentUs)
        return AiSegment(index,start,start+minOf(segmentUs,durationUs-start))
    }
    fun segments(from: Long = 0L): Sequence<AiSegment> {
        require(from in 0..count)
        return generateSequence(from) { if (it < count-1) it+1 else null }.takeWhile { it < count }.map(::segment)
    }
    fun progress(completedUs: Long) = (completedUs.coerceIn(0,durationUs).toDouble()/durationUs).toFloat()
    fun storageBytes(bitsPerSecond: Long): Long {
        require(bitsPerSecond > 0)
        val seconds = 1L+(durationUs-1L)/1_000_000L
        val bytes = Math.multiplyExact(seconds, (bitsPerSecond+7L)/8L)
        return Math.addExact(Math.multiplyExact(bytes, 3L)/2L, 64L*1024L*1024L)
    }
}

enum class AiLongJobState { PREPARING, PROCESSING, FINALIZING, VALIDATING, COMPLETED, CANCELLED, FAILED }

data class AiResourceProfile(val workers: Int = 1, val queueCapacity: Int = 2, val tileSize: Int = 512) {
    init { require(workers == 1 && queueCapacity in 1..2 && tileSize == 512) }
    // Serial final inference preserves determinism on all devices, including thermal throttling.
    val maximumQueuedTasks get() = workers+queueCapacity
}
