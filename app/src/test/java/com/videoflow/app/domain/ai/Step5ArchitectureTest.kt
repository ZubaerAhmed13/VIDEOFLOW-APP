package com.videoflow.app.domain.ai

import com.videoflow.app.domain.editor.TimelineViewport
import com.videoflow.app.data.media.FingerprintEngine
import com.videoflow.app.data.media.RandomAccessReader
import com.videoflow.app.data.diagnostics.DiagnosticRedaction
import org.junit.Assert.*
import org.junit.Test

class Step5ArchitectureTest {
    @Test fun thirtyMinutesOneTwoFourEightHoursCoverEveryBoundaryAndResumeOffset() {
        for(duration in listOf(1_800_000_000L,3_600_000_000L,7_200_000_000L,14_400_000_000L,28_800_000_000L)) {
            val plan=AiLongJobPlan(duration+123_456L)
            var cursor=0L
            for(segment in plan.segments()) {
                assertEquals(cursor,segment.startUs)
                assertTrue(segment.durationUs in 1L..60_000_000L)
                assertEquals(segment.startUs,plan.segments(segment.index).first().startUs)
                cursor=segment.endUs
            }
            assertEquals(duration+123_456L,cursor)
            assertEquals(.5f,plan.progress(plan.durationUs/2),.00001f)
            assertTrue(plan.storageBytes(100_000_000L)>duration/1_000_000L*12_500_000L)
            assertEquals(3,AiResourceProfile().maximumQueuedTasks)
        }
    }
    @Test fun eightHourTimelineReachesLastMicrosecondsAtEveryZoom() {
        val duration=28_800_000_000L
        for(scale in listOf(12f,60f,240f)) {
            for(time in listOf(0L,1_800_000_000L,3_600_000_000L,duration-1)) {
                val origin=TimelineViewport.centeredOrigin(time,scale,duration)
                val x=TimelineViewport.positionDp(time,origin,scale)
                assertTrue(x>=0 && x<=TimelineViewport.MAX_WIDTH_DP)
                assertTrue(kotlin.math.abs(time-TimelineViewport.timeAt(origin,x,scale))<=1)
            }
        }
    }
    @Test fun pinchKeepsLogicalAnchorWithoutLargeFloatTimestamps() {
        val origin=20_000_000_000L
        val anchor=412.25
        val next=TimelineViewport.zoomOrigin(origin,anchor,60f,120f,28_800_000_000L)
        assertTrue(kotlin.math.abs(TimelineViewport.timeAt(origin,anchor,60f)-TimelineViewport.timeAt(next,anchor,120f))<=1)
        assertFalse(TimelineViewport.intersects(0,1_000,1_000,2_000))
        assertTrue(TimelineViewport.intersects(999,2_000,1_000,2_000))
    }
    @Test fun twoThreeAndSixGiBUseBoundedActualFingerprintReads() {
        for(size in listOf(2_147_483_649L,3L*1024*1024*1024,6L*1024*1024*1024+77)) {
            var largest=0L;var bytes=0L
            val reader=object: RandomAccessReader {
                override val size=size
                override fun readAt(offset: Long,buffer: ByteArray,length: Int): Int {
                    assertTrue(buffer.size<=256*1024);assertTrue(offset>=0 && offset<size)
                    largest=maxOf(largest,offset);bytes+=length
                    for(i in 0 until length) buffer[i]=((offset+i) and 255).toByte()
                    return length
                }
                override fun close() {}
            }
            val hash=FingerprintEngine().fingerprint(reader,3_600_000_000L,3840,2160)
            assertEquals(64,hash.sha256.length)
            assertEquals(12L*1024*1024,bytes)
            assertTrue(largest>Int.MAX_VALUE-256*1024L)
        }
    }
    @Test fun landscapeAndPortraitFourKTilesPreserveEveryPixelAtAllRotations() {
        for(rotation in listOf(0,90,180,270)) {
            val w=if(rotation%180==0) 3840 else 2160
            val h=if(rotation%180==0) 2160 else 3840
            val target=AiWatermarkMath.toPixelRect(NormalizedRoi(0f,0f,1f,1f),w,h)
            val tiles=AiWatermarkMath.planTiles(target,w,h)
            assertEquals(w.toLong()*h,tiles.sumOf { it.core.width.toLong()*it.core.height })
            assertTrue(tiles.all { it.read.width<=512 && it.read.height<=512 })
            assertEquals(target,AiWatermarkMath.toOpenGlRect(AiWatermarkMath.toOpenGlRect(target,h),h))
        }
    }
    @Test fun diagnosticsRemoveMediaUrisPrivatePathsAndControlCharacters() {
        val clean=DiagnosticRedaction.clean("Failed content://provider/private.mp4 and /storage/emulated/0/secret.mp4\nhttps://example.test/upload?q=private")
        assertFalse(clean.contains("secret"));assertFalse(clean.contains("private.mp4"));assertFalse(clean.contains("example.test"));assertFalse(clean.contains('\n'))
    }
}
