@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.render.effects

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.Effect
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.videoflow.app.domain.effects.*

/** Both ExoPlayer preview and the production Composition use this exact ordered factory. */
object VisualEffectPipeline {
    fun create(edits: VisualEdits, clipId: String, sourceOffsetUs: Long = 0L, speed: Double = 1.0): List<Effect> =
        VisualStage.ordered(edits,clipId,sourceOffsetUs,speed).map(::VisualGlEffect)

    /** Update shader parameters without registering a new input stream or clearing replay frames. */
    fun updatePreview(current: List<Effect>, requested: List<Effect>): Boolean {
        if(current.size!=requested.size) return false
        val pairs=current.zip(requested)
        if(pairs.any { (a,b) -> a !is VisualGlEffect || b !is VisualGlEffect || !a.sameStage(b) })
            return current==requested
        pairs.forEach { (a,b) -> (a as VisualGlEffect).update(b as VisualGlEffect) }
        return true
    }
}

private class VisualGlEffect(stage: VisualStage) : GlEffect {
    private val parameters=java.util.concurrent.atomic.AtomicReference(stage)
    fun sameStage(other: VisualGlEffect): Boolean {
        val a=parameters.get();val b=other.parameters.get()
        return a.node?.id==b.node?.id && a.node?.type==b.node?.type
    }
    fun update(other: VisualGlEffect) { parameters.set(other.parameters.get()) }
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = VisualShader(parameters::get, useHdr)
}

/** One output texture per stage, independent of duration. No CPU frame readback/cache. */
private class VisualShader(
    private val stageProvider: () -> VisualStage, useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX, FRAGMENT)
    private var width = 1
    private var height = 1
    init {
        require(!useHdr) { "Effects and Enhance currently require explicit SDR export; HDR preservation is not certified." }
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4)
    }
    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth; height = inputHeight
        return Size(width, height)
    }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val stage=stageProvider()
        val localTimeUs = stage.localTimeUs(presentationTimeUs)
        val node=stage.node; val enhance=stage.enhance
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatsUniform("uPixel", floatArrayOf(1f / width, 1f / height))
        program.setFloatUniform("uTime", ((localTimeUs % 60_000_000L).toDouble() / 1_000_000.0).toFloat())
        program.setFloatUniform("uMode", node?.type?.ordinal?.plus(1)?.toFloat() ?: 0f)
        program.setFloatUniform("uAmount", stage.amountAt(presentationTimeUs))
        program.setFloatsUniform("uBasic", floatArrayOf(enhance[Adjustment.EXPOSURE], enhance[Adjustment.BRIGHTNESS], enhance[Adjustment.CONTRAST], enhance[Adjustment.SATURATION]))
        program.setFloatsUniform("uTone", floatArrayOf(enhance[Adjustment.HIGHLIGHTS], enhance[Adjustment.SHADOWS], enhance[Adjustment.TEMPERATURE], enhance[Adjustment.TINT]))
        program.setFloatsUniform("uDetail", floatArrayOf(enhance[Adjustment.SHARPEN], enhance[Adjustment.CLARITY], enhance[Adjustment.VIGNETTE]))
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }
    override fun release() { super.release(); program.delete() }
}

