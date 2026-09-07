@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import androidx.room.withTransaction
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.history.ClipHistoryEntry
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.project.AddMediaResult
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.editor.TrackType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Reference-based input, bounded encoded packet copy with a real local AAC fallback. */
@Singleton
class AudioExtractionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: VideoFlowDatabase,
    private val editor: EditorRepository,
    private val projects: ProjectRepository,
    private val history: EditHistoryService
) {
    private val mutex = Mutex()
    suspend fun extract(projectId: String, clipId: String, muteOriginal: Boolean, progress: (Float) -> Unit): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(projectId.matches(Regex("[A-Za-z0-9_-]+")))
            val before = editor.load(projectId).timeline.clips.first { it.id == clipId }
            val originalFrames = db.editorDao().getKeyframes(listOf(clipId))
            val source = db.mediaAssetDao().get(before.assetId) ?: error("Original source is missing")
            require(source.sourceStatus == "AVAILABLE") { "Original source must be available before audio extraction." }
            require(source.audioTrackCount > 0) { "This video has no audio track." }
            val root = File(context.filesDir, "extracted-audio/$projectId")
            check(root.isDirectory || root.mkdirs())
            // Reserve enough for the full encoded source as a conservative track-copy ceiling.
            val estimate = source.sizeBytes ?: Math.multiplyExact((source.durationUs ?: before.sourceEndUs) / 1_000_000L + 1, 192_000L)
            require(root.usableSpace > Math.addExact(estimate, 32L * 1024 * 1024)) { "Not enough free space for extracted audio." }
            require(source.assetId.matches(Regex("[A-Za-z0-9_-]+")))
            val identity = source.fingerprintSha256?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) } ?: UUID.randomUUID().toString()
            val target = File(root, "${source.assetId}-$identity.m4a")
            val created = !target.exists()
            var registered = false
            try {
                progress(0f)
                if (created) copyOrTranscode(Uri.parse(source.sourceUri), target, source.durationUs ?: before.sourceEndUs, progress)
                currentCoroutineContext().ensureActive()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.derived", target)
                withContext(NonCancellable) {
                val assetId = db.withTransaction {
                    val result = projects.addMedia(projectId, uri)
                    val id = when (result) {
                        is AddMediaResult.Added -> result.asset.id
                        is AddMediaResult.DuplicateCandidate -> projects.confirmAddDuplicate(result.candidate).id
                    }
                    val asset = requireNotNull(db.mediaAssetDao().get(id))
                    require(asset.audioTrackCount == 1 && asset.videoTrackCount == 0) { "Extracted output did not validate as audio." }
                    require(asset.audioChannelCount == source.audioChannelCount && asset.audioSampleRate == source.audioSampleRate) {
                        "This device cannot preserve the source audio channels/sample rate in the editing format."
                    }
                    db.mediaAssetDao().update(asset.copy(displayName = "Extracted from ${source.displayName}", permissionPersisted = true))
                    val track = editor.createTrack(projectId, TrackType.AUDIO, "Extracted audio")
                    require(editor.load(projectId).timeline.clips.first { it.id == clipId } == before) { "The video was edited during extraction. Open Extract audio again with the current clip." }
                    val originalEntity = db.editorDao().getClips(projectId).first { it.id == clipId }
                    val audio = originalEntity.copy(
                        id = UUID.randomUUID().toString(), trackId = track.id, assetId = id,
                        // Preserve trim, speed, gain/fades and timing; the audio track is independently editable.
                        sourceEndUs = minOf(before.sourceEndUs, asset.durationUs ?: before.sourceEndUs)
                    )
                    require(audio.sourceEndUs > audio.sourceStartUs) { "No audio exists in this selected clip range." }
                    db.editorDao().putClip(audio)
                    originalFrames.filter { it.property == "AUDIO_GAIN" }.forEach { frame ->
                        db.editorDao().putKeyframe(frame.copy(id=UUID.randomUUID().toString(),ownerId=audio.id))
                    }
                    if (muteOriginal) {
                        require(!db.editorDao().getTracks(projectId).first { it.id == originalEntity.trackId }.locked) { "Unlock the video track before muting it." }
                        db.editorDao().putClip(originalEntity.copy(gainDb = -120f))
                        originalFrames.filter { it.property == "AUDIO_GAIN" }.forEach { frame -> db.editorDao().putKeyframe(frame.copy(value=-120f)) }
                    }
                    id
                }
                registered = true
                val after = editor.load(projectId).timeline.clips.filter { it.id == clipId || it.assetId == assetId }
                val frames = editor.load(projectId).timeline.keyframes.filter { frame -> after.any { it.id == frame.ownerId } }
                val beforeFrames = originalFrames.map { row -> com.videoflow.app.domain.editor.Keyframe(row.id,row.ownerId,
                    com.videoflow.app.domain.editor.KeyframeOwnerType.valueOf(row.ownerType),com.videoflow.app.domain.editor.KeyframeProperty.valueOf(row.property),
                    row.timeUs,row.value,com.videoflow.app.domain.editor.KeyframeInterpolation.valueOf(row.interpolation)) }
                history.record(ClipHistoryEntry(projectId, "Extract audio", listOf(before), after, beforeFrames, frames))
                progress(1f)
                assetId
                }
            } finally {
                if (!registered && created) target.delete()
            }
        }
    }

    private suspend fun copyOrTranscode(uri: Uri, target: File, durationUs: Long, progress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { extractor.setDataSource(it.fileDescriptor) }
                ?: error("Original source permission is unavailable. Locate Original to reconnect it.")
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This video has no audio track.")
            val format = extractor.getTrackFormat(track)
            if (format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) {
                transcode(uri, target); return
            }
            extractor.selectTrack(track)
            val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outputTrack = muxer.addTrack(format)
                muxer.start()
                val buffer = ByteBuffer.allocateDirect(1024 * 1024)
                val info = MediaCodec.BufferInfo()
                var samples = 0L
                while (extractor.sampleTrackIndex >= 0) {
                    currentCoroutineContext().ensureActive()
                    // AAC encoder priming may have a negative PTS. Only sampleTrackIndex==-1 is EOF.
                    if (extractor.sampleTime < 0L) { extractor.advance(); continue }
                    require(android.os.Build.VERSION.SDK_INT < 28 || extractor.sampleSize <= buffer.capacity().toLong()) { "Encoded audio packet exceeds the safe buffer size." }
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Encrypted audio cannot be extracted." }
                    info.set(0, size, extractor.sampleTime, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    muxer.writeSampleData(outputTrack, buffer, info)
                    if (++samples % 128L == 0L) progress((extractor.sampleTime.toDouble() / durationUs.coerceAtLeast(1)).toFloat().coerceIn(0f, .95f))
                    extractor.advance()
                }
                require(samples > 0) { "The audio track contains no readable samples." }
                muxer.stop()
            } finally { muxer.release() }
        } finally { extractor.release() }
    }

    private suspend fun transcode(uri: Uri, target: File) = withContext(Dispatchers.Main.immediate) {
        val completion = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { completion.complete(Unit) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { completion.completeExceptionally(exportException) }
            }).build()
        try {
            transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).build(), target.path)
            completion.await()
        } finally { transformer.cancel() }
    }
}
