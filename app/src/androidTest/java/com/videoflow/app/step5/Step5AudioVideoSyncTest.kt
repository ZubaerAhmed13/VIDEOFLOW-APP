@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.media.*
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.domain.effects.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteOrder
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class Step5AudioVideoSyncTest {
    @Test fun decodedFlashAndToneStayAlignedAfterTrimAndSpeedAndVisualProcessing() = runBlocking {
        val f=Step5MediaFixture()
        try {
            val source=f.source("step5-sync.mp4")
            f.evidence("sync-diagnostics.txt","source video events=${videoEvents(f,source,2_000_000L)}")
            f.preserve(source,"sync-source.mp4")
            for(speed in listOf(1.0,2.0,.5)) {
                val clip=f.clip.copy(sourceStartUs=200_000L,sourceEndUs=1_800_000L,speed=speed)
                f.ai.visualEdits.replace(f.id,VisualEdits(
                    listOf(VideoEffectNode("sepia",clip.id,VisualEffectType.SEPIA,0,clip.timelineDurationUs,.2f)),
                    mapOf(clip.id to EnhanceParameters(mapOf(Adjustment.EXPOSURE to .1f)))))
                val output=f.render(f.plan(source,clip)).first
                val video=videoEvents(f,output,clip.timelineDurationUs)
                val audio=audioEvents(f,output)
                f.evidence("sync-diagnostics.txt","speed=$speed video=$video audio=$audio")
                f.preserve(output,"sync-output-$speed.mp4")
                assertEquals("video event count at speed $speed: $video",2,video.size)
                assertEquals("audio event count at speed $speed: $audio",2,audio.size)
                for(i in 0..1) {
                    val delta=abs(video[i]-audio[i])
                    assertTrue("A/V drift at speed $speed event $i: video=$video audio=$audio",delta<=66_667L)
                    val expected=((listOf(600_000L,1_500_000L)[i]-200_000L)/speed).toLong()
                    assertTrue("Both streams shifted from intended edit timing",abs(video[i]-expected)<=66_667L)
                    f.evidence("av-sync.jsonl","{\"speed\":$speed,\"event\":$i,\"video_us\":${video[i]},\"audio_us\":${audio[i]},\"difference_us\":$delta,\"tolerance_us\":66667}")
                }
            }
        } finally { f.close() }
    }
    @Test fun variableSourceTimestampsNormalizeToRequestedFractionalOutputCadence()=runBlocking {
        val f=Step5MediaFixture()
        try {
            val plan=f.plan(f.source("step5-vfr.mp4"))
            for(rate in listOf(com.videoflow.app.domain.editor.FrameRate.FPS_2997,com.videoflow.app.domain.editor.FrameRate.FPS_5994)) {
                val result=f.render(plan,f.settings.copy(frameRate=rate)).second
                assertTrue(result.validation.problems.toString(),result.validation.passed)
                assertTrue(com.videoflow.app.render.FrameCadenceVerifier.matches(checkNotNull(result.validation.video?.measuredFrameRate),rate))
                f.evidence("vfr-cadence.jsonl","{\"requested_fps\":${rate.fps},\"measured_fps\":${result.validation.video!!.measuredFrameRate}}")
            }
        } finally { f.close() }
    }
    @Test fun stereo44100SourceUsesRequestedSampleRateAndChannelCount()=runBlocking {
        val f=Step5MediaFixture()
        try {
            val original=f.plan(f.source("step5-44100.mp4"))
            val plan=original.copy(originalSources=original.originalSources.mapValues { (_,s) -> s.copy(audioSampleRate=44_100,audioChannelCount=2) })
            for(channels in listOf(1,2)) {
                val result=f.render(plan,f.settings.copy(audioSampleRate=48_000,audioChannels=channels)).second
                assertTrue(result.validation.problems.toString(),result.validation.passed)
                assertEquals(48_000,result.validation.audio!!.sampleRate)
                assertEquals(channels,result.validation.audio!!.channelCount)
            }
        } finally { f.close() }
    }
    @Test fun fadesFollowTimelineTimeAfterUpstreamSpeedConversion()=runBlocking {
        val f=Step5MediaFixture()
        try {
            val source=f.source()
            for(speed in listOf(.5,2.0)) {
                val clip=f.clip.copy(sourceStartUs=200_000L,sourceEndUs=1_800_000L,speed=speed,fadeInUs=200_000L,fadeOutUs=200_000L)
                val output=f.render(f.plan(source,clip)).first
                val ranges=listOf(40_000L..80_000L,(clip.timelineDurationUs/2-50_000L)..(clip.timelineDurationUs/2+50_000L),
                    (clip.timelineDurationUs-80_000L)..(clip.timelineDurationUs-40_000L))
                val sum=DoubleArray(3);val count=IntArray(3)
                audioEvents(f,output) { time,value -> ranges.forEachIndexed { index,range -> if(time in range) { sum[index]+=value;count[index]++ } } }
                val means=sum.indices.map { assertTrue(count[it]>0);sum[it]/count[it] }
                assertTrue("Missing audible middle at speed $speed: $means",means[1]>500.0)
                for(index in listOf(0,2)) assertTrue("Fade used source time twice at speed $speed: $means",means[index]/means[1] in .15.. .45)
                f.evidence("audio-fades.jsonl","{\"speed\":$speed,\"early_amplitude\":${means[0]},\"middle_amplitude\":${means[1]},\"late_amplitude\":${means[2]}}")
            }
        } finally { f.close() }
    }
    private fun videoEvents(f: Step5MediaFixture,uri: Uri,duration: Long): List<Long> {
        val retriever=MediaMetadataRetriever();val events=mutableListOf<Long>();var active=false
        try {
            retriever.setDataSource(f.context,uri)
            var time=0L
            while(time<duration) {
                val b=checkNotNull(retriever.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST))
                val white=try { (b.getPixel(b.width/2,b.height/2) and 255)>100 } finally { b.recycle() }
                if(white && !active) events+=time
                active=white;time+=16_667L
            }
        } finally { retriever.release() }
        return events
    }
    private fun audioEvents(f: Step5MediaFixture,uri: Uri,onSample: ((Long,Int)->Unit)?=null): List<Long> {
        val extractor=MediaExtractor();var codec: MediaCodec?=null
        val events=mutableListOf<Long>();var lastLoud=-1_000_000L
        try {
            extractor.setDataSource(f.context,uri,null)
            val track=(0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
            val format=extractor.getTrackFormat(track);extractor.selectTrack(track)
            val decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!);codec=decoder
            decoder.configure(format,null,null,0);decoder.start()
            var inputDone=false;var done=false;val info=MediaCodec.BufferInfo()
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val deadline=android.os.SystemClock.elapsedRealtime()+30_000L
            while(!done) {
                check(android.os.SystemClock.elapsedRealtime()<deadline) { "Audio decoder stalled" }
                if(!inputDone) {
                    val index=decoder.dequeueInputBuffer(10_000)
                    if(index>=0) {
                        val buffer=decoder.getInputBuffer(index)!!;buffer.clear()
                        val n=extractor.readSampleData(buffer,0)
                        if(n<0) { decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true }
                        else { decoder.queueInputBuffer(index,0,n,extractor.sampleTime,0);extractor.advance() }
                    }
                }
                val index=decoder.dequeueOutputBuffer(info,10_000)
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    rate=decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if(decoder.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) check(decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)==AudioFormat.ENCODING_PCM_16BIT)
                }
                if(index>=0) {
                    val buffer=decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset);buffer.limit(info.offset+info.size)
                    var sample=0
                    while(buffer.remaining()>=2) {
                        val value=abs(buffer.short.toInt())
                        val time=info.presentationTimeUs+(sample/channels).toLong()*1_000_000L/rate
                        onSample?.invoke(time,value)
                        if(value>5000) { if(time-lastLoud>100_000L) events+=time;lastLoud=time }
                        sample++
                    }
                    done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index,false)
                }
            }
        } finally { codec?.stop();codec?.release();extractor.release() }
        return events
    }
}
