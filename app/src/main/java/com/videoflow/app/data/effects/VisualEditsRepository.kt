package com.videoflow.app.data.effects

import android.content.Context
import android.util.AtomicFile
import com.videoflow.app.domain.effects.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Versioned parameter-only sidecar. AtomicFile retains the old document if a write is interrupted. */
class VisualEditsRepository(context: Context) {
    private val root = File(context.filesDir, "visual-edits")
    private val mutex = visualStateMutex
    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Unsafe project ID" }
        return AtomicFile(File(root, "$id.json"))
    }
    suspend fun load(id: String): VisualEdits = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = file(id)
            if (!source.baseFile.exists() && !File(source.baseFile.path + ".bak").exists()) VisualEdits()
            else source.openRead().bufferedReader().use { decode(it.readText()) }
        }
    }
    suspend fun replace(id: String, value: VisualEdits) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val destination = file(id)
            check(root.isDirectory || root.mkdirs()) { "Cannot create effects storage" }
            val output = destination.startWrite()
            try { output.write(encode(value).toByteArray(Charsets.UTF_8)); destination.finishWrite(output) }
            catch (failure: Throwable) { destination.failWrite(output); throw failure }
        }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { mutex.withLock { file(id).delete() } }

    companion object {
        fun encode(value: VisualEdits): String {
            val adjustments = JSONObject()
            value.enhance.toSortedMap().forEach { (id, parameters) ->
                val row = JSONObject()
                parameters.values.forEach { (key, amount) -> row.put(key.name, amount.toDouble()) }
                adjustments.put(id, row)
            }
            return JSONObject().put("version", 1).put("enhance", adjustments).put("effects", JSONArray(value.effects.map {
                JSONObject().put("id", it.id).put("clip", it.clipId).put("type", it.type.name)
                    .put("startUs", it.startUs).put("endUs", it.endUs).put("intensity", it.intensity.toDouble())
                    .put("enabled", it.enabled).put("order", it.order)
            })).toString()
        }
        fun decode(payload: String): VisualEdits {
            val root = JSONObject(payload)
            require(root.getInt("version") == 1) { "Unsupported visual editing version" }
            val rows = root.getJSONArray("effects")
            val effects = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
                VideoEffectNode(it.getString("id"), it.getString("clip"), VisualEffectType.valueOf(it.getString("type")),
                    it.getLong("startUs"), it.getLong("endUs"), it.getDouble("intensity").toFloat(), it.getBoolean("enabled"), it.getInt("order"))
            } }
            val values = root.getJSONObject("enhance")
            val enhance = values.keys().asSequence().associateWith { id ->
                val row = values.getJSONObject(id)
                EnhanceParameters(row.keys().asSequence().associate { Adjustment.valueOf(it) to row.getDouble(it).toFloat() })
            }
            return VisualEdits(effects, enhance)
        }
    }
}

private val visualStateMutex=Mutex()
