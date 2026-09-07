package com.videoflow.app.domain.ai

import com.videoflow.app.domain.editor.*
import com.videoflow.app.domain.export.*
import com.videoflow.app.domain.effects.*
import com.videoflow.app.render.AiCheckpointIdentity
import com.videoflow.app.ai.watermark.AiTemporalCheckpoint
import org.junit.Assert.*
import org.junit.Test

class Step5CheckpointIdentityTest {
    private val clip=TimelineClip("c","p","t","a",0,0,3_600_000_000L)
    private val source=OriginalRenderSource("a","content://media/1","a.mp4","video/mp4",3_500_000_000L,3_600_000_000L,
        3840,2160,0,30.0,"video/avc","audio/mp4a-latm",48000,2,40_000_000,null,null,null,false,"source-hash")
    private val plan=FinalRenderPlan(RenderPlan("p",3840,2160,FrameRate.FPS_30,listOf(TimelineTrack("t","p",TrackType.VIDEO,"V",0)),
        listOf(clip),emptyList(),emptyList(),emptyList(),0xFF000000L),mapOf("a" to source),3_600_000_000L)
    private val settings=ExportMath.resolve(ExportSize(3840,2160),FrameRate.FPS_30,ExportSettings())
    private val effect=AiWatermarkEffect("e","p","c",0,3_600_000_000L,NormalizedRoi(.1f,.1f,.2f,.2f))
    private fun key(p: FinalRenderPlan=plan,s: ResolvedExportSettings=settings,e: List<AiWatermarkEffect> = listOf(effect),
        v: VisualEdits=VisualEdits(),fingerprint: String="sampled",interval: Long=60_000_000L,model: String="approved") =
        AiCheckpointIdentity.key(p,s,e,v,fingerprint,interval,model)
    @Test fun everyRelevantSourceEditRenderAndModelChangeInvalidatesResume() {
        val base=key();assertEquals(base,key());assertEquals(64,base.length)
        val variants=listOf(
            key(p=plan.copy(originalSources=mapOf("a" to source.copy(sourceUri="content://media/2")))),
            key(p=plan.copy(originalSources=mapOf("a" to source.copy(fingerprintSha256="changed")))),
            key(p=plan.copy(editorPlan=plan.editorPlan.copy(clips=listOf(clip.copy(sourceStartUs=1_000_000L))))),
            key(p=plan.copy(editorPlan=plan.editorPlan.copy(clips=listOf(clip.copy(speed=2.0))))),
            key(p=plan.copy(editorPlan=plan.editorPlan.copy(clips=listOf(clip.copy(gainDb=-6f))))),
            key(p=plan.copy(editorPlan=plan.editorPlan.copy(clips=listOf(clip.copy(transform=ClipTransform(rotationDegrees=90f)))))),
            key(p=plan.copy(editorPlan=plan.editorPlan.copy(textOverlays=listOf(TextOverlay("o","p","t",0,100,"Title"))))),
            key(s=settings.copy(size=ExportSize(1920,1080))),key(s=settings.copy(videoCodec=VideoCodec.HEVC)),
            key(s=settings.copy(frameRate=FrameRate.FPS_24)),key(s=settings.copy(videoBitrate=settings.videoBitrate+1)),
            key(s=settings.copy(hdrPolicy=HdrPolicy.CONVERT_TO_SDR)),
            key(e=listOf(effect.copy(featherPx=12))),key(e=listOf(effect.copy(motionAnchors=listOf(RoiMotionAnchor(0,.8f,.8f,manual=true))))),
            key(v=VisualEdits(listOf(VideoEffectNode("fx","c",VisualEffectType.SEPIA,0,100)))),
            key(v=VisualEdits(enhance=mapOf("c" to EnhanceParameters(mapOf(Adjustment.EXPOSURE to .2f))))),
            key(fingerprint="changed"),key(interval=30_000_000L),key(model="new-model"))
        assertTrue(variants.all { it!=base });assertEquals(variants.size,variants.distinct().size)
    }
    @Test fun temporalStateDropsCompletedRegionsInsteadOfGrowingWithProjectDuration() {
        val state=AiTemporalCheckpoint()
        repeat(2000) { index ->
            state.retainEffects(setOf("region-$index"))
            state.store("region-$index:0",ByteArray(128))
            assertEquals(1,state.patches.size)
        }
        state.retainEffects(emptySet());assertTrue(state.patches.isEmpty())
    }
}
