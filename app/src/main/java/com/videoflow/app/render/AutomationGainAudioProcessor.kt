package com.videoflow.app.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeProperty
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Converts decoded PCM to float and applies clip/track gain, AUDIO_GAIN keyframes and bounded fades.
 * Values are intentionally allowed above unity here so multi-track mixing keeps floating-point
 * headroom. The final audio mixer clips only after sources have been summed.
 */
class AutomationGainAudioProcessor(
    private val baseGainDb: Float,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    private val timelineDurationUs: Long,
    private val speed: Double,
    private val gainKeyframes: List<Keyframe>
) : BaseAudioProcessor() {
    private var inputFramesProcessed = 0L

    init {
        require(baseGainDb.isFinite())
        require(fadeInUs >= 0 && fadeOutUs >= 0)
        require(timelineDurationUs > 0)
        require(speed.isFinite() && speed > 0)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_FLOAT
        )
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        inputFramesProcessed = 0L
    }

    override fun onReset() {
        inputFramesProcessed = 0L
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = bytesPerSample * inputAudioFormat.channelCount
        val frameCount = inputBuffer.remaining() / frameSize
        if (frameCount <= 0) return
        val output = replaceOutputBuffer(frameCount * inputAudioFormat.channelCount * 4).order(ByteOrder.nativeOrder())
        repeat(frameCount) {
            val sourceTimeUs = inputFramesProcessed * 1_000_000L / inputAudioFormat.sampleRate
            val timelineTimeUs = (sourceTimeUs.toDouble() / speed).toLong().coerceIn(0L, timelineDurationUs)
            val factor = gainFactor(timelineTimeUs)
            repeat(inputAudioFormat.channelCount) {
                val sample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    inputBuffer.float
                } else {
                    inputBuffer.short.toFloat() / 32768f
                }
                output.putFloat(sample * factor)
            }
            inputFramesProcessed++
        }
        output.flip()
    }

    private fun gainFactor(timeUs: Long): Float {
        val db = KeyframeEvaluator.evaluate(
            baseGainDb,
            timeUs,
            gainKeyframes.filter { it.property == KeyframeProperty.AUDIO_GAIN }
        )
        val base = 10.0.pow(db.toDouble() / 20.0).toFloat()
        val fadeIn = if (fadeInUs <= 0) 1f else (timeUs.toDouble() / fadeInUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        val fadeOutStart = (timelineDurationUs - fadeOutUs).coerceAtLeast(0L)
        val fadeOut = if (fadeOutUs <= 0 || timeUs <= fadeOutStart) 1f else {
            ((timelineDurationUs - timeUs).toDouble() / fadeOutUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
        return base * fadeIn * fadeOut
    }
}
