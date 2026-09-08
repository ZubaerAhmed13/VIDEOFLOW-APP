@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.videoflow.app.ai.watermark

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.videoflow.app.BuildConfig
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.editor.EditorProject
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.ai.AiModelCatalog
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
import com.videoflow.app.domain.editor.ProxyMedia
import com.videoflow.app.domain.editor.ProxyStatus
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.VideoFlowProject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val AI_PREVIEW_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS = 120_000L

/** A bounded, editor-quality AI segment. Times are clip-local timeline times. */
data class AiPreviewReadySegment(
    val projectId: String,
    val clipId: String,
    val clipLocalStartUs: Long,
    val clipLocalEndUs: Long,
    val path: String,
    val stateKey: String
) {
    init {
        require(clipLocalStartUs >= 0L && clipLocalEndUs > clipLocalStartUs)
        require(path.isNotBlank() && stateKey.isNotBlank())
    }
}

enum class AiMovingPreviewLength(val label: String, val durationUs: Long?) {
    THREE_SECONDS("3 sec", 3_000_000L),
    FIVE_SECONDS("5 sec", 5_000_000L),
    TEN_SECONDS("10 sec", 10_000_000L),
    SELECTED_RANGE("Selected Range", null)
}

data class AiMovingPreviewState(
    val preparing: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val path: String? = null,
    val clipLocalStartUs: Long = 0L,
    val clipLocalEndUs: Long = 0L,
    val provider: String? = null,
    val error: String? = null
)

/** Small process-local invalidation signal; disk identity remains the source of truth after restart. */
object AiPreviewCacheBus {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun changed() {
        _revision.value = _revision.value + 1L
    }
}

/**
 * Disk cache contract shared by the renderer and normal editor playback.
 * A segment is accepted only when its state key matches the CURRENT source/proxy/clip/effect state.
 */
class AiPreviewCacheStore(private val context: Context) {
    private val root = File(context.cacheDir, "ai-preview/ranges")

    suspend fun resolveReadySegments(
        project: VideoFlowProject,
        editor: EditorProject,
        clip: TimelineClip
    ): List<AiPreviewReadySegment> = withContext(Dispatchers.IO) {
        val asset = project.mediaAssets.firstOrNull { it.id == clip.assetId } ?: return@withContext emptyList()
        val effects = AiWatermarkRepository(context).effectsForClip(project.id, clip.id).filter { it.enabled }
        if (effects.isEmpty()) return@withContext emptyList()
        val proxy = currentProxy(editor, asset)
        val key = AiPreviewIdentity.stateKey(project, clip, asset, proxy, effects)
        val directory = clipDirectory(project.id, clip.id)
        if (!directory.isDirectory) return@withContext emptyList()
        directory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { meta -> readMeta(meta, key) }
            .sortedBy { it.clipLocalStartUs }
    }

    fun currentProxy(editor: EditorProject, asset: MediaAsset): ProxyMedia? =
        editor.proxies.firstOrNull {
            it.assetId == asset.id && it.status == ProxyStatus.READY &&
                (it.sourceFingerprint == null || asset.fingerprintSha256 == null || it.sourceFingerprint == asset.fingerprintSha256) &&
                File(it.path).isFile
        }

    fun clipDirectory(projectId: String, clipId: String): File =
        File(root, "${safe(projectId)}/${safe(clipId)}")

    fun expectedChunks(effects: List<AiWatermarkEffect>, clipDurationUs: Long): List<LongRange> {
        val ranges = effects.asSequence()
            .filter { it.enabled }
            .map {
                val start = it.clipLocalStartUs.coerceIn(0L, clipDurationUs)
                val endExclusive = it.clipLocalEndUs.coerceIn(0L, clipDurationUs)
                start until endExclusive
            }
            .filter { !it.isEmpty() }
            .sortedBy { it.first }
            .toList()
        if (ranges.isEmpty()) return emptyList()

        val merged = mutableListOf<LongRange>()
        var start = ranges.first().first
        var endExclusive = ranges.first().last + 1L
        for (range in ranges.drop(1)) {
            val nextEnd = range.last + 1L
            if (range.first <= endExclusive) {
                endExclusive = maxOf(endExclusive, nextEnd)
            } else {
                merged += start until endExclusive
                start = range.first
                endExclusive = nextEnd
            }
        }
        merged += start until endExclusive

        return buildList {
            merged.forEach { range ->
                var cursor = range.first
                val end = range.last + 1L
                while (cursor < end) {
                    val chunkEnd = minOf(end, cursor + MAX_SEGMENT_US)
                    add(cursor until chunkEnd)
                    cursor = chunkEnd
                }
            }
        }
    }

