package com.videoflow.app.domain.editor

import com.videoflow.app.ai.watermark.AiPreviewReadySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPreviewPlaybackResolverTest {
    private val clip = TimelineClip(
        id = "clip",
        projectId = "project",
        trackId = "video",
        assetId = "asset",
        timelineStartUs = 0L,
        sourceStartUs = 2_000_000L,
        sourceEndUs = 12_000_000L
    )

    @Test
    fun `multiple AI ranges stitch with source ranges without gaps or bleed`() {
        val processed = listOf(
            AiPreviewReadySegment("project", "clip", 2_000_000L, 4_000_000L, "/cache/a.mp4", "key"),
            AiPreviewReadySegment("project", "clip", 7_000_000L, 9_000_000L, "/cache/b.mp4", "key")
        )

        val result = AiPreviewPlaybackResolver.build("content://source", clip, processed)

        assertEquals(5, result.size)
        assertEquals(listOf(false, true, false, true, false), result.map { it.aiProcessed })
        assertEquals(listOf(0L, 2_000L, 4_000L, 7_000L, 9_000L), result.map { it.sourceOffsetStartMs })
        assertEquals(listOf(2_000L, 4_000L, 7_000L, 9_000L, 10_000L), result.map { it.sourceOffsetEndMs })
        assertEquals("/cache/a.mp4", result[1].uri)
        assertEquals(0L, result[1].mediaStartMs)
        assertEquals(2_000L, result[1].mediaEndMs)
        assertEquals("content://source", result[2].uri)
        assertEquals(6_000L, result[2].mediaStartMs)
        assertEquals(9_000L, result[2].mediaEndMs)

        result.zipWithNext().forEach { (left, right) ->
            assertEquals(left.sourceOffsetEndMs, right.sourceOffsetStartMs)
            assertFalse(left.sourceOffsetEndMs > right.sourceOffsetStartMs)
        }
        assertEquals(0L, result.first().sourceOffsetStartMs)
        assertEquals(clip.sourceDurationUs / 1_000L, result.last().sourceOffsetEndMs)
    }

    @Test
    fun `seek mapping remains continuous across processed boundaries`() {
        val result = AiPreviewPlaybackResolver.build(
            "content://source",
            clip,
            listOf(AiPreviewReadySegment("project", "clip", 2_000_000L, 4_000_000L, "/cache/a.mp4", "key"))
        )
        val clipSourceStartMs = clip.sourceStartUs / 1_000L

        val inside = AiPreviewPlaybackResolver.locate(result, absoluteSourcePositionMs = 5_000L, clipSourceStartMs = clipSourceStartMs)
        assertEquals(1, inside?.first)
        assertEquals(1_000L, inside?.second)
        assertEquals(
            5_000L,
            AiPreviewPlaybackResolver.absoluteSourcePositionMs(result, inside!!.first, inside.second, clipSourceStartMs)
        )

        val beforeBoundary = AiPreviewPlaybackResolver.locate(result, 3_999L, clipSourceStartMs)!!
        val atBoundary = AiPreviewPlaybackResolver.locate(result, 4_000L, clipSourceStartMs)!!
        assertTrue(beforeBoundary.first <= atBoundary.first)
        assertEquals(3_999L, AiPreviewPlaybackResolver.absoluteSourcePositionMs(result, beforeBoundary.first, beforeBoundary.second, clipSourceStartMs))
        assertEquals(4_000L, AiPreviewPlaybackResolver.absoluteSourcePositionMs(result, atBoundary.first, atBoundary.second, clipSourceStartMs))
    }

    @Test
    fun `segments from another clip are ignored`() {
        val result = AiPreviewPlaybackResolver.build(
            "content://source",
            clip,
            listOf(AiPreviewReadySegment("project", "other", 1_000_000L, 2_000_000L, "/cache/wrong.mp4", "key"))
        )
        assertTrue(result.isEmpty())
    }
}
