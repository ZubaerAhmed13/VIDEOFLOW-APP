package com.videoflow.app.domain.editor

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object TimelineEngine {
    fun moveClip(clip: TimelineClip, newTimelineStartUs: Long): TimelineClip =
        clip.copy(timelineStartUs = newTimelineStartUs.coerceAtLeast(0))

    fun trimStart(clip: TimelineClip, newSourceStartUs: Long): TimelineClip {
        require(newSourceStartUs in 0 until clip.sourceEndUs)
        val deltaSourceUs = newSourceStartUs - clip.sourceStartUs
        val deltaTimelineUs = (deltaSourceUs.toDouble() / clip.speed).roundToLong()
        val newDurationUs = timelineDurationUs(clip.sourceEndUs - newSourceStartUs, clip.speed)
        return clip.copy(
            sourceStartUs = newSourceStartUs,
            timelineStartUs = (clip.timelineStartUs + deltaTimelineUs).coerceAtLeast(0),
            fadeInUs = clip.fadeInUs.coerceAtMost(newDurationUs),
            fadeOutUs = clip.fadeOutUs.coerceAtMost(newDurationUs)
        )
    }

    fun trimEnd(clip: TimelineClip, newSourceEndUs: Long, sourceDurationUs: Long): TimelineClip {
        require(newSourceEndUs > clip.sourceStartUs)
        require(newSourceEndUs <= sourceDurationUs)
        val newDurationUs = timelineDurationUs(newSourceEndUs - clip.sourceStartUs, clip.speed)
        return clip.copy(
            sourceEndUs = newSourceEndUs,
            fadeInUs = clip.fadeInUs.coerceAtMost(newDurationUs),
            fadeOutUs = clip.fadeOutUs.coerceAtMost(newDurationUs)
        )
    }

    fun splitClip(clip: TimelineClip, playheadUs: Long, rightClipId: String): Pair<TimelineClip, TimelineClip> {
        require(playheadUs > clip.timelineStartUs && playheadUs < clip.timelineEndUs)
        val localTimelineUs = playheadUs - clip.timelineStartUs
        val sourceOffsetUs = (localTimelineUs.toDouble() * clip.speed).roundToLong()
        val sourceSplitUs = (clip.sourceStartUs + sourceOffsetUs).coerceIn(
            clip.sourceStartUs + 1,
            clip.sourceEndUs - 1
        )
        val leftDurationUs = timelineDurationUs(sourceSplitUs - clip.sourceStartUs, clip.speed)
        val rightDurationUs = timelineDurationUs(clip.sourceEndUs - sourceSplitUs, clip.speed)
        val left = clip.copy(
            sourceEndUs = sourceSplitUs,
            fadeInUs = clip.fadeInUs.coerceAtMost(leftDurationUs),
            fadeOutUs = clip.fadeOutUs.coerceAtMost(leftDurationUs)
        )
        val right = clip.copy(
            id = rightClipId,
            timelineStartUs = playheadUs,
            sourceStartUs = sourceSplitUs,
            fadeInUs = clip.fadeInUs.coerceAtMost(rightDurationUs),
            fadeOutUs = clip.fadeOutUs.coerceAtMost(rightDurationUs)
        )
        return left to right
    }

    fun duplicateClip(clip: TimelineClip, newId: String, newTimelineStartUs: Long = clip.timelineEndUs): TimelineClip =
        clip.copy(id = newId, timelineStartUs = newTimelineStartUs.coerceAtLeast(0))

    fun compatible(track: TimelineTrack, mediaMime: String?): Boolean = when (track.type) {
        TrackType.VIDEO -> mediaMime?.startsWith("video/") == true
        TrackType.AUDIO -> mediaMime?.startsWith("audio/") == true
        TrackType.OVERLAY -> mediaMime?.startsWith("image/") == true
    }

    fun effectiveAudioTracks(tracks: List<TimelineTrack>): List<TimelineTrack> {
        val audioCapable = tracks.filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
        val solo = audioCapable.filter { it.solo }
        return (if (solo.isNotEmpty()) solo else audioCapable).filterNot { it.muted }
    }

    fun snapTime(
        candidateUs: Long,
        pixelsPerSecond: Double,
        thresholdPx: Double,
        targetsUs: Collection<Long>
    ): Long {
        require(pixelsPerSecond > 0.0 && pixelsPerSecond.isFinite())
        require(thresholdPx >= 0.0 && thresholdPx.isFinite())
        val thresholdUs = ((thresholdPx / pixelsPerSecond) * 1_000_000.0).roundToLong()
        return targetsUs
            .asSequence()
            .map { it to abs(it - candidateUs) }
            .filter { it.second <= thresholdUs }
            .minByOrNull { it.second }
            ?.first
            ?: candidateUs
    }

    fun splitKeyframes(
        keyframes: List<Keyframe>,
        originalOwnerId: String,
        rightOwnerId: String,
        splitLocalTimeUs: Long,
        idFactory: (Keyframe) -> String
    ): Pair<List<Keyframe>, List<Keyframe>> {
        val owned = keyframes.filter { it.ownerId == originalOwnerId }
        val left = owned.filter { it.timeUs <= splitLocalTimeUs }
        val right = owned.filter { it.timeUs >= splitLocalTimeUs }.map {
            it.copy(
                id = idFactory(it),
                ownerId = rightOwnerId,
                timeUs = (it.timeUs - splitLocalTimeUs).coerceAtLeast(0)
            )
        }
        return left to right
    }

    private fun timelineDurationUs(sourceDurationUs: Long, speed: Double): Long =
        (sourceDurationUs.toDouble() / speed).roundToLong().coerceAtLeast(1)
}

