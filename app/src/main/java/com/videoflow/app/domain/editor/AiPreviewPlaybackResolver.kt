package com.videoflow.app.domain.editor

import com.videoflow.app.ai.watermark.AiPreviewReadySegment
import kotlin.math.roundToLong

/** One source-time slice in the editor's range-aware preview playlist. */
data class PreviewMediaSegment(
    val uri: String,
    /** Position represented by this segment, relative to clip.sourceStartUs, in source-time ms. */
    val sourceOffsetStartMs: Long,
    val sourceOffsetEndMs: Long,
    /** Source clipping positions inside [uri]. Processed cache segments normally begin at 0. */
    val mediaStartMs: Long,
    val mediaEndMs: Long,
    val aiProcessed: Boolean
) {
    init {
        require(uri.isNotBlank())
        require(sourceOffsetStartMs >= 0L && sourceOffsetEndMs > sourceOffsetStartMs)
        require(mediaStartMs >= 0L && mediaEndMs > mediaStartMs)
    }

    val sourceDurationMs: Long get() = sourceOffsetEndMs - sourceOffsetStartMs
}

/**
 * Builds a complete clip playlist from the normal proxy/source plus bounded AI cache segments.
 * No AI cache is promoted to source-of-record; gaps and non-AI ranges always reference baseUri.
 */
object AiPreviewPlaybackResolver {
    fun build(
        baseUri: String,
        clip: TimelineClip,
        processed: List<AiPreviewReadySegment>
    ): List<PreviewMediaSegment> {
        if (processed.isEmpty()) return emptyList()
        val valid = processed.asSequence()
            .filter { it.clipId == clip.id }
            .mapNotNull { segment ->
                val start = segment.clipLocalStartUs.coerceIn(0L, clip.timelineDurationUs)
                val end = segment.clipLocalEndUs.coerceIn(0L, clip.timelineDurationUs)
                if (end <= start) null else SegmentWindow(start, end, segment.path)
            }
            .sortedBy { it.startUs }
            .toList()
        if (valid.isEmpty()) return emptyList()

        val result = mutableListOf<PreviewMediaSegment>()
        var cursorLocalUs = 0L
        valid.forEach { window ->
            val start = maxOf(cursorLocalUs, window.startUs)
            val end = maxOf(start, window.endUs)
            if (start > cursorLocalUs) result += baseSegment(baseUri, clip, cursorLocalUs, start)
            if (end > start) {
                val sourceStart = sourceOffsetMs(clip, start)
                val sourceEnd = sourceOffsetMs(clip, end).coerceAtLeast(sourceStart + 1L)
                result += PreviewMediaSegment(
                    uri = window.path,
                    sourceOffsetStartMs = sourceStart,
                    sourceOffsetEndMs = sourceEnd,
                    mediaStartMs = 0L,
                    mediaEndMs = sourceEnd - sourceStart,
                    aiProcessed = true
                )
                cursorLocalUs = end
            }
        }
        if (cursorLocalUs < clip.timelineDurationUs) result += baseSegment(baseUri, clip, cursorLocalUs, clip.timelineDurationUs)
        return result.filter { it.sourceDurationMs > 0L }
    }

    /** Returns playlist index + position relative to that already-clipped media item. */
    fun locate(segments: List<PreviewMediaSegment>, absoluteSourcePositionMs: Long, clipSourceStartMs: Long): Pair<Int, Long>? {
        if (segments.isEmpty()) return null
        val offset = (absoluteSourcePositionMs - clipSourceStartMs).coerceAtLeast(0L)
        val index = segments.indexOfFirst { offset >= it.sourceOffsetStartMs && offset < it.sourceOffsetEndMs }
            .let { if (it >= 0) it else segments.lastIndex }
        val segment = segments[index]
        val localOffset = (offset - segment.sourceOffsetStartMs).coerceIn(0L, segment.sourceDurationMs - 1L)
        return index to localOffset
    }

    /** Maps a position inside a clipped playlist item back to the authoritative source timeline. */
    fun absoluteSourcePositionMs(
        segments: List<PreviewMediaSegment>,
        mediaItemIndex: Int,
        mediaPositionMs: Long,
        clipSourceStartMs: Long
    ): Long {
        val segment = segments.getOrNull(mediaItemIndex) ?: return clipSourceStartMs + mediaPositionMs.coerceAtLeast(0L)
        val offsetInSegment = mediaPositionMs.coerceIn(0L, segment.sourceDurationMs)
        return clipSourceStartMs + segment.sourceOffsetStartMs + offsetInSegment
    }

    private fun baseSegment(baseUri: String, clip: TimelineClip, startLocalUs: Long, endLocalUs: Long): PreviewMediaSegment {
        val sourceOffsetStart = sourceOffsetMs(clip, startLocalUs)
        val sourceOffsetEnd = sourceOffsetMs(clip, endLocalUs).coerceAtLeast(sourceOffsetStart + 1L)
        val clipSourceStartMs = clip.sourceStartUs / 1_000L
        return PreviewMediaSegment(
            uri = baseUri,
            sourceOffsetStartMs = sourceOffsetStart,
            sourceOffsetEndMs = sourceOffsetEnd,
            mediaStartMs = clipSourceStartMs + sourceOffsetStart,
            mediaEndMs = clipSourceStartMs + sourceOffsetEnd,
            aiProcessed = false
        )
    }

    private fun sourceOffsetMs(clip: TimelineClip, localUs: Long): Long =
        (localUs.toDouble() * clip.speed / 1_000.0).roundToLong().coerceIn(0L, clip.sourceDurationUs / 1_000L)

    private data class SegmentWindow(val startUs: Long, val endUs: Long, val path: String)
}
