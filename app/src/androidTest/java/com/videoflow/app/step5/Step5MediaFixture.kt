@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.ai.watermark.AiModelPackManager
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.domain.editor.*
import com.videoflow.app.domain.export.*
import com.videoflow.app.render.*
import java.io.File

internal class Step5MediaFixture {
    val instrumentation=InstrumentationRegistry.getInstrumentation()
    val context=instrumentation.targetContext
    val resolver=context.contentResolver
    val id="step5-${System.nanoTime()}"
    val ai=AiWatermarkRepository(context)
    val uris=mutableListOf<Uri>()
    val track=TimelineTrack("video",id,TrackType.VIDEO,"Video",0)
    val clip=TimelineClip("clip",id,track.id,"source",0L,0L,2_000_000L)
    val settings=ResolvedExportSettings(ExportSize(320,240),FrameRate.FPS_30,VideoCodec.H264,ExportQuality.BALANCED,
        BitrateMode.AUTO,4_000_000,AudioCodec.AAC_LC,128_000,48_000,1,HdrPolicy.PRESERVE_WHEN_COMPATIBLE,false)
    fun destination(): Uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME,"$id-${uris.size}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VideoFlowCertification")
    })!!.also { uris+=it }
    fun source(asset: String="sample_av.mp4"): Uri=destination().also { uri ->
        resolver.openOutputStream(uri)!!.use { output -> instrumentation.context.assets.open(asset).use { it.copyTo(output,64*1024) } }
    }
    fun plan(source: Uri,edited: TimelineClip=clip): FinalRenderPlan = FinalRenderPlan(
        RenderPlan(id,320,240,FrameRate.FPS_30,listOf(track),listOf(edited),emptyList(),emptyList(),emptyList(),0xFF000000L),
        mapOf("source" to OriginalRenderSource("source",source.toString(),"fixture.mp4","video/mp4",null,2_000_000L,
            320,240,0,30.0,"video/avc","audio/mp4a-latm",48_000,1,null,null,null,null,false,null)),
        edited.timelineStartUs+edited.timelineDurationUs)
    suspend fun render(plan: FinalRenderPlan): Pair<Uri,RenderExecutionResult> {
        val uri=destination()
        val engine=Media3RenderEngine(context,ai,AiModelPackManager(context))
        val preparation=engine.prepare(plan,OutputDestination(uri,"test.mp4"),settings)
        check(preparation.ready) { preparation.problems.toString() }
        return uri to engine.render(checkNotNull(preparation.preparation),RenderProgressListener {}).getOrThrow()
    }
    fun evidence(name: String,text: String) {
        File(context.getExternalFilesDir(null),"step5-evidence/$name").apply { parentFile!!.mkdirs(); appendText(text+"\n") }
    }
    suspend fun close() { ai.deleteProjectState(id);ai.visualEdits.delete(id);uris.forEach { resolver.delete(it,null,null) } }
}
