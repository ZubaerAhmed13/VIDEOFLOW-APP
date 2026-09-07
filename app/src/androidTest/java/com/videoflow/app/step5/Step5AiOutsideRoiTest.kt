@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.ai.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class Step5AiOutsideRoiTest {
    @Test fun finalLamaChangesTargetWhileUnrelatedPixelsRetainColour()=runBlocking {
        val f=Step5MediaFixture()
        try {
            val plan=f.plan(f.source("step5-colour.mp4"))
            val before=frame(f,f.render(plan).first)
            f.ai.upsert(AiWatermarkEffect("real-final",f.id,f.clip.id,0L,40_000L,NormalizedRoi(.6f,.55f,.9f,.9f),
                contextPaddingPx=32,featherPx=6,temporalStability=0f))
            val after=frame(f,f.render(plan).first)
            var outside=0.0;var count=0;var inside=0.0;var insideCount=0
            for(y in 0 until 240 step 2) for(x in 0 until 320 step 2) {
                val a=before[y*320+x];val b=after[y*320+x]
                val diff=listOf(0,8,16).sumOf { shift -> abs(((a ushr shift) and 255)-((b ushr shift) and 255)) }/3.0
                if(x<176 || y<116 || x>304 || y>232) { outside+=diff;count++ }
                if(x in 202..276 && y in 142..204) { inside+=diff;insideCount++ }
            }
            outside/=count;inside/=insideCount
            assertTrue("Unrelated-region colour error $outside",outside<5.0)
            assertTrue("Real final AI target must visibly change: $inside",inside>1.0)
            f.evidence("ai-roi-quality.jsonl","{\"outside_rgb_error\":$outside,\"outside_threshold\":5.0,\"inside_rgb_change\":$inside,\"inside_minimum\":1.0}")
        } finally { f.close() }
    }
    private fun frame(f: Step5MediaFixture,uri: Uri): IntArray {
        val r=MediaMetadataRetriever()
        try { r.setDataSource(f.context,uri);val b=checkNotNull(r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST))
            return try { IntArray(320*240).also { b.getPixels(it,0,320,0,0,320,240) } } finally { b.recycle() }
        } finally { r.release() }
    }
}
