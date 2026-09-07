@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.render

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.effects.VisualEditsRepository
import com.videoflow.app.domain.ai.*
import com.videoflow.app.domain.editor.*
import com.videoflow.app.domain.export.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resumable single-source AI export. Video segments are encoded once; source AAC is copied once
 * during assembly, avoiding AAC encoder priming at every boundary. Complex compositions keep the
 * existing full composition path until their segment boundary semantics have equivalent coverage.
 */
@Singleton
class SegmentedAiRenderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val delegate: Media3RenderEngine,
    private val ai: AiWatermarkRepository
) : RenderEngine {
    var checkpointIntervalUs: Long = 60_000_000L
        set(value) { require(value > 0L); field = value }
    @Volatile var completedSegmentCount: Long = 0L
        private set
    @Volatile var resumedSegmentCount: Long = 0L
        private set
    private val mutex = ExportCacheLease.mutex
    private val cancelled = AtomicBoolean(false)

    override suspend fun prepare(plan: FinalRenderPlan, destination: OutputDestination, settings: ResolvedExportSettings): RenderPreparationResult {
        val prepared = delegate.prepare(plan,destination,settings)
        if (prepared.ready && eligible(plan,settings)) {
            val schedule = AiLongJobPlan(plan.durationUs,checkpointIntervalUs)
            val clip = plan.editorPlan.clips.first { it.enabled }
            val source = plan.originalSources.getValue(clip.assetId)
            val framePixels = Math.multiplyExact((source.width ?: settings.size.width).toLong(),(source.height ?: settings.size.height).toLong())
            val effectCount = ai.load(plan.editorPlan.projectId).count { it.enabled && it.clipId == clip.id }.toLong()
            val temporalBytes = Math.multiplyExact(Math.multiplyExact(framePixels,4L),effectCount)
            val required = Math.addExact(schedule.storageBytes(settings.videoBitrate.toLong()),Math.multiplyExact(schedule.count,temporalBytes))
            if (context.filesDir.usableSpace < required) return RenderPreparationResult(null,prepared.warnings,
                listOf(ExportProblem(ExportFailureCode.STORAGE_FULL,"Checkpointed AI export needs $required bytes of temporary storage.")))
        }
        return prepared
    }

    private suspend fun eligible(plan: FinalRenderPlan, settings: ResolvedExportSettings): Boolean {
        if (plan.durationUs < checkpointIntervalUs) return false
        val clips = plan.editorPlan.clips.filter { it.enabled }
        val clip = clips.singleOrNull() ?: return false
        val source = plan.originalSources.getValue(clip.assetId)
        if (clip.timelineStartUs != 0L || clip.speed != 1.0 || clip.gainDb != 0f || clip.fadeInUs != 0L || clip.fadeOutUs != 0L) return false
        if (plan.editorPlan.keyframes.isNotEmpty() || plan.durationUs != clip.timelineDurationUs) return false
        val track = plan.editorPlan.tracks.firstOrNull { it.id == clip.trackId } ?: return false
        if (track.type != TrackType.VIDEO || !track.visible || track.muted || plan.editorPlan.tracks.any { it.solo } || track.gainDb != 0f) return false
        if (source.audioCodecMime != null && (source.audioCodecMime != MediaFormat.MIMETYPE_AUDIO_AAC || source.audioChannelCount != settings.audioChannels || source.audioSampleRate != settings.audioSampleRate)) return false
        return ai.load(plan.editorPlan.projectId).any { it.enabled && it.clipId == clip.id }
    }

    override suspend fun render(preparation: RenderPreparation, listener: RenderProgressListener): Result<RenderExecutionResult> = mutex.withLock {
        ExportDestinationSafety.problem(context,preparation.plan,preparation.destination.uri)?.let { message ->
            return@withLock Result.failure(RenderPipelineException(ExportFailureCode.DESTINATION_IO,message))
        }
        cancelled.set(false)
        completedSegmentCount=0L; resumedSegmentCount=0L
        if (!eligible(preparation.plan,preparation.settings)) return@withLock delegate.render(preparation,listener)
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val plan = preparation.plan
            val clip = plan.editorPlan.clips.first { it.enabled }
            val effects = ai.load(plan.editorPlan.projectId)
            val visual = ai.visualEdits.load(plan.editorPlan.projectId)
            val original = plan.originalSources.getValue(clip.assetId)
            val sourceFingerprints=linkedMapOf<String,String>()
            for((assetId,source) in plan.originalSources.toSortedMap()) {
                val fresh=com.videoflow.app.data.media.UriFingerprintService(context).fingerprint(Uri.parse(source.sourceUri),source.sizeBytes,
                    source.durationUs,source.width,source.height)
                val hash=requireNotNull(fresh.sha256) { "Original media could not be fingerprinted safely." }
                if(source.fingerprintSha256?.matches(Regex("[0-9a-fA-F]{64}"))==true)
                    require(source.fingerprintSha256==hash) { "Original media changed; reconnect and review the source before resuming." }
                sourceFingerprints[assetId]=hash
            }
            val signature = AiCheckpointIdentity.key(plan,preparation.settings,effects,visual,sourceFingerprints.toString(),checkpointIntervalUs,AiModelCatalog.FINAL_512.sha256)
            val directory = File(context.filesDir,"ai-jobs/$signature")
            directory.mkdirs()
            val checkpoint = AtomicFile(File(directory,"checkpoint.json"))
            val schedule = AiLongJobPlan(plan.durationUs,checkpointIntervalUs)
            val temporal = com.videoflow.app.ai.watermark.AiTemporalCheckpoint()
            var sampledPeakPssKb = 0L
            var completedUs = 0L
            fun save(state: AiLongJobState) {
                sampledPeakPssKb = maxOf(sampledPeakPssKb,android.os.Debug.getPss())
                val row = JSONObject().put("version",1).put("signature",signature).put("project",plan.editorPlan.projectId)
                    .put("sourceFingerprints",JSONObject(sourceFingerprints as Map<*,*>))
                    .put("modelSha256",AiModelCatalog.FINAL_512.sha256).put("destination",preparation.destination.uri.toString())
                    .put("sampledPeakPssKb",sampledPeakPssKb).put("tileSize",512).put("queueLimit",2).put("workers",1)
                    .put("durationUs",plan.durationUs).put("completedUs",completedUs).put("state",state.name)
                val out = checkpoint.startWrite()
                try { out.write(row.toString().toByteArray()); checkpoint.finishWrite(out) }
                catch (e: Throwable) { checkpoint.failWrite(out); throw e }
            }
            try {
                save(AiLongJobState.PREPARING)
                var reuseChain=true
                for (segment in schedule.segments()) {
                    currentCoroutineContext().ensureActive(); checkCancelled()
                    val file = File(directory,"segment-${segment.index}.mp4")
                    val digest = File(directory,"segment-${segment.index}.sha256")
                    val temporalFile = File(directory,"segment-${segment.index}.temporal")
                    val temporalHash = File(directory,"segment-${segment.index}.temporal.sha256")
                    val valid = reuseChain && file.isFile && digest.isFile && digest.readText() == hashFile(file) &&
                        temporalFile.isFile && temporalHash.isFile && temporalHash.readText() == hashFile(temporalFile)
                    val restored=valid && runCatching { temporal.read(temporalFile); true }.getOrDefault(false)
                    if (restored) {
                        resumedSegmentCount++
                    } else {
                        reuseChain=false
                        temporalFile.delete(); temporalHash.delete()
                        file.delete(); digest.delete()
                        val shortClip = clip.copy(timelineStartUs=0L,sourceStartUs=clip.sourceStartUs+segment.startUs,sourceEndUs=clip.sourceStartUs+segment.endUs)
                        val shortPlan = plan.copy(editorPlan=plan.editorPlan.copy(clips=listOf(shortClip),
                            textOverlays=plan.editorPlan.textOverlays.filter { it.timelineStartUs<segment.endUs && it.timelineEndUs>segment.startUs }.map {
                                it.copy(timelineStartUs=maxOf(it.timelineStartUs,segment.startUs)-segment.startUs,
                                    timelineEndUs=minOf(it.timelineEndUs,segment.endUs)-segment.startUs)
                            },
                            imageOverlays=plan.editorPlan.imageOverlays.filter { it.timelineStartUs<segment.endUs && it.timelineEndUs>segment.startUs }.map {
                                it.copy(timelineStartUs=maxOf(it.timelineStartUs,segment.startUs)-segment.startUs,
                                    timelineEndUs=minOf(it.timelineEndUs,segment.endUs)-segment.startUs)
                            }),
                            originalSources=plan.originalSources.mapValues { (_, source) -> source.copy(audioCodecMime=null) },durationUs=segment.durationUs)
                        val shortAi = effects.filter { it.clipId == clip.id && it.enabled && it.clipLocalStartUs < segment.endUs && it.clipLocalEndUs > segment.startUs }.map { effect ->
                            val anchors = (listOf(segment.startUs,segment.endUs-1L)+effect.motionAnchors.map { it.clipLocalTimeUs }.filter { it in segment.startUs until segment.endUs }).distinct().sorted().map { time ->
                                val roi = effect.roiAt(time)
                                RoiMotionAnchor(time-segment.startUs,(roi.left+roi.right)/2f,(roi.top+roi.bottom)/2f,1f,roi.width,roi.height)
                            }
                            effect.copy(clipLocalStartUs=(effect.clipLocalStartUs-segment.startUs).coerceAtLeast(0L),
                                clipLocalEndUs=minOf(effect.clipLocalEndUs,segment.endUs)-segment.startUs,motionAnchors=anchors)
                        }
                        temporal.retainEffects(shortAi.map { it.id }.toSet())
                        val uri = FileProvider.getUriForFile(context,"${context.packageName}.derived",file)
                        save(AiLongJobState.PROCESSING)
                        val part = preparation.copy(plan=shortPlan,destination=OutputDestination(uri,file.name),
                            aiEffectsOverride=shortAi,visualEditsOverride=visual,visualTimeOffsetUs=segment.startUs,aiTemporalCheckpoint=temporal)
                        delegate.render(part,RenderProgressListener { p -> listener.onProgress(((segment.startUs.toDouble()+p*segment.durationUs)/plan.durationUs* .90).toFloat()) }).getOrThrow()
                        checkCancelled()
                        temporal.write(temporalFile)
                        temporalHash.writeText(hashFile(temporalFile))
                        digest.writeText(hashFile(file))
                    }
                    completedUs=segment.endUs
                    completedSegmentCount=segment.index+1L
                    save(AiLongJobState.PROCESSING)
                    listener.onProgress(schedule.progress(completedUs)*.90f)
                }
                save(AiLongJobState.FINALIZING)
                assemble(directory,schedule,clip,plan,preparation.destination.uri)
                save(AiLongJobState.VALIDATING)
                listener.onProgress(.97f)
                val output=OutputValidator(context.contentResolver).validateUri(preparation.destination.uri,preparation.settings,plan.durationUs,
                    plan.originalSources.getValue(clip.assetId).audioCodecMime != null,false,null)
                require(output.passed) { "Checkpointed output validation failed: ${output.problems.joinToString()}" }
                save(AiLongJobState.COMPLETED)
                directory.deleteRecursively()
                listener.onProgress(1f)
                Result.success(RenderExecutionResult(preparation.destination.uri,output.fileSizeBytes,System.currentTimeMillis()-started,null,null,output))
            } catch (failure: Throwable) {
                runCatching { save(if (cancelled.get() || failure is CancellationException) AiLongJobState.CANCELLED else AiLongJobState.FAILED) }
                    .onFailure { failure.addSuppressed(it) }
                // Completed validated segments remain resumable. A partial final destination is never successful.
                runCatching { context.contentResolver.openFileDescriptor(preparation.destination.uri,"rwt")?.close() }
                Result.failure(if (cancelled.get() || failure is CancellationException) RenderPipelineException(ExportFailureCode.CANCELLED,"AI export cancelled; completed checkpoints retained.",failure) else failure)
            }
        }
    }

    private suspend fun assemble(directory: File, schedule: AiLongJobPlan, clip: TimelineClip, plan: FinalRenderPlan, destination: Uri) {
        val first = MediaExtractor()
        val videoFormat = try { first.setDataSource(File(directory,"segment-0.mp4").path); first.getTrackFormat((0 until first.trackCount).first { first.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true }) } finally { first.release() }
        val source=plan.originalSources.getValue(clip.assetId)
        val audio = MediaExtractor()
        val hasAudio = source.audioCodecMime != null
        var audioTrack = -1
        try {
            if (hasAudio) {
                audio.setDataSource(context,Uri.parse(source.sourceUri),null)
                audioTrack=(0 until audio.trackCount).first { audio.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }
            }
            context.contentResolver.openFileDescriptor(destination,"rwt")?.use { descriptor ->
                val muxer=MediaMuxer(descriptor.fileDescriptor,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                try {
                    val outputVideo=muxer.addTrack(videoFormat)
                    val outputAudio=if(hasAudio) muxer.addTrack(audio.getTrackFormat(audioTrack)) else -1
                    muxer.start()
                    val buffer=ByteBuffer.allocateDirect(16*1024*1024)
                    val info=MediaCodec.BufferInfo()
                    var lastVideo=-1L
                    for (segment in schedule.segments()) {
                        checkCancelled(); currentCoroutineContext().ensureActive()
                        val extractor=MediaExtractor()
                        try {
                            extractor.setDataSource(File(directory,"segment-${segment.index}.mp4").path)
                            val track=(0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true }
                            val format=extractor.getTrackFormat(track)
                            require(format.getInteger(MediaFormat.KEY_WIDTH)==videoFormat.getInteger(MediaFormat.KEY_WIDTH) && format.getInteger(MediaFormat.KEY_HEIGHT)==videoFormat.getInteger(MediaFormat.KEY_HEIGHT))
                            for (key in listOf("csd-0","csd-1","csd-2")) require(format.getByteBuffer(key)==videoFormat.getByteBuffer(key)) { "Segment codec configuration changed; assembly refused." }
                            extractor.selectTrack(track)
                            while(extractor.sampleTime>=0 && extractor.sampleTime<segment.durationUs) {
                                checkCancelled(); currentCoroutineContext().ensureActive()
                                val time=segment.startUs+extractor.sampleTime
                                require(time>lastVideo) { "Segment timestamps are not strictly increasing." }
                                val maximumGap=(2_000_000.0/plan.editorPlan.frameRate.fps).toLong()+1L
                                require(lastVideo<0L || time-lastVideo<=maximumGap) { "A video section is missing at a segment boundary." }
                                require(android.os.Build.VERSION.SDK_INT < 28 || extractor.sampleSize<=buffer.capacity()) { "Encoded frame exceeds bounded assembly buffer." }
                                buffer.clear(); val size=extractor.readSampleData(buffer,0)
                                if(size<0) break
                                info.set(0,size,time,if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                                muxer.writeSampleData(outputVideo,buffer,info); lastVideo=time; extractor.advance()
                            }
                        } finally { extractor.release() }
                    }
                    if(hasAudio) {
                        audio.selectTrack(audioTrack); audio.seekTo(clip.sourceStartUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        while(audio.sampleTrackIndex>=0 && audio.sampleTime<clip.sourceEndUs) {
                            checkCancelled(); currentCoroutineContext().ensureActive()
                            if(audio.sampleTime>=clip.sourceStartUs) {
                                require(android.os.Build.VERSION.SDK_INT < 28 || audio.sampleSize<=buffer.capacity())
                                require(audio.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Encrypted audio cannot be assembled." }
                                buffer.clear(); val size=audio.readSampleData(buffer,0); if(size<0) break
                                info.set(0,size,audio.sampleTime-clip.sourceStartUs,MediaCodec.BUFFER_FLAG_KEY_FRAME)
                                muxer.writeSampleData(outputAudio,buffer,info)
                            }
                            audio.advance()
                        }
                    }
                    muxer.stop()
                } finally { muxer.release() }
            } ?: error("Destination permission is unavailable.")
        } finally { audio.release() }
    }
    private fun checkCancelled() { if(cancelled.get()) throw CancellationException("AI export cancelled") }
    override suspend fun cancel() { cancelled.set(true); delegate.cancel() }
    private fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun hashFile(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(64*1024)
        file.inputStream().use { input -> while(true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
