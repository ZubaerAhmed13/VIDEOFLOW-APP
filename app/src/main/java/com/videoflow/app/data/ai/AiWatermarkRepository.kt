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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic Step-4 sidecar persistence. This avoids a destructive Room schema migration while
 * keeping AI edits tied to stable project/clip IDs and fully local to app-private storage.
 */
@Singleton
class AiWatermarkRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root = File(context.filesDir, "ai-watermark/projects")

    suspend fun load(projectId: String): List<AiWatermarkEffect> = withContext(Dispatchers.IO) {
        requireSafeId(projectId)
        val file = fileFor(projectId)
        if (!file.isFile) return@withContext emptyList()
        val rootJson = JSONObject(file.readText(Charsets.UTF_8))
        require(rootJson.optInt("version", 0) == 1) { "Unsupported AI Watermark sidecar version." }
        val rows = rootJson.optJSONArray("effects") ?: JSONArray()
        buildList {
            for (i in 0 until rows.length()) add(effectFromJson(rows.getJSONObject(i)))
        }.sortedWith(compareBy<AiWatermarkEffect> { it.clipId }.thenBy { it.clipLocalStartUs }.thenBy { it.id })
    }

    suspend fun effectsForClip(projectId: String, clipId: String): List<AiWatermarkEffect> =
        load(projectId).filter { it.clipId == clipId }

    suspend fun upsert(effect: AiWatermarkEffect) = withContext(Dispatchers.IO) {
        val current = load(effect.projectId).associateBy { it.id }.toMutableMap()
        current[effect.id] = effect
        write(effect.projectId, current.values.toList())
    }

    suspend fun remove(projectId: String, effectId: String) = withContext(Dispatchers.IO) {
        write(projectId, load(projectId).filterNot { it.id == effectId })
    }

    suspend fun removeForClip(projectId: String, clipId: String) = withContext(Dispatchers.IO) {
        write(projectId, load(projectId).filterNot { it.clipId == clipId })
    }

    suspend fun replaceProjectEffects(projectId: String, effects: List<AiWatermarkEffect>) = withContext(Dispatchers.IO) {
        require(effects.all { it.projectId == projectId })
        write(projectId, effects)
    }

    private fun write(projectId: String, effects: List<AiWatermarkEffect>) {
        requireSafeId(projectId)
        root.mkdirs()
        val target = fileFor(projectId)
        val temp = File(root, ".${target.name}.tmp-${System.nanoTime()}")
        val rows = JSONArray()
        effects.sortedWith(compareBy<AiWatermarkEffect> { it.clipId }.thenBy { it.clipLocalStartUs }.thenBy { it.id })
            .forEach { rows.put(effectToJson(it)) }
        val payload = JSONObject()
            .put("version", 1)
            .put("projectId", projectId)
            .put("effects", rows)
            .toString()
        temp.outputStream().buffered().use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        if (target.exists() && !target.delete()) error("Could not replace AI Watermark sidecar.")
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Could not atomically persist AI Watermark sidecar.")
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
            )
        }
        return JSONObject()
            .put("id", effect.id)
            .put("projectId", effect.projectId)
            .put("clipId", effect.clipId)
            .put("startUs", effect.clipLocalStartUs)
            .put("endUs", effect.clipLocalEndUs)
            .put("roi", JSONObject()
                .put("left", effect.roi.left.toDouble())
                .put("top", effect.roi.top.toDouble())
                .put("right", effect.roi.right.toDouble())
                .put("bottom", effect.roi.bottom.toDouble()))
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
                        confidence = row.optDouble("confidence", 1.0).toFloat()
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
}
