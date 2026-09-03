package com.videoflow.app.data.snapshot

import androidx.room.withTransaction
import com.videoflow.app.data.db.ClipEntity
import com.videoflow.app.data.db.ImageOverlayEntity
import com.videoflow.app.data.db.KeyframeEntity
import com.videoflow.app.data.db.ProjectSettingsEntity
import com.videoflow.app.data.db.SnapshotEntity
import com.videoflow.app.data.db.TextOverlayEntity
import com.videoflow.app.data.db.TrackEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotService @Inject constructor(private val db: VideoFlowDatabase) {
    suspend fun create(projectId: String, name: String): SnapshotEntity = withContext(Dispatchers.IO) {
        db.withTransaction {
            val settings = db.editorDao().getProjectSettings(projectId) ?: error("Project settings missing")
            val tracks = db.editorDao().getTracks(projectId)
            val clips = db.editorDao().getClips(projectId)
            val text = db.editorDao().getTextOverlays(projectId)
            val images = db.editorDao().getImageOverlays(projectId)
            val ownerIds = clips.map { it.id } + text.map { it.id } + images.map { it.id }
            val keyframes = if (ownerIds.isEmpty()) emptyList() else db.editorDao().getKeyframes(ownerIds)
            val payload = JSONObject()
                .put("format", 2)
                .put("settings", settings.toJson())
                .put("tracks", JSONArray(tracks.map { it.toJson() }))
                .put("clips", JSONArray(clips.map { it.toJson() }))
                .put("text", JSONArray(text.map { it.toJson() }))
                .put("images", JSONArray(images.map { it.toJson() }))
                .put("keyframes", JSONArray(keyframes.map { it.toJson() }))
                .toString()
            SnapshotEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                name = name.trim().ifBlank { "Snapshot" },
                projectFormatVersion = 2,
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            ).also { db.snapshotDao().put(it) }
        }
    }

    suspend fun list(projectId: String): List<SnapshotEntity> = withContext(Dispatchers.IO) {
        db.snapshotDao().getForProject(projectId)
    }

    suspend fun delete(snapshotId: String) = withContext(Dispatchers.IO) {
        db.snapshotDao().delete(snapshotId)
    }

    suspend fun restore(snapshotId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val snapshot = db.snapshotDao().get(snapshotId) ?: error("Snapshot not found")
            val root = JSONObject(snapshot.payloadJson)
            require(root.getInt("format") == 2) { "Unsupported snapshot format" }
            val projectId = snapshot.projectId
            val settings = settingsFromJson(root.getJSONObject("settings"))
            require(settings.projectId == projectId) { "Snapshot belongs to another project" }

            val writable = db.openHelper.writableDatabase
            writable.execSQL(
                "DELETE FROM keyframes WHERE ownerId IN (SELECT id FROM clips WHERE projectId=? UNION SELECT id FROM text_overlays WHERE projectId=? UNION SELECT id FROM image_overlays WHERE projectId=?)",
                arrayOf(projectId, projectId, projectId)
            )
            writable.execSQL("DELETE FROM tracks WHERE projectId=?", arrayOf(projectId))
            writable.execSQL("DELETE FROM project_settings WHERE projectId=?", arrayOf(projectId))

            db.editorDao().putProjectSettings(settings)
            val tracks = root.getJSONArray("tracks").mapObjects(::trackFromJson)
            db.editorDao().putTracks(tracks)
            val clips = root.getJSONArray("clips").mapObjects(::clipFromJson)
            if (clips.isNotEmpty()) db.editorDao().putClips(clips)
            root.getJSONArray("text").mapObjects(::textFromJson).forEach { db.editorDao().putTextOverlay(it) }
            root.getJSONArray("images").mapObjects(::imageFromJson).forEach { db.editorDao().putImageOverlay(it) }
            root.getJSONArray("keyframes").mapObjects(::keyframeFromJson).forEach { db.editorDao().putKeyframe(it) }

            val project = db.projectDao().get(projectId)?.project ?: error("Project missing")
            db.projectDao().update(project.copy(projectFormatVersion = 2, updatedAt = System.currentTimeMillis()))
        }
    }
}

private fun ProjectSettingsEntity.toJson() = JSONObject()
    .put("projectId", projectId).put("width", width).put("height", height)
    .put("fpsN", frameRateNumerator).put("fpsD", frameRateDenominator)
    .put("background", backgroundArgb).put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun settingsFromJson(o: JSONObject) = ProjectSettingsEntity(
    o.getString("projectId"), o.getInt("width"), o.getInt("height"), o.getInt("fpsN"), o.getInt("fpsD"),
    o.getLong("background"), o.getLong("createdAt"), o.getLong("updatedAt")
)

private fun TrackEntity.toJson() = JSONObject()
    .put("id", id).put("projectId", projectId).put("type", type).put("name", name).put("order", orderIndex)
    .put("muted", muted).put("solo", solo).put("locked", locked).put("visible", visible).put("gain", gainDb.toDouble())

