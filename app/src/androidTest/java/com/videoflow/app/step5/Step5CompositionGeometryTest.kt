@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.media.MediaMetadataRetriever
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.editor.*
import com.videoflow.app.domain.export.ExportSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step5CompositionGeometryTest {
    @Test fun backgroundFillsCanvasVideoFitsAndTextIsAboveVideoAtRequestedSize() = runBlocking {
        val f=Step5MediaFixture()
        try {
            val source=f.source("step5-sync.mp4")
            val base=f.plan(source)
            val plan=base.copy(editorPlan=base.editorPlan.copy(backgroundArgb=0xFF2244AAL))
            val settings=f.settings.copy(size=ExportSize(640,360))
            fun frame(uri: android.net.Uri): android.graphics.Bitmap {
                val r=MediaMetadataRetriever()
                return try { r.setDataSource(f.context,uri);checkNotNull(r.getFrameAtTime(633_333L,MediaMetadataRetriever.OPTION_CLOSEST)) }
                finally { r.release() }
            }
            val plain=frame(f.render(plan,settings).first)
            try {
                for(x in listOf(10,50,590,630)) for(y in listOf(10,180,350)) {
                    val c=plain.getPixel(x,y)
                    assertTrue("Background must cover letterbox at $x,$y: $c",kotlin.math.abs(Color.red(c)-34)<20 && kotlin.math.abs(Color.green(c)-68)<20 && kotlin.math.abs(Color.blue(c)-170)<20)
                }
                for(x in listOf(100,200,320,440,540)) for(y in listOf(20,180,340)) {
                    val c=plain.getPixel(x,y)
                    assertTrue("Video fit or primary clock covered $x,$y: $c",Color.red(c)>230 && Color.green(c)>230 && Color.blue(c)>230)
                }
            } finally { plain.recycle() }
            val track=TimelineTrack("text",f.id,TrackType.OVERLAY,"Text",2)
            val text=TextOverlay("text",f.id,track.id,0L,2_000_000L,"TEST",fontSizeSp=32f,colorArgb=0xFF000000L)
            val layered=plan.copy(editorPlan=plan.editorPlan.copy(tracks=plan.editorPlan.tracks+track,textOverlays=listOf(text)))
            val uri=f.render(layered,settings).first
            f.preserve(uri,"geometry-text.mp4")
            val bitmap=frame(uri)
            try {
                val ink=mutableListOf<Pair<Int,Int>>()
                for(y in 120 until 240) for(x in 200 until 440) {
                    val c=bitmap.getPixel(x,y)
                    if(Color.red(c)<40 && Color.green(c)<40 && Color.blue(c)<40) ink+=x to y
                }
                assertTrue("Text must be above opaque video",ink.size>100)
                val width=ink.maxOf { it.first }-ink.minOf { it.first }
                val height=ink.maxOf { it.second }-ink.minOf { it.second }
                assertTrue("Text was scaled twice: ${width}x$height",width>80 && height>20)
                f.evidence("composition-geometry.jsonl","{\"width\":640,\"height\":360,\"text_ink_width\":$width,\"text_ink_height\":$height,\"background_and_video_fit\":true}")
            } finally { bitmap.recycle() }
        } finally { f.close() }
    }
}
