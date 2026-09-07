package com.videoflow.app.domain.ai

import org.junit.Assert.*
import org.junit.Test
import com.videoflow.app.domain.effects.*

class ProfessionalRangeTest {
    @Test fun longRangesRemainHalfOpenAndOrdered() {
        val hour = 3_600_000_000L
        val a = VideoEffectNode("a","clip",VisualEffectType.BLUR,hour,hour*3, order=1)
        val b = a.copy(id="b",order=0)
        assertFalse(a.activeAt(hour-1)); assertTrue(a.activeAt(hour)); assertFalse(a.activeAt(hour*3))
        assertEquals(listOf(b,a),VisualEdits(listOf(a,b)).ordered("clip"))
        assertFalse(a.copy(enabled=false).activeAt(hour))
        assertFalse(a.copy(intensity=0f).activeAt(hour))
    }
    @Test fun enhanceIdentityAndConservativeRecommendations() {
        assertTrue(EnhanceParameters().isIdentity)
        for (mean in listOf(0.0,.1,.5,.9,1.0)) {
            val p = AutoEnhanceRecommendation.fromLuminance(mean,.8,.8)
            assertTrue(p.values.values.all { it in -.15f.. .15f })
            assertEquals(0f,p[Adjustment.TEMPERATURE],0f)
            assertEquals(0f,p[Adjustment.SATURATION],0f)
        }
    }
    @Test fun manualCorrectionInterpolatesPositionAndSizeAtLongTimestamps() {
        val t = 3_600_000_000L
        val effect = AiWatermarkEffect("id","project","clip",0,t*3,NormalizedRoi(.1f,.1f,.3f,.3f),
            listOf(RoiMotionAnchor(t,.2f,.2f,width=.2f,height=.2f,manual=true),
                RoiMotionAnchor(t*2,.6f,.6f,width=.4f,height=.4f,manual=true)))
        val roi = effect.roiAt(t+t/2)
        assertEquals(.3f,roi.width,.0001f)
        assertEquals(.25f,roi.left,.0001f)
        val tiles = AiWatermarkMath.planTiles(AiWatermarkMath.toPixelRect(roi,3840,2160),3840,2160)
        assertTrue(tiles.all { it.read.width<=512 && it.read.height<=512 })
        val pixels = tiles.sumOf { it.core.width.toLong()*it.core.height }
        val rectangle = AiWatermarkMath.toPixelRect(roi,3840,2160)
        assertEquals(rectangle.width.toLong()*rectangle.height,pixels)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidIntensityRejected() {
        VideoEffectNode("a","b",VisualEffectType.BLUR,0,1,Float.NaN)
    }
}