    fun segmentFile(projectId: String, clipId: String, stateKey: String, range: LongRange): File {
        val endExclusive = range.last + 1L
        return File(clipDirectory(projectId, clipId), "$stateKey-${range.first}-$endExclusive.mp4")
    }

    fun metadataFile(segment: File): File = File(segment.parentFile, segment.nameWithoutExtension + ".json")

    fun writeReady(segment: AiPreviewReadySegment) {
        val file = File(segment.path)
        require(file.isFile && file.length() > 0L) { "AI preview segment is empty." }
        val meta = metadataFile(file)
        val temp = File(meta.parentFile, ".${meta.name}.tmp")
        meta.parentFile?.mkdirs()
        temp.writeText(
            JSONObject()
                .put("version", CACHE_VERSION)
                .put("projectId", segment.projectId)
                .put("clipId", segment.clipId)
                .put("stateKey", segment.stateKey)
                .put("startUs", segment.clipLocalStartUs)
                .put("endUs", segment.clipLocalEndUs)
                .put("file", file.name)
                .toString()
        )
        if (meta.exists() && !meta.delete()) error("Could not replace AI preview metadata.")
        if (!temp.renameTo(meta)) error("Could not finalize AI preview metadata.")
        AiPreviewCacheBus.changed()
    }

    fun removeStale(projectId: String, clipId: String, keepStateKey: String? = null) {
        val directory = clipDirectory(projectId, clipId)
        if (!directory.isDirectory) return
        directory.listFiles().orEmpty().forEach { file ->
            val keep = keepStateKey != null && file.name.startsWith(keepStateKey)
            if (!keep) file.delete()
        }
        if (directory.listFiles()?.isEmpty() == true) directory.delete()
        AiPreviewCacheBus.changed()
    }

    fun removeProject(projectId: String) {
        File(root, safe(projectId)).deleteRecursively()
        AiPreviewCacheBus.changed()
    }

    private fun readMeta(meta: File, expectedKey: String): AiPreviewReadySegment? = runCatching {
        val json = JSONObject(meta.readText())
        if (json.optInt("version") != CACHE_VERSION || json.optString("stateKey") != expectedKey) return@runCatching null
        val media = File(meta.parentFile, json.getString("file"))
        if (!media.isFile || media.length() <= 0L) return@runCatching null
        AiPreviewReadySegment(
            projectId = json.getString("projectId"),
            clipId = json.getString("clipId"),
            clipLocalStartUs = json.getLong("startUs"),
            clipLocalEndUs = json.getLong("endUs"),
            path = media.absolutePath,
            stateKey = expectedKey
        )
    }.getOrNull()

    private fun safe(value: String): String {
        require(value.isNotBlank() && value.none { it == '/' || it == '\\' })
        return value
    }

    companion object {
        const val CACHE_VERSION = 2
        const val PREVIEW_TARGET_HEIGHT = 720
        const val MAX_SEGMENT_US = 10_000_000L
    }
}

