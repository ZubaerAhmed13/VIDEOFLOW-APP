@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.effects.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class Step5QualityExportTest {
    @Test fun everyEffectAndEnhanceEndpointChangesRealEncodedPixels() = runBlocking {
        val f=Step5MediaFixture()
        try {
            val source=f.source("step5-colour.mp4")
            val plan=f.plan(source,f.clip.copy(sourceEndUs=1_000_000L))
            val baseline=f.render(plan).first
            val times=listOf(100_000L,366_667L,733_333L)
            val identity=times.map { pixels(f,baseline,it) }
            val original=times.map { pixels(f,source,it) }
            val mae=identity.indices.map { difference(identity[it],original[it]) }.average()
            assertTrue("SDR identity mean RGB error $mae",mae<12.0)
            f.evidence("quality.jsonl","{\"check\":\"sdr_identity\",\"mean_rgb_error\":$mae,\"threshold\":12.0}")
            fun descriptors()=java.io.File("/proc/self/fd").listFiles()!!.mapNotNull { runCatching { android.system.Os.readlink(it.path) }.getOrNull() }.groupingBy { it }.eachCount()
            val initialDescriptors=descriptors()
            val initialFds=initialDescriptors.values.sum()
            val cases=VisualEffectType.entries.map { type -> type.name to VisualEdits(listOf(VideoEffectNode(type.name,f.clip.id,type,0L,1_000_000L,1f))) }+
                Adjustment.entries.flatMap { adjustment -> (if(adjustment in setOf(Adjustment.SHARPEN,Adjustment.VIGNETTE)) listOf(0f,1f) else listOf(-1f,1f)).map { value ->
                    "${adjustment.name}-$value" to VisualEdits(enhance=mapOf(f.clip.id to EnhanceParameters(mapOf(adjustment to value))))
                } }
            var renderDescriptorGrowth=0;var frameReadDescriptorGrowth=0
            for((name,edits) in cases) {
                f.ai.visualEdits.replace(f.id,edits)
                val beforeRender=descriptors().values.sum()
                val (uri,result)=f.render(plan)
                val afterRender=descriptors().values.sum()
                renderDescriptorGrowth+=afterRender-beforeRender
                assertTrue(result.validation.passed)
                val change=times.indices.maxOf { difference(identity[it],pixels(f,uri,times[it])) }
                val afterFrames=descriptors().values.sum()
                frameReadDescriptorGrowth+=afterFrames-afterRender
                f.evidence("fd-phases.jsonl","{\"control\":\"$name\",\"before_render\":$beforeRender,\"after_render\":$afterRender,\"after_test_frame_reads\":$afterFrames}")
                if(name.endsWith("-0.0")) assertTrue("$name identity error $change",change<2.0)
                else assertTrue("$name did not visibly change decoded export pixels: $change",change>.25)
                f.evidence("quality.jsonl","{\"control\":\"$name\",\"max_mean_rgb_difference\":$change,\"threshold\":0.25}")
            }
            val unsettled=descriptors()
            // Distinguish unreachable framework cleanup objects from a retained live-resource leak.
            System.gc();System.runFinalization();kotlinx.coroutines.delay(1_000)
            val settled=descriptors()
            val finalFds=settled.values.sum()
            f.evidence("fd-details.txt","initial=$initialDescriptors\nunsettled=$unsettled\nsettled=$settled\nthreads=${Thread.getAllStackTraces().keys.map { it.name }}")
            assertTrue("Production renders leaked descriptors: $renderDescriptorGrowth; separate test frame reads: $frameReadDescriptorGrowth",renderDescriptorGrowth<20)
            f.evidence("resources.jsonl","{\"initial_fds\":$initialFds,\"final_fds\":$finalFds,\"render_descriptor_growth\":$renderDescriptorGrowth,\"test_frame_read_descriptor_growth\":$frameReadDescriptorGrowth,\"pss_kb\":${android.os.Debug.getPss()}}")
            f.ai.visualEdits.replace(f.id,VisualEdits())
            val reset=f.render(plan).first
            assertTrue(difference(identity[1],pixels(f,reset,times[1]))<2.0)
        } finally { f.close() }
    }
    private fun pixels(f: Step5MediaFixture,uri: Uri,time: Long): IntArray {
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(f.context,uri)
            val bitmap=checkNotNull(retriever.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST))
            return try { IntArray(80*60) { i -> bitmap.getPixel((i%80)*bitmap.width/80,(i/80)*bitmap.height/60) } }
            finally { bitmap.recycle() }
        } finally { retriever.release() }
    }
    private fun difference(a: IntArray,b: IntArray): Double = a.indices.sumOf { i ->
        listOf(0,8,16).sumOf { shift -> abs(((a[i] ushr shift) and 255)-((b[i] ushr shift) and 255)) }
    }.toDouble()/(a.size*3)
}
