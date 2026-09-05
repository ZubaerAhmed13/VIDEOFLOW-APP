package com.videoflow.app.domain.export

import com.videoflow.app.domain.editor.ClipTransform
import com.videoflow.app.domain.editor.Keyframe
import com.videoflow.app.domain.editor.KeyframeEvaluator
import com.videoflow.app.domain.editor.KeyframeOwnerType
import com.videoflow.app.domain.editor.KeyframeProperty
import com.videoflow.app.domain.editor.RenderPlan
import com.videoflow.app.domain.editor.TextOverlay
import com.videoflow.app.domain.editor.ImageOverlay
import com.videoflow.app.domain.editor.TimelineClip
import com.videoflow.app.domain.editor.TrackType
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus

data class OriginalRenderSource(
    val assetId: String,
    val sourceUri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val frameRate: Double?,
    val videoCodecMime: String?,
    val audioCodecMime: String?,
    val audioSampleRate: Int?,
    val audioChannelCount: Int?,
    val videoBitrate: Int?,
    val colorStandard: Int?,
    val colorTransfer: Int?,
    val colorRange: Int?,
    val hdrStaticInfoPresent: Boolean,
    val fingerprintSha256: String?
)

data class FinalRenderPlan(
    val editorPlan: RenderPlan,
    val originalSources: Map<String, OriginalRenderSource>,
    val durationUs: Long
) {
    init {
        require(durationUs >= 0)
        val referenced = buildSet {
            addAll(editorPlan.clips.map { it.assetId })
            addAll(editorPlan.imageOverlays.map { it.assetId })
        }
        require(originalSources.keys.containsAll(referenced)) { "Final render plan must resolve every original asset" }
    }
}

data class FinalRenderCompileResult(
    val plan: FinalRenderPlan?,
    val problems: List<ExportProblem>
) {
    val ready: Boolean get() = plan != null && problems.isEmpty()
}

object FinalRenderPlanCompiler {
    fun compile(editorPlan: RenderPlan, assets: List<MediaAsset>): FinalRenderCompileResult {
        val assetsById = assets.associateBy { it.id }
        val requiredIds = buildSet {
            editorPlan.clips.filter { it.enabled }.forEach { add(it.assetId) }
            editorPlan.imageOverlays.forEach { add(it.assetId) }
        }.sorted()

        val problems = mutableListOf<ExportProblem>()
        val sources = linkedMapOf<String, OriginalRenderSource>()
        requiredIds.forEach { id ->
            val asset = assetsById[id]
            if (asset == null) {
                problems += ExportProblem(ExportFailureCode.SOURCE_MISSING, "Original media $id is not present in the project media bin.")
                return@forEach
            }
            when (asset.sourceStatus) {
                SourceStatus.AVAILABLE -> Unit
                SourceStatus.CHANGED -> problems += ExportProblem(ExportFailureCode.SOURCE_CHANGED, "${asset.displayName}: original media changed since import.")
                SourceStatus.PERMISSION_LOST -> problems += ExportProblem(ExportFailureCode.PERMISSION_LOST, "${asset.displayName}: Android document permission was lost.")
                SourceStatus.MISSING -> problems += ExportProblem(ExportFailureCode.SOURCE_MISSING, "${asset.displayName}: original media is offline.")
                SourceStatus.UNSUPPORTED, SourceStatus.CORRUPTED, SourceStatus.UNKNOWN -> problems += ExportProblem(ExportFailureCode.SOURCE_MISSING, "${asset.displayName}: original media is not currently exportable (${asset.sourceStatus}).")
            }
            if (!asset.permissionPersisted) {
                problems += ExportProblem(ExportFailureCode.PERMISSION_LOST, "${asset.displayName}: persistent source permission is not available for a background export.")
            }
            if (problems.none { it.message.startsWith("${asset.displayName}:") || (it.code == ExportFailureCode.SOURCE_MISSING && it.message.contains(id)) }) {
                sources[id] = asset.toOriginalRenderSource()
            }
        }

        if (problems.isNotEmpty()) return FinalRenderCompileResult(null, problems.distinct())

        val durationUs = maxOf(
            editorPlan.clips.filter { it.enabled }.maxOfOrNull { it.timelineEndUs } ?: 0L,
            editorPlan.textOverlays.maxOfOrNull { it.timelineEndUs } ?: 0L,
            editorPlan.imageOverlays.maxOfOrNull { it.timelineEndUs } ?: 0L
        )
        return FinalRenderCompileResult(
            plan = FinalRenderPlan(editorPlan, sources.toMap(), durationUs),
            problems = emptyList()
        )
    }

    private fun MediaAsset.toOriginalRenderSource() = OriginalRenderSource(
        assetId = id,
        sourceUri = sourceUri,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationUs = durationUs,
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
        frameRate = frameRate,
        videoCodecMime = videoCodecMime,
        audioCodecMime = audioCodecMime,
        audioSampleRate = audioSampleRate,
        audioChannelCount = audioChannelCount,
        videoBitrate = videoBitrate,
        colorStandard = colorStandard,
        colorTransfer = colorTransfer,
        colorRange = colorRange,
        hdrStaticInfoPresent = hdrStaticInfoPresent,
        fingerprintSha256 = fingerprintSha256
    )
}