private fun trackFromJson(o: JSONObject) = TrackEntity(
    o.getString("id"), o.getString("projectId"), o.getString("type"), o.getString("name"), o.getInt("order"),
    o.getBoolean("muted"), o.getBoolean("solo"), o.getBoolean("locked"), o.getBoolean("visible"), o.getDouble("gain").toFloat()
)

private fun ClipEntity.toJson() = JSONObject()
    .put("id", id).put("projectId", projectId).put("trackId", trackId).put("assetId", assetId)
    .put("timelineStartUs", timelineStartUs).put("sourceStartUs", sourceStartUs).put("sourceEndUs", sourceEndUs)
    .put("speed", speed).put("opacity", opacity.toDouble()).put("enabled", enabled).put("gainDb", gainDb.toDouble())
    .put("fadeInUs", fadeInUs).put("fadeOutUs", fadeOutUs)
    .put("x", x.toDouble()).put("y", y.toDouble()).put("scaleX", scaleX.toDouble()).put("scaleY", scaleY.toDouble())
    .put("rotation", rotationDegrees.toDouble()).put("flipH", flipHorizontal).put("flipV", flipVertical)
    .put("cropL", cropLeft.toDouble()).put("cropT", cropTop.toDouble()).put("cropR", cropRight.toDouble()).put("cropB", cropBottom.toDouble())

private fun clipFromJson(o: JSONObject) = ClipEntity(
    o.getString("id"), o.getString("projectId"), o.getString("trackId"), o.getString("assetId"),
    o.getLong("timelineStartUs"), o.getLong("sourceStartUs"), o.getLong("sourceEndUs"), o.getDouble("speed"),
    o.getDouble("opacity").toFloat(), o.getBoolean("enabled"), o.getDouble("gainDb").toFloat(), o.getLong("fadeInUs"), o.getLong("fadeOutUs"),
    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("scaleX").toFloat(), o.getDouble("scaleY").toFloat(),
    o.getDouble("rotation").toFloat(), o.getBoolean("flipH"), o.getBoolean("flipV"),
    o.getDouble("cropL").toFloat(), o.getDouble("cropT").toFloat(), o.getDouble("cropR").toFloat(), o.getDouble("cropB").toFloat()
)

private fun TextOverlayEntity.toJson() = JSONObject()
    .put("id", id).put("projectId", projectId).put("trackId", trackId).put("start", timelineStartUs).put("end", timelineEndUs)
    .put("content", content).put("fontSize", fontSizeSp.toDouble()).put("weight", fontWeight).put("italic", italic)
    .put("color", colorArgb).put("opacity", opacity.toDouble()).put("alignment", alignment)
    .put("x", x.toDouble()).put("y", y.toDouble()).put("scaleX", scaleX.toDouble()).put("scaleY", scaleY.toDouble()).put("rotation", rotationDegrees.toDouble())

private fun textFromJson(o: JSONObject) = TextOverlayEntity(
    o.getString("id"), o.getString("projectId"), o.getString("trackId"), o.getLong("start"), o.getLong("end"), o.getString("content"),
    o.getDouble("fontSize").toFloat(), o.getInt("weight"), o.getBoolean("italic"), o.getLong("color"), o.getDouble("opacity").toFloat(),
    o.getString("alignment"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("scaleX").toFloat(), o.getDouble("scaleY").toFloat(), o.getDouble("rotation").toFloat()
)

private fun ImageOverlayEntity.toJson() = JSONObject()
    .put("id", id).put("projectId", projectId).put("trackId", trackId).put("assetId", assetId).put("start", timelineStartUs).put("end", timelineEndUs)
    .put("x", x.toDouble()).put("y", y.toDouble()).put("scaleX", scaleX.toDouble()).put("scaleY", scaleY.toDouble()).put("rotation", rotationDegrees.toDouble()).put("opacity", opacity.toDouble())

private fun imageFromJson(o: JSONObject) = ImageOverlayEntity(
    o.getString("id"), o.getString("projectId"), o.getString("trackId"), o.getString("assetId"), o.getLong("start"), o.getLong("end"),
    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("scaleX").toFloat(), o.getDouble("scaleY").toFloat(), o.getDouble("rotation").toFloat(), o.getDouble("opacity").toFloat()
)

private fun KeyframeEntity.toJson() = JSONObject()
    .put("id", id).put("ownerId", ownerId).put("ownerType", ownerType).put("property", property)
    .put("timeUs", timeUs).put("value", value.toDouble()).put("interpolation", interpolation)

private fun keyframeFromJson(o: JSONObject) = KeyframeEntity(
    o.getString("id"), o.getString("ownerId"), o.getString("ownerType"), o.getString("property"),
    o.getLong("timeUs"), o.getDouble("value").toFloat(), o.getString("interpolation")
)

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    List(length()) { index -> block(getJSONObject(index)) }