private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTex;
void main(){ gl_Position=aFramePosition; vTex=aFramePosition.xy*0.5+0.5; }
"""

// Original VideoFlow shader. Input/output remain linear BT.709 as required by Media3 SDR effects.
// Range decisions use Long on the CPU; modulo time here is only for periodic artistic animation.
private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uPixel;
uniform float uTime;
uniform float uMode;
uniform float uAmount;
uniform vec4 uBasic;
uniform vec4 uTone;
uniform vec3 uDetail;
varying vec2 vTex;
float luma(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
float noise(vec2 p){return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453);}
vec3 sampleAt(vec2 p){return texture2D(uTexSampler,clamp(p,vec2(0.0),vec2(1.0))).rgb;}
vec3 blurAt(vec2 p,float radius){
 vec2 d=uPixel*radius;
 return (sampleAt(p)*4.0+sampleAt(p+d)+sampleAt(p-d)+sampleAt(p+vec2(d.x,-d.y))+sampleAt(p+vec2(-d.x,d.y)))/8.0;
}
void main(){
 vec2 p=vTex; float a=uAmount;
 if(uMode==14.0) p=(p-0.5)/(1.0+a*0.12*(0.5+0.5*sin(uTime*6.283185)))+0.5;
 if(uMode==15.0) p=(p-0.5)/(1.0+0.06*a)+0.5+vec2(sin(uTime*37.0),cos(uTime*43.0))*a*0.025;
 if(uMode==16.0) p=(p-0.5)/(1.0+0.02*a)+0.5+vec2(sin(uTime*53.0),cos(uTime*61.0))*a*0.005;
 vec3 c=sampleAt(p); float alpha=texture2D(uTexSampler,p).a;
 if(uMode==0.0){
   vec3 original=c;
   c*=exp2(uBasic.x*2.0); c+=uBasic.y*0.15;
   c=(c-0.18)*(1.0+uBasic.z*0.5)+0.18;
   float l=clamp(luma(c),0.0,1.0);
   c+=uTone.x*l*l*(1.0-l)*0.7+uTone.y*(1.0-l)*(1.0-l)*l*0.7;
   c=mix(vec3(luma(c)),c,1.0+uBasic.w);
   c*=vec3(1.0+uTone.z*0.12+uTone.w*0.05,1.0-uTone.w*0.08,1.0-uTone.z*0.12+uTone.w*0.05);
   c+=(original-blurAt(p,1.0))*max(uDetail.x,0.0)*0.8;
   c+=(original-blurAt(p,8.0))*uDetail.y*0.3;
   c*=1.0-max(uDetail.z,0.0)*smoothstep(0.1,0.7,length(p-0.5))*0.65;
 }
 if(uMode==1.0) c=mix(c,blurAt(p,1.0+a*18.0),a);
 if(uMode==2.0) c*=1.0-a*smoothstep(0.1,0.7,length(p-0.5))*0.75;
 if(uMode==3.0) c+=(c-blurAt(p,1.0))*a;
 if(uMode==4.0) c=mix(c,c*0.78+vec3(0.08),a);
 if(uMode==5.0) c=mix(c,vec3(luma(c)),a);
 if(uMode==6.0) c=mix(c,vec3(dot(c,vec3(.393,.769,.189)),dot(c,vec3(.349,.686,.168)),dot(c,vec3(.272,.534,.131))),a);
 if(uMode==7.0) c+=(noise(p/uPixel+floor(uTime*24.0))-0.5)*a*0.08;
 if(uMode==8.0) c=vec3(sampleAt(p+vec2(a*0.01,0.0)).r,c.g,sampleAt(p-vec2(a*0.01,0.0)).b);
 if(uMode==9.0){float shift=(noise(vec2(floor(p.y*24.0),floor(uTime*12.0)))-0.5)*a*0.07;c=sampleAt(p+vec2(shift,0.0));}
 if(uMode==10.0){c=mix(c,blurAt(p,2.0),a*0.4);c*=1.0-a*0.12*(0.5+0.5*sin(p.y/uPixel.y*3.14159));c+=(noise(p+floor(uTime*25.0))-0.5)*a*0.035;}
 if(uMode==11.0){vec2 d=(p-0.5)*a*0.018;c=vec3(sampleAt(p+d).r,c.g,sampleAt(p-d).b);}
 if(uMode==12.0) c*=1.0+a*0.15*sin(uTime*6.283185);
 if(uMode==13.0) c*=1.0-a*0.18*noise(vec2(floor(uTime*12.0),0.0));
 gl_FragColor=vec4(clamp(c,0.0,1.0),alpha);
}
"""
