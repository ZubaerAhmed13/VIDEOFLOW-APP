package com.videoflow.app.data.ai

import android.content.Context
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.ai.NormalizedRoi
import com.videoflow.app.domain.ai.RoiMotionAnchor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic Step-4 sidecar persistence. This avoids a destructive Room schema migration while
 * keeping AI edits tied to stable project/clip IDs and fully local to app-private storage.
 *
 * The same codec is used for normal persistence, project snapshots and deletion compensation so
 * those lifecycle paths cannot silently drift apart.
 */
@Singleton
class AiWatermarkRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val visualEdits = com.videoflow.app.data.effects.VisualEditsRepository(context)
    private val root = File(context.filesDir, "ai-watermark/projects")
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** Emits the project id after every successful AI-sidecar replacement or removal. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    suspend fun load(projectId: String): List<AiWatermarkEffect> = withContext(Dispatchers.IO) {
        requireSafeId(projectId)
        readEffects(projectId)
    }

    suspend fun effectsForClip(projectId: String, clipId: String): List<AiWatermarkEffect> =
        load(projectId).filter { it.clipId == clipId }

    suspend fun upsert(effect: AiWatermarkEffect) = withContext(Dispatchers.IO) {
        val current = readEffects(effect.projectId).associateBy { it.id }.toMutableMap()
        current[effect.id] = effect
        write(effect.projectId, current.values.toList())
    }

    suspend fun remove(projectId: String, effectId: String) = withContext(Dispatchers.IO) {
        write(projectId, readEffects(projectId).filterNot { it.id == effectId })
    }

    suspend fun removeForClip(projectId: String, clipId: String) = withContext(Dispatchers.IO) {
        write(projectId, readEffects(projectId).filterNot { it.clipId == clipId })
    }

    suspend fun replaceProjectEffects(projectId: String, effects: List<AiWatermarkEffect>) = withContext(Dispatchers.IO) {
        requireSafeId(projectId)
        require(effects.all { it.projectId == projectId })
        write(projectId, effects)
    }

    /**
     * Returns a complete, versioned AI state document for project snapshots and rollback. The
     * returned document is valid even when the project currently has no AI effects.
     */
    suspend fun exportProjectStateJson(projectId: String): String = withContext(Dispatchers.IO) {
        requireSafeId(projectId)
        encodeState(projectId, readEffects(projectId)).toString()
    }

    /** Restores an exact state previously produced by [exportProjectStateJson]. */
    suspend fun restoreProjectStateJson(projectId: String, payloadJson: String) = withContext(Dispatchers.IO) {
        requireSafeId(projectId)
        val effects = decodeState(projectId, JSONObject(payloadJson))
        write(projectId, effects)
    }

    /**
     * Deletes all persistent AI state owned by a project, including interrupted atomic-write temp
     * files. This is the canonical cleanup operation for project deletion.
     */
    suspend fun deleteProjectState(projectId: String) = withContext(Dispatchers.IO) {
        requireSafeId(projectId)
        deleteStateFiles(projectId)
        _changes.tryEmit(projectId)
    }

    private fun readEffects(projectId: String): List<AiWatermarkEffect> {
        requireSafeId(projectId)
        val file = fileFor(projectId)
        if (!file.isFile) return emptyList()
        return decodeState(projectId, JSONObject(file.readText(Charsets.UTF_8)))
    }

    private fun write(projectId: String, effects: List<AiWatermarkEffect>) {
        requireSafeId(projectId)
        require(effects.all { it.projectId == projectId })

        // An empty project AI state does not need a persistent sidecar. Removing it also guarantees
        // Undo/Redo, legacy snapshot restore and project cleanup cannot leave empty orphan files.
        if (effects.isEmpty()) {
            deleteStateFiles(projectId)
            _changes.tryEmit(projectId)
            return
        }

        root.mkdirs()
        val target = fileFor(projectId)
        val temp = File(root, ".${target.name}.tmp-${System.nanoTime()}")
        val payload = encodeState(projectId, effects).toString()
        temp.outputStream().buffered().use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        if (target.exists() && !target.delete()) {
            temp.delete()
            error("Could not replace AI Watermark sidecar.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Could not atomically persist AI Watermark sidecar.")
        }
        cleanupTempFiles(projectId)
        _changes.tryEmit(projectId)
    }

    private fun encodeState(projectId: String, effects: List<AiWatermarkEffect>): JSONObject {
        val rows = JSONArray()
        effects.sortedWith(EFFECT_ORDER).forEach { rows.put(effectToJson(it)) }
        return JSONObject()
            .put("version", SIDECAR_VERSION)
            .put("projectId", projectId)
            .put("effects", rows)
    }

    private fun decodeState(projectId: String, rootJson: JSONObject): List<AiWatermarkEffect> {
        require(rootJson.optInt("version", 0) == SIDECAR_VERSION) {
            "Unsupported AI Watermark sidecar version."
        }
        require(rootJson.optString("projectId") == projectId) {
            "AI Watermark sidecar belongs to another project."
        }
        val rows = rootJson.optJSONArray("effects") ?: JSONArray()
        val effects = buildList {
            for (i in 0 until rows.length()) add(effectFromJson(rows.getJSONObject(i)))
        }
        require(effects.all { it.projectId == projectId }) {
            "AI Watermark sidecar contains an effect owned by another project."
        }
        return effects.sortedWith(EFFECT_ORDER)
    }

    private fun deleteStateFiles(projectId: String) {
        val target = fileFor(projectId)
        if (target.exists() && !target.delete()) {
            error("Could not delete AI Watermark sidecar.")
        }
        cleanupTempFiles(projectId)
        if (root.isDirectory && root.listFiles()?.isEmpty() == true) {
            // Best-effort parent cleanup only; the project state itself is already gone.
            runCatching { root.delete() }
        }
    }

    private fun cleanupTempFiles(projectId: String) {
        if (!root.isDirectory) return
        val prefix = ".${fileFor(projectId).name}.tmp-"
        root.listFiles()
            ?.filter { it.name.startsWith(prefix) }
            ?.forEach { temp ->
                if (temp.exists() && !temp.delete()) {
                    error("Could not delete interrupted AI Watermark sidecar temp file.")
                }
            }
    }

    private fun fileFor(projectId: String) = File(root, "$projectId.json")

    private fun requireSafeId(id: String) {
        require(id.isNotBlank() && id.none { it == '/' || it == '\\' }) { "Unsafe project identifier." }
    }

    private fun effectToJson(effect: AiWatermarkEffect): JSONObject {
        val anchors = JSONArray()
        effect.motionAnchors.sortedBy { it.clipLocalTimeUs }.forEach { anchor ->
            anchors.put(
                JSONObject()
                    .put("timeUs", anchor.clipLocalTimeUs)
                    .put("centerX", anchor.centerX.toDouble())
                    .put("centerY", anchor.centerY.toDouble())
                    .put("confidence", anchor.confidence.toDouble())
                    .put("width", anchor.width?.toDouble()).put("height", anchor.height?.toDouble()).put("manual", anchor.manual)
            )
        }
        return JSONObject()
            .put("id", effect.id)
            .put("projectId", effect.projectId)
            .put("clipId", effect.clipId)
            .put("startUs", effect.clipLocalStartUs)
            .put("endUs", effect.clipLocalEndUs)
            .put(
                "roi",
                JSONObject()
                    .put("left", effect.roi.left.toDouble())
                    .put("top", effect.roi.top.toDouble())
                    .put("right", effect.roi.right.toDouble())
                    .put("bottom", effect.roi.bottom.toDouble())
            )
            .put("motionAnchors", anchors)
            .put("contextPaddingPx", effect.contextPaddingPx)
            .put("featherPx", effect.featherPx)
            .put("temporalStability", effect.temporalStability.toDouble())
            .put("modelId", effect.modelId)
            .put("enabled", effect.enabled)
    }

    private fun effectFromJson(json: JSONObject): AiWatermarkEffect {
        val roiJson = json.getJSONObject("roi")
        val anchorsJson = json.optJSONArray("motionAnchors") ?: JSONArray()
        val anchors = buildList {
            for (i in 0 until anchorsJson.length()) {
                val row = anchorsJson.getJSONObject(i)
                add(
                    RoiMotionAnchor(
                        clipLocalTimeUs = row.getLong("timeUs"),
                        centerX = row.getDouble("centerX").toFloat(),
                        centerY = row.getDouble("centerY").toFloat(),
                        confidence = row.optDouble("confidence", 1.0).toFloat(),
                        width = if (row.has("width")) row.getDouble("width").toFloat() else null,
                        height = if (row.has("height")) row.getDouble("height").toFloat() else null,
                        manual = row.optBoolean("manual", false)
                    )
                )
            }
        }
        return AiWatermarkEffect(
            id = json.getString("id"),
            projectId = json.getString("projectId"),
            clipId = json.getString("clipId"),
            clipLocalStartUs = json.getLong("startUs"),
            clipLocalEndUs = json.getLong("endUs"),
            roi = NormalizedRoi(
                roiJson.getDouble("left").toFloat(),
                roiJson.getDouble("top").toFloat(),
                roiJson.getDouble("right").toFloat(),
                roiJson.getDouble("bottom").toFloat()
            ),
            motionAnchors = anchors,
            contextPaddingPx = json.optInt("contextPaddingPx", 48),
            featherPx = json.optInt("featherPx", 8),
            temporalStability = json.optDouble("temporalStability", 0.12).toFloat(),
            modelId = json.optString("modelId", com.videoflow.app.domain.ai.AiModelCatalog.FINAL_512.id),
            enabled = json.optBoolean("enabled", true)
        )
    }

    private companion object {
        const val SIDECAR_VERSION = 1
        val EFFECT_ORDER = compareBy<AiWatermarkEffect> { it.clipId }
            .thenBy { it.clipLocalStartUs }
            .thenBy { it.id }
    }
}