object AiPreviewIdentity {
    fun stateKey(
        project: VideoFlowProject,
        clip: TimelineClip,
        asset: MediaAsset,
        proxy: ProxyMedia?,
        effects: List<AiWatermarkEffect>
    ): String {
        val canonical = buildString {
            append("vf-ai-preview-v${AiPreviewCacheStore.CACHE_VERSION}|app=").append(BuildConfig.VERSION_CODE)
            append("|project=").append(project.id)
            append("|asset=").append(asset.id)
            append("|fingerprint=").append(asset.fingerprintSha256 ?: "none")
            append("|sourceSize=").append(asset.sizeBytes ?: -1L)
            append("|sourceDim=").append(asset.width ?: -1).append('x').append(asset.height ?: -1)
            append("|rotation=").append(asset.rotationDegrees ?: 0)
            append("|clip=").append(clip.id)
            append("|sourceRange=").append(clip.sourceStartUs).append(':').append(clip.sourceEndUs)
            append("|speed=").append(clip.speed)
            append("|proxy=")
            if (proxy == null) append("none") else {
                append(proxy.id).append(':').append(proxy.sourceFingerprint ?: "none")
                    .append(':').append(proxy.width).append('x').append(proxy.height)
                    .append(':').append(proxy.quality.name).append(':').append(proxy.sizeBytes ?: -1L)
            }
            append("|targetH=").append(AiPreviewCacheStore.PREVIEW_TARGET_HEIGHT)
            effects.sortedWith(compareBy<AiWatermarkEffect> { it.clipLocalStartUs }.thenBy { it.id }).forEach { effect ->
                val model = AiModelCatalog.byId(effect.modelId)
                append("|fx=").append(effect.id)
                    .append(':').append(effect.clipLocalStartUs).append(':').append(effect.clipLocalEndUs)
                    .append(':').append(effect.roi.left).append(',').append(effect.roi.top).append(',').append(effect.roi.right).append(',').append(effect.roi.bottom)
                    .append(':').append(effect.contextPaddingPx).append(':').append(effect.featherPx)
                    .append(':').append(effect.temporalStability).append(':').append(effect.enabled)
                    .append(':').append(effect.modelId).append(':').append(model?.sha256 ?: "unknown")
                effect.motionAnchors.sortedBy { it.clipLocalTimeUs }.forEach { anchor ->
                    append("@a=").append(anchor.clipLocalTimeUs).append(',').append(anchor.centerX).append(',').append(anchor.centerY)
                        .append(',').append(anchor.confidence).append(',').append(anchor.width ?: -1f).append(',').append(anchor.height ?: -1f)
                        .append(',').append(anchor.manual)
                }
            }
        }
        return sha256(canonical)
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/**
 * Generates ONLY bounded AI-active editor segments. It never becomes final-export authority.
 * Final export continues through FinalRenderPlan/Media3RenderEngine from the original source.
 */
@Singleton
class AiProcessedPreviewManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editorRepository: EditorRepository,
    private val projectRepository: ProjectRepository,
    private val aiRepository: AiWatermarkRepository,
    private val modelPackManager: AiModelPackManager
) {
    private val mutex = Mutex()
    private val cache = AiPreviewCacheStore(context)
    private val _state = MutableStateFlow(AiMovingPreviewState())
    val state: StateFlow<AiMovingPreviewState> = _state.asStateFlow()

    @Volatile private var activeTransformer: Transformer? = null
    @Volatile private var activeRuntime: SharedLamaRenderRuntime? = null

    suspend fun prepareClip(
        projectId: String,
        clipId: String,
        onProgress: (Float) -> Unit = {}
    ): List<AiPreviewReadySegment> = mutex.withLock {
        val inputs = loadInputs(projectId, clipId)
        val enabled = inputs.effects.filter { it.enabled }
        if (enabled.isEmpty()) {
            cache.removeStale(projectId, clipId)
            _state.value = AiMovingPreviewState(message = "No active AI range")
            return@withLock emptyList()
        }
        val key = AiPreviewIdentity.stateKey(inputs.project, inputs.clip, inputs.asset, inputs.proxy, enabled)
        cache.removeStale(projectId, clipId, keepStateKey = key)
        val chunks = cache.expectedChunks(enabled, inputs.clip.timelineDurationUs)
        val existing = cache.resolveReadySegments(inputs.project, inputs.editor, inputs.clip).associateBy { it.clipLocalStartUs to it.clipLocalEndUs }
        if (chunks.all { range -> existing.containsKey(range.first to (range.last + 1L)) }) {
            val ready = chunks.mapNotNull { existing[it.first to (it.last + 1L)] }
            _state.value = AiMovingPreviewState(progress = 1f, message = "AI Preview Ready")
            return@withLock ready
        }

        _state.value = AiMovingPreviewState(preparing = true, progress = 0f, message = "Preparing AI Preview…")
        val runtime = SharedLamaRenderRuntime.create(modelPackManager, context)
        activeRuntime = runtime
        try {
            val completed = mutableListOf<AiPreviewReadySegment>()
            chunks.forEachIndexed { index, range ->
                currentCoroutineContext().ensureActive()
                val old = existing[range.first to (range.last + 1L)]
                if (old != null) {
                    completed += old
                } else {
                    val result = renderRange(inputs, enabled, range, key, runtime) { segmentProgress ->
                        val overall = (index + segmentProgress.coerceIn(0f, 1f)) / chunks.size.toFloat()
                        _state.value = _state.value.copy(preparing = true, progress = overall, message = "Preparing AI Preview…")
                        onProgress(overall)
                    }
                    cache.writeReady(result)
                    completed += result
                }
            }
            _state.value = AiMovingPreviewState(
                preparing = false,
                progress = 1f,
                message = "AI Preview Ready",
                provider = runtime.provider
            )
            onProgress(1f)
            completed.sortedBy { it.clipLocalStartUs }
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(preparing = false, message = "AI preview preparation cancelled", error = null)
            throw cancelled
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                preparing = false,
                progress = 0f,
                message = "AI preview unavailable",
                error = t.message ?: t::class.java.simpleName
            )
            throw t
        } finally {
            activeTransformer = null
            activeRuntime = null
            runCatching { runtime.close() }
        }
    }

