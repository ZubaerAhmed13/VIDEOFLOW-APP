package com.videoflow.app.domain.ai

import com.videoflow.app.domain.effects.*
import org.junit.Assert.*
import org.junit.Test

class VisualPreviewFinalParityTest {
    @Test fun allEffectsUseIdenticalParametersAtTrimmedSpedAndSegmentedTimes() {
        val edits=VisualEdits(VisualEffectType.entries.mapIndexed { index,type ->
            VideoEffectNode("fx-$index","clip",type,1_000_000L,3_000_000L,.3f,order=index)
        },mapOf("clip" to EnhanceParameters(mapOf(Adjustment.EXPOSURE to .1f,Adjustment.TINT to -.2f))))
        val preview=VisualStage.ordered(edits,"clip",10_000_000L,2.0)
        val finalStages=VisualStage.ordered(edits,"clip")
        val resumed=VisualStage.ordered(edits,"clip",-1_000_000L)
        assertEquals(17,preview.size)
        for (time in listOf(0L,999_999L,1_000_000L,2_234_567L,2_999_999L,3_000_000L,3_600_000_000L)) {
            for (index in finalStages.indices) {
                assertEquals(finalStages[index].node,preview[index].node)
                assertEquals(finalStages[index].enhance,preview[index].enhance)
                assertEquals(time,preview[index].localTimeUs(10_000_000L+time*2))
                assertEquals(finalStages[index].amountAt(time),preview[index].amountAt(10_000_000L+time*2),0f)
                if(time>=1_000_000L) assertEquals(finalStages[index].amountAt(time),resumed[index].amountAt(time-1_000_000L),0f)
            }
        }
        assertNull(finalStages.first().node)
        assertEquals(VisualEffectType.entries,finalStages.drop(1).map { it.node!!.type })
    }
    @Test fun disabledZeroAndOtherClipsCannotReachEitherPipeline() {
        val edits=VisualEdits(listOf(VideoEffectNode("off","clip",VisualEffectType.BLUR,0,9,enabled=false),
            VideoEffectNode("zero","clip",VisualEffectType.SEPIA,0,9,intensity=0f),VideoEffectNode("other","else",VisualEffectType.SHAKE,0,9)))
        assertTrue(VisualStage.ordered(edits,"clip").isEmpty())
    }
}