data class EvaluatedTransform(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
    val opacity: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float
)

data class EvaluatedClip(
    val clip: TimelineClip,
    val localTimeUs: Long,
    val sourceTimeUs: Long,
    val transform: EvaluatedTransform,
    val audioGainDb: Float
)

data class EvaluatedText(val overlay: TextOverlay, val localTimeUs: Long, val transform: EvaluatedTransform)
data class EvaluatedImage(val overlay: ImageOverlay, val localTimeUs: Long, val transform: EvaluatedTransform)

data class RenderStateAtTime(
    val video: List<EvaluatedClip>,
    val audio: List<EvaluatedClip>,
    val text: List<EvaluatedText>,
    val images: List<EvaluatedImage>
)

object FinalRenderEvaluator {
    fun evaluate(plan: FinalRenderPlan, projectTimeUs: Long): RenderStateAtTime {
        require(projectTimeUs >= 0)
        val tracks = plan.editorPlan.tracks.associateBy { it.id }
        val audibleTrackIds = run {
            val audioCapable = plan.editorPlan.tracks.filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
            val solo = audioCapable.filter { it.solo }
            (if (solo.isNotEmpty()) solo else audioCapable).filterNot { it.muted }.map { it.id }.toSet()
        }
        val framesByOwner = plan.editorPlan.keyframes.groupBy { it.ownerId }

        val activeClips = plan.editorPlan.clips
            .asSequence()
            .filter { it.enabled && projectTimeUs >= it.timelineStartUs && projectTimeUs < it.timelineEndUs }
            .mapNotNull { clip ->
                val track = tracks[clip.trackId] ?: return@mapNotNull null
                if (!track.visible && track.type != TrackType.AUDIO) return@mapNotNull null
                val localUs = projectTimeUs - clip.timelineStartUs
                val sourceOffsetUs = (localUs.toDouble() * clip.speed).toLong().coerceIn(0L, clip.sourceDurationUs - 1)
                val ownerFrames = framesByOwner[clip.id].orEmpty()
                val transform = evaluateTransform(clip.transform, clip.opacity, localUs, ownerFrames)
                val gain = KeyframeEvaluator.evaluate(
                    clip.gainDb,
                    localUs,
                    ownerFrames.filter { it.property == KeyframeProperty.AUDIO_GAIN }
                ) + track.gainDb
                Triple(track, EvaluatedClip(clip, localUs, clip.sourceStartUs + sourceOffsetUs, transform, gain), clip.assetId)
            }
            .toList()

        val video = activeClips
            .filter { (track, clip, _) -> track.type == TrackType.VIDEO && clip.clip.enabled }
            .sortedBy { (track, _, _) -> track.orderIndex }
            .map { it.second }

        val audio = activeClips
            .filter { (track, _, _) -> track.type != TrackType.OVERLAY && track.id in audibleTrackIds }
            .sortedBy { (track, _, _) -> track.orderIndex }
            .map { it.second }

        val text = plan.editorPlan.textOverlays
            .filter { projectTimeUs >= it.timelineStartUs && projectTimeUs < it.timelineEndUs }
            .filter { tracks[it.trackId]?.visible != false }
            .sortedBy { tracks[it.trackId]?.orderIndex ?: Int.MIN_VALUE }
            .map { overlay ->
                val local = projectTimeUs - overlay.timelineStartUs
                EvaluatedText(overlay, local, evaluateTransform(overlay.transform, overlay.opacity, local, framesByOwner[overlay.id].orEmpty()))
            }

        val images = plan.editorPlan.imageOverlays
            .filter { projectTimeUs >= it.timelineStartUs && projectTimeUs < it.timelineEndUs }
            .filter { tracks[it.trackId]?.visible != false }
            .sortedBy { tracks[it.trackId]?.orderIndex ?: Int.MIN_VALUE }
            .map { overlay ->
                val local = projectTimeUs - overlay.timelineStartUs
                EvaluatedImage(overlay, local, evaluateTransform(overlay.transform, overlay.transform.opacity, local, framesByOwner[overlay.id].orEmpty()))
            }

        return RenderStateAtTime(video, audio, text, images)
    }

    private fun evaluateTransform(base: ClipTransform, ownerOpacity: Float, localUs: Long, frames: List<Keyframe>): EvaluatedTransform {
        fun property(property: KeyframeProperty, baseValue: Float): Float = KeyframeEvaluator.evaluate(
            baseValue,
            localUs,
            frames.filter { it.property == property }
        )
        return EvaluatedTransform(
            x = property(KeyframeProperty.POSITION_X, base.x),
            y = property(KeyframeProperty.POSITION_Y, base.y),
            scaleX = property(KeyframeProperty.SCALE_X, base.scaleX),
            scaleY = property(KeyframeProperty.SCALE_Y, base.scaleY),
            rotationDegrees = property(KeyframeProperty.ROTATION, base.rotationDegrees),
            opacity = property(KeyframeProperty.OPACITY, ownerOpacity * base.opacity).coerceIn(0f, 1f),
            flipHorizontal = base.flipHorizontal,
            flipVertical = base.flipVertical,
            cropLeft = base.crop.left,
            cropTop = base.crop.top,
            cropRight = base.crop.right,
            cropBottom = base.crop.bottom
        )
    }
}
