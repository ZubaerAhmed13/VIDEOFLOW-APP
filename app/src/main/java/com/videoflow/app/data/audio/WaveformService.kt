package com.videoflow.app.data.audio

import android.content.ContentResolver
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.videoflow.app.data.db.VideoFlowDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max


data class WaveformPeaks(
    val assetId: String,
    val durationUs: Long,
    val peaks: FloatArray
)

@Singleton
class WaveformService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: VideoFlowDatabase
) {
    private val resolver: ContentResolver = context.contentResolver
    private val decoderSlots = Semaphore(1)

    suspend fun loadOrGenerate(assetId: String, requestedBins: Int = 1024): WaveformPeaks = decoderSlots.withPermit {
        withContext(Dispatchers.IO) {
            val bins = requestedBins.coerceIn(64, 4096)
            val asset = db.mediaAssetDao().get(assetId) ?: error("Media asset not found")
            val durationUs = asset.durationUs?.takeIf { it > 0 } ?: error("Audio duration unavailable")
            val cache = cacheFile(assetId, asset.fingerprintSha256, bins)
            readCache(cache, assetId, durationUs)?.let { return@withContext it }
            val generated = decodePeaks(assetId, Uri.parse(asset.sourceUri), durationUs, bins)
            writeCache(cache, generated)
            generated
        }
    }

    suspend fun deleteCache(assetId: String) = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "waveforms")
        directory.listFiles()?.filter { it.name.startsWith("$assetId-") }?.forEach { it.delete() }
    }

    private suspend fun decodePeaks(assetId: String, uri: Uri, durationUs: Long, bins: Int): WaveformPeaks {
        val extractor = MediaExtractor()
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: error("Unable to open audio source")
        descriptor.use { extractor.setDataSource(it.fileDescriptor) }
        var trackIndex = -1
        var inputFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                trackIndex = index
                inputFormat = format
                break
            }
        }
        if (trackIndex < 0 || inputFormat == null) {
            extractor.release()
            error("No audio track available for waveform")
        }
        extractor.selectTrack(trackIndex)
        val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val peaks = FloatArray(bins)
        val info = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        try {
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: error("Decoder input buffer unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val peak = when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val floats = output.slice().order(output.order()).asFloatBuffer()
                                    var p = 0f
                                    while (floats.hasRemaining()) p = max(p, abs(floats.get()).coerceAtMost(1f))
                                    p
                                }
                                else -> {
                                    val shorts = output.slice().order(output.order()).asShortBuffer()
                                    var p = 0f
                                    while (shorts.hasRemaining()) {
                                        val value = abs(shorts.get().toInt()) / 32768f
                                        if (value > p) p = value
                                    }
                                    p.coerceIn(0f, 1f)
                                }
                            }
                            val bin = ((info.presentationTimeUs.toDouble() / durationUs.toDouble()) * bins)
                                .toInt().coerceIn(0, bins - 1)
                            if (peak > peaks[bin]) peaks[bin] = peak
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        return WaveformPeaks(assetId, durationUs, peaks)
    }

    private fun cacheFile(assetId: String, fingerprint: String?, bins: Int): File {
        val directory = File(context.cacheDir, "waveforms").apply { mkdirs() }
        return File(directory, "$assetId-${fingerprint?.take(12) ?: "weak"}-$bins.vfwp")
    }

    private fun writeCache(file: File, waveform: WaveformPeaks) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeLong(waveform.durationUs)
            out.writeInt(waveform.peaks.size)
            waveform.peaks.forEach(out::writeFloat)
        }
    }

    private fun readCache(file: File, assetId: String, durationUs: Long): WaveformPeaks? {
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return@runCatching null
                val cachedDuration = input.readLong()
                val count = input.readInt()
                if (cachedDuration != durationUs || count !in 64..4096) return@runCatching null
                WaveformPeaks(assetId, durationUs, FloatArray(count) { input.readFloat() })
            }
        }.getOrNull()
    }

    companion object {
        private const val MAGIC = 0x56465750
        private const val VERSION = 1
    }
}
