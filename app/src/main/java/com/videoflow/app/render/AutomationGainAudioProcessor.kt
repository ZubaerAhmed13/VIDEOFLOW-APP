package com.videoflow.app.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeProperty
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Applies clip/track gain, AUDIO_GAIN keyframes and bounded fades to decoded PCM while also
 * normalizing each Composition input to the requested mono/stereo 16-bit layout.
 *
 * Media3 Composition requires every audio item to output 16-bit PCM with the same channel count
 * before the audio graph can mix sequences. Decoders are free to expose PCM16 or PCM-float, so
 * this processor accepts both, performs automation in floating point, uses Media3's constant-power
 * channel matrix, then quantizes once at the item boundary required by Transformer.
 */
@UnstableApi
class AutomationGainAudioProcessor(
    private val baseGainDb: Float,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    private val timelineDurationUs: Long,
    private val speed: Double,
    private val gainKeyframes: List<Keyframe>,
    private val outputChannelCount: Int
) : BaseAudioProcessor() {
    private var inputFramesProcessed = 0L
    private var mixingMatrix: ChannelMixingMatrix? = null

    init {
        require(baseGainDb.isFinite())
        require(fadeInUs >= 0 && fadeOutUs >= 0)
        require(timelineDurationUs > 0)
        require(speed.isFinite() && speed > 0)
        require(outputChannelCount in 1..2) { "Step 3 export supports mono or stereo AAC output" }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount !in 1..6) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        mixingMatrix = ChannelMixingMatrix.createForConstantPower(
            inputAudioFormat.channelCount,
            outputChannelCount
        )
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            outputChannelCount,
            C.ENCODING_PCM_16BIT
        )
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        inputFramesProcessed = 0L
    }

    override fun onReset() {
        inputFramesProcessed = 0L
        mixingMatrix = null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val matrix = requireNotNull(mixingMatrix) { "Audio processor must be configured before input is queued" }
        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = bytesPerSample * inputAudioFormat.channelCount
        val frameCount = inputBuffer.remaining() / frameSize
        if (frameCount <= 0) return

        val output = replaceOutputBuffer(frameCount * outputChannelCount * 2).order(ByteOrder.nativeOrder())
        val sourceSamples = FloatArray(inputAudioFormat.channelCount)
        repeat(frameCount) {
            val sourceTimeUs = inputFramesProcessed * 1_000_000L / inputAudioFormat.sampleRate
            val timelineTimeUs = (sourceTimeUs.toDouble() / speed).toLong().coerceIn(0L, timelineDurationUs)
            val factor = gainFactor(timelineTimeUs)

            for (inputChannel in sourceSamples.indices) {
                sourceSamples[inputChannel] = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    inputBuffer.float
                } else {
                    inputBuffer.short.toFloat() / 32768f
                }
            }

            for (outputChannel in 0 until outputChannelCount) {
                var mixed = 0f
                for (inputChannel in sourceSamples.indices) {
                    mixed += sourceSamples[inputChannel] * matrix.getMixingCoefficient(inputChannel, outputChannel)
                }
                output.putShort(floatToPcm16(mixed * factor))
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
        val fadeIn = if (fadeInUs <= 0) 1f else {
            (timeUs.toDouble() / fadeInUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
        val fadeOutStart = (timelineDurationUs - fadeOutUs).coerceAtLeast(0L)
        val fadeOut = if (fadeOutUs <= 0 || timeUs <= fadeOutStart) 1f else {
            ((timelineDurationUs - timeUs).toDouble() / fadeOutUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
        return base * fadeIn * fadeOut
    }

    private fun floatToPcm16(value: Float): Short {
        val clipped = value.coerceIn(-1f, 1f)
        return if (clipped <= -1f) {
            Short.MIN_VALUE
        } else {
            (clipped * Short.MAX_VALUE.toFloat()).roundToInt().toShort()
        }
    }
}