    suspend fun prepareDraftWindow(
        projectId: String,
        clipId: String,
        draftEffect: AiWatermarkEffect,
        centerLocalUs: Long,
        length: AiMovingPreviewLength,
        onProgress: (Float) -> Unit = {}
    ): AiPreviewReadySegment = mutex.withLock {
        val inputs = loadInputs(projectId, clipId)
        val selectedStart = draftEffect.clipLocalStartUs.coerceIn(0L, inputs.clip.timelineDurationUs - 1L)
        val selectedEnd = draftEffect.clipLocalEndUs.coerceIn(selectedStart + 1L, inputs.clip.timelineDurationUs)
        val duration = length.durationUs
        val (start, end) = if (duration == null) {
            selectedStart to selectedEnd
        } else {
            val half = duration / 2L
            val preferred = (centerLocalUs - half).coerceAtLeast(selectedStart)
            val boundedStart = minOf(preferred, maxOf(selectedStart, selectedEnd - duration))
            boundedStart to minOf(selectedEnd, boundedStart + duration)
        }
        val range = start until end
        val key = AiPreviewIdentity.stateKey(inputs.project, inputs.clip, inputs.asset, inputs.proxy, listOf(draftEffect)) + "-draft-${start}-${end}"
        val draftDir = File(context.cacheDir, "ai-preview/drafts/${safe(projectId)}/${safe(clipId)}").apply { mkdirs() }
        val output = File(draftDir, "$key.mp4")
        if (output.isFile && output.length() > 0L) {
            val ready = AiPreviewReadySegment(projectId, clipId, start, end, output.absolutePath, key)
            _state.value = AiMovingPreviewState(false, 1f, "AI Preview Ready", ready.path, start, end)
            return@withLock ready
        }

        _state.value = AiMovingPreviewState(true, 0f, "Preparing AI Preview…", null, start, end)
        val runtime = SharedLamaRenderRuntime.create(modelPackManager, context)
        activeRuntime = runtime
        try {
            val result = renderRange(inputs, listOf(draftEffect), range, key, runtime, outputOverride = output) { p ->
                _state.value = _state.value.copy(preparing = true, progress = p, message = "Preparing AI Preview…")
                onProgress(p)
            }
            _state.value = AiMovingPreviewState(false, 1f, "AI Preview Ready", result.path, start, end, runtime.provider)
            result
        } catch (cancelled: CancellationException) {
            output.delete()
            _state.value = AiMovingPreviewState(false, 0f, "AI preview preparation cancelled", null, start, end)
            throw cancelled
        } catch (t: Throwable) {
            output.delete()
            _state.value = AiMovingPreviewState(false, 0f, "AI preview unavailable", null, start, end, error = t.message ?: t::class.java.simpleName)
            throw t
        } finally {
            activeTransformer = null
            activeRuntime = null
            runCatching { runtime.close() }
        }
    }