object KeyframeEvaluator {
    fun evaluate(baseValue: Float, localTimeUs: Long, keyframes: List<Keyframe>): Float {
        if (keyframes.isEmpty()) return baseValue
        val sorted = keyframes.sortedBy { it.timeUs }
        if (localTimeUs < sorted.first().timeUs) return baseValue
        if (localTimeUs == sorted.first().timeUs) return sorted.first().value
        if (localTimeUs >= sorted.last().timeUs) return sorted.last().value

        val rightIndex = sorted.indexOfFirst { it.timeUs >= localTimeUs }
        val right = sorted[rightIndex]
        if (right.timeUs == localTimeUs) return right.value
        val left = sorted[rightIndex - 1]
        if (left.interpolation == KeyframeInterpolation.HOLD) return left.value

        val span = (right.timeUs - left.timeUs).toDouble()
        val fraction = ((localTimeUs - left.timeUs).toDouble() / span).coerceIn(0.0, 1.0)
        return (left.value + (right.value - left.value) * fraction).toFloat()
    }
}

object AudioMath {
    fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    fun fadeGain(localTimeUs: Long, durationUs: Long, fadeInUs: Long, fadeOutUs: Long): Float {
        if (durationUs <= 0) return 0f
        val inGain = if (fadeInUs > 0) (localTimeUs.toDouble() / fadeInUs).coerceIn(0.0, 1.0) else 1.0
        val remainingUs = durationUs - localTimeUs
        val outGain = if (fadeOutUs > 0) (remainingUs.toDouble() / fadeOutUs).coerceIn(0.0, 1.0) else 1.0
        return minOf(inGain, outGain).toFloat()
    }
}

data class PreviewClip(
    val clip: TimelineClip,
    val source: String,
    val usingProxy: Boolean
)

data class PreviewPlan(
    val projectId: String,
    val width: Int,
    val height: Int,
    val frameRate: FrameRate,
    val tracks: List<TimelineTrack>,
    val clips: List<PreviewClip>,
    val textOverlays: List<TextOverlay>,
    val imageOverlays: List<ImageOverlay>,
    val keyframes: List<Keyframe>
)

data class RenderPlan(
    val projectId: String,
    val width: Int,
    val height: Int,
    val frameRate: FrameRate,
    val tracks: List<TimelineTrack>,
    val clips: List<TimelineClip>,
    val textOverlays: List<TextOverlay>,
    val imageOverlays: List<ImageOverlay>,
    val keyframes: List<Keyframe>,
    val backgroundArgb: Long = 0xFF000000
)