    suspend fun cancel() {
        activeRuntime?.cancel()
        withContext(NonCancellable + Dispatchers.Main.immediate) { activeTransformer?.cancel() }
    }

    suspend fun invalidateProject(projectId: String) = withContext(Dispatchers.IO) {
        cache.removeProject(projectId)
    }

    private suspend fun renderRange(
        inputs: Inputs,
        effects: List<AiWatermarkEffect>,
        range: LongRange,
        stateKey: String,
        runtime: SharedLamaRenderRuntime,
        outputOverride: File? = null,
        onProgress: (Float) -> Unit
    ): AiPreviewReadySegment {
        val endExclusive = range.last + 1L
        require(endExclusive > range.first)
        val source = previewSource(inputs)
        val sourceRangeStartUs = inputs.clip.sourceStartUs + (range.first.toDouble() * inputs.clip.speed).roundToLong()
        val sourceRangeEndUs = inputs.clip.sourceStartUs + (endExclusive.toDouble() * inputs.clip.speed).roundToLong()
        require(sourceRangeEndUs > sourceRangeStartUs)
        val remapped = effects.filter { it.enabled && it.clipLocalStartUs < endExclusive && it.clipLocalEndUs > range.first }
            .map { remapToSourceTime(it, range.first, endExclusive, inputs.clip.speed) }

        val videoEffects = mutableListOf<Effect>()
        var effectWidth = source.width
        var effectHeight = source.height
        if (effectHeight > AiPreviewCacheStore.PREVIEW_TARGET_HEIGHT) {
            val targetHeight = AiPreviewCacheStore.PREVIEW_TARGET_HEIGHT
            val targetWidth = ((effectWidth.toDouble() * targetHeight / effectHeight).roundToLong().toInt()).coerceAtLeast(2).let { if (it % 2 == 0) it else it + 1 }
            videoEffects += Presentation.createForHeight(targetHeight)
            effectWidth = targetWidth
            effectHeight = targetHeight
        }
        remapped.sortedWith(compareBy<AiWatermarkEffect> { it.clipLocalStartUs }.thenBy { it.id }).forEach { effect ->
            videoEffects += OnnxWatermarkEffectFactory.createEffects(effect, effectWidth, effectHeight, runtime)
        }

        val media = MediaItem.Builder()
            .setUri(Uri.parse(source.uri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(sourceRangeStartUs)
                    .setEndPositionUs(sourceRangeEndUs)
                    .build()
            )
            .build()
        val edited = EditedMediaItem.Builder(media)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val output = outputOverride ?: cache.segmentFile(inputs.project.id, inputs.clip.id, stateKey, range)
        output.parentFile?.mkdirs()
        val partial = File(output.parentFile, ".${output.name}.partial")
        partial.delete()
        try {
            runTransform(edited, partial, onProgress)
            currentCoroutineContext().ensureActive()
            if (!partial.isFile || partial.length() <= 0L) error("AI preview renderer produced no media.")
            if (output.exists() && !output.delete()) error("Could not replace stale AI preview segment.")
            if (!partial.renameTo(output)) error("Could not finalize AI preview segment.")
            return AiPreviewReadySegment(inputs.project.id, inputs.clip.id, range.first, endExclusive, output.absolutePath, stateKey)
        } finally {
            partial.delete()
        }
    }

    private suspend fun runTransform(
        edited: EditedMediaItem,
        output: File,
        onProgress: (Float) -> Unit
    ): ExportResult = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val holder = ProgressHolder()
            lateinit var transformer: Transformer
            lateinit var poll: Runnable
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    handler.removeCallbacks(poll)
                    activeTransformer = null
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    handler.removeCallbacks(poll)
                    activeTransformer = null
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }
            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setMaxDelayBetweenMuxerSamplesMs(AI_PREVIEW_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
                .addListener(listener)
                .build()
            activeTransformer = transformer
            poll = object : Runnable {
                override fun run() {
                    val progressState = transformer.getProgress(holder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress.coerceIn(0, 100) / 100f)
                    if (continuation.isActive && progressState != Transformer.PROGRESS_STATE_NOT_STARTED) handler.postDelayed(this, 350L)
                }
            }
            continuation.invokeOnCancellation {
                handler.removeCallbacks(poll)
                transformer.cancel()
                activeTransformer = null
                output.delete()
            }
            transformer.start(edited, output.absolutePath)
            handler.post(poll)
        }
    }

    private suspend fun loadInputs(projectId: String, clipId: String): Inputs {
        val project = projectRepository.getProject(projectId) ?: error("Project is unavailable.")
        val editor = editorRepository.load(projectId)
        val clip = editor.timeline.clips.firstOrNull { it.id == clipId } ?: error("Video clip is unavailable.")
        val asset = project.mediaAssets.firstOrNull { it.id == clip.assetId } ?: error("Source media is unavailable.")
        val effects = aiRepository.effectsForClip(projectId, clipId)
        val proxy = cache.currentProxy(editor, asset)
        return Inputs(project, editor, clip, asset, proxy, effects)
    }

    private fun previewSource(inputs: Inputs): PreviewSource {
        val proxy = inputs.proxy
        if (proxy != null) {
            return PreviewSource(Uri.fromFile(File(proxy.path)).toString(), proxy.width, proxy.height)
        }
        val rotated = inputs.asset.rotationDegrees == 90 || inputs.asset.rotationDegrees == 270
        val width = (if (rotated) inputs.asset.height else inputs.asset.width)?.takeIf { it > 0 }
            ?: error("AI moving preview requires known video width.")
        val height = (if (rotated) inputs.asset.width else inputs.asset.height)?.takeIf { it > 0 }
            ?: error("AI moving preview requires known video height.")
        return PreviewSource(inputs.asset.sourceUri, width, height)
    }

    private fun remapToSourceTime(
        effect: AiWatermarkEffect,
        segmentStartLocalUs: Long,
        segmentEndLocalUs: Long,
        speed: Double
    ): AiWatermarkEffect {
        val activeStart = maxOf(effect.clipLocalStartUs, segmentStartLocalUs)
        val activeEnd = minOf(effect.clipLocalEndUs, segmentEndLocalUs)
        require(activeEnd > activeStart)
        fun sourceRelative(localUs: Long): Long = ((localUs - segmentStartLocalUs).toDouble() * speed).roundToLong().coerceAtLeast(0L)
        fun anchor(localUs: Long, roi: NormalizedRoi, manual: Boolean) = RoiMotionAnchor(
            clipLocalTimeUs = sourceRelative(localUs),
            centerX = (roi.left + roi.right) / 2f,
            centerY = (roi.top + roi.bottom) / 2f,
            confidence = 1f,
            width = roi.width,
            height = roi.height,
            manual = manual
        )
        val anchors = buildList {
            add(anchor(activeStart, effect.roiAt(activeStart), true))
            effect.motionAnchors.filter { it.clipLocalTimeUs > activeStart && it.clipLocalTimeUs < activeEnd }.forEach { original ->
                add(original.copy(clipLocalTimeUs = sourceRelative(original.clipLocalTimeUs)))
            }
            add(anchor(activeEnd, effect.roiAt(activeEnd), true))
        }.distinctBy { it.clipLocalTimeUs }.sortedBy { it.clipLocalTimeUs }
        val startSource = sourceRelative(activeStart)
        val endSource = sourceRelative(activeEnd).coerceAtLeast(startSource + 1L)
        return effect.copy(
            clipLocalStartUs = startSource,
            clipLocalEndUs = endSource,
            roi = effect.roiAt(activeStart),
            motionAnchors = anchors
        )
    }

    private fun safe(value: String): String {
        require(value.isNotBlank() && value.none { it == '/' || it == '\\' })
        return value
    }

    private data class PreviewSource(val uri: String, val width: Int, val height: Int)
    private data class Inputs(
        val project: VideoFlowProject,
        val editor: EditorProject,
        val clip: TimelineClip,
        val asset: MediaAsset,
        val proxy: ProxyMedia?,
        val effects: List<AiWatermarkEffect>
    )
}