object PlanBuilder {
    fun preview(
        settings: ProjectSettings,
        state: TimelineState,
        originalSources: Map<String, String>,
        proxies: Map<String, ProxyMedia>,
        preferProxy: Boolean = true
    ): PreviewPlan {
        val clips = state.clips.sortedWith(compareBy<TimelineClip> { it.timelineStartUs }.thenBy { it.id }).map { clip ->
            val proxy = proxies[clip.assetId]?.takeIf { it.status == ProxyStatus.READY }
            val useProxy = preferProxy && proxy != null
            PreviewClip(
                clip = clip,
                source = if (useProxy) proxy!!.path else originalSources.getValue(clip.assetId),
                usingProxy = useProxy
            )
        }
        return PreviewPlan(
            projectId = state.projectId,
            width = settings.width,
            height = settings.height,
            frameRate = settings.frameRate,
            tracks = state.tracks.sortedBy { it.orderIndex },
            clips = clips,
            textOverlays = state.textOverlays.sortedBy { it.timelineStartUs },
            imageOverlays = state.imageOverlays.sortedBy { it.timelineStartUs },
            keyframes = state.keyframes.sortedWith(compareBy<Keyframe> { it.ownerId }.thenBy { it.property.name }.thenBy { it.timeUs })
        )
    }

    fun render(settings: ProjectSettings, state: TimelineState): RenderPlan = RenderPlan(
        projectId = state.projectId,
        width = settings.width,
        height = settings.height,
        frameRate = settings.frameRate,
        tracks = state.tracks.sortedBy { it.orderIndex },
        clips = state.clips.sortedWith(compareBy<TimelineClip> { it.timelineStartUs }.thenBy { it.id }),
        textOverlays = state.textOverlays.sortedBy { it.timelineStartUs },
        imageOverlays = state.imageOverlays.sortedBy { it.timelineStartUs },
        keyframes = state.keyframes.sortedWith(compareBy<Keyframe> { it.ownerId }.thenBy { it.property.name }.thenBy { it.timeUs }),
        backgroundArgb = settings.backgroundArgb
    )
}

data class ValidationIssue(val code: String, val detail: String)

object ProjectValidator {
    fun validate(state: TimelineState): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val trackIds = state.tracks.map { it.id }.toSet()
        val duplicateTrackIds = state.tracks.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        duplicateTrackIds.forEach { issues += ValidationIssue("DUPLICATE_TRACK", it) }

        state.clips.forEach { clip ->
            if (clip.trackId !in trackIds) issues += ValidationIssue("MISSING_TRACK", clip.id)
            if (clip.projectId != state.projectId) issues += ValidationIssue("WRONG_PROJECT", clip.id)
        }
        state.textOverlays.forEach { if (it.trackId !in trackIds) issues += ValidationIssue("MISSING_TEXT_TRACK", it.id) }
        state.imageOverlays.forEach { if (it.trackId !in trackIds) issues += ValidationIssue("MISSING_IMAGE_TRACK", it.id) }

        val ownerIds = buildSet {
            addAll(state.clips.map { it.id })
            addAll(state.textOverlays.map { it.id })
            addAll(state.imageOverlays.map { it.id })
        }
        state.keyframes.forEach { if (it.ownerId !in ownerIds) issues += ValidationIssue("MISSING_KEYFRAME_OWNER", it.id) }
        return issues
    }
}

interface EditCommand<T> {
    fun apply(state: T): T
    fun revert(state: T): T
}

class EditHistory<T>(private val limit: Int = 200) {
    init { require(limit > 0) }
    private val undo = ArrayDeque<EditCommand<T>>()
    private val redo = ArrayDeque<EditCommand<T>>()

    fun execute(state: T, command: EditCommand<T>): T {
        val next = command.apply(state)
        undo.addLast(command)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
        return next
    }

    fun undo(state: T): T {
        val command = undo.removeLastOrNull() ?: return state
        redo.addLast(command)
        return command.revert(state)
    }

    fun redo(state: T): T {
        val command = redo.removeLastOrNull() ?: return state
        undo.addLast(command)
        return command.apply(state)
    }

    fun canUndo(): Boolean = undo.isNotEmpty()
    fun canRedo(): Boolean = redo.isNotEmpty()
}
