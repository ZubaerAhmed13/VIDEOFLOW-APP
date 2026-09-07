package com.videoflow.app.ai.watermark

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Small ROI histories only. A committed video segment and this state are checkpointed together. */
class AiTemporalCheckpoint {
    val patches = mutableMapOf<String, ByteArray>()
    fun store(key: String, value: ByteArray) {
        require(patches.values.sumOf { it.size.toLong() }-(patches[key]?.size ?: 0)+value.size <= MAX_BYTES) {
            "Active temporal regions exceed the safe memory budget. Reduce simultaneous regions or export separate sections."
        }
        patches[key]=value
    }
    fun retainEffects(ids: Set<String>) { patches.keys.removeAll { key -> ids.none { key.startsWith("$it:") } } }
    fun write(file: File) {
        require(patches.values.sumOf { it.size.toLong() } <= MAX_BYTES) { "Temporal ROI history exceeds the safe resource budget." }
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(1); out.writeInt(patches.size)
            patches.toSortedMap().forEach { (key,bytes) -> out.writeUTF(key);out.writeInt(bytes.size);out.write(bytes) }
        }
    }
    fun read(file: File) {
        require(file.length() in 8..MAX_BYTES+1_048_576L)
        val restored=mutableMapOf<String,ByteArray>()
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt()==1)
            val count=input.readInt();require(count in 0..16384)
            var bytes=0L
            repeat(count) {
                val key=input.readUTF();val size=input.readInt()
                require(size in 0..512*512*4)
                bytes+=size;require(bytes<=MAX_BYTES)
                val patch=ByteArray(size);input.readFully(patch);require(restored.put(key,patch)==null)
            }
            require(input.read()==-1)
        }
        patches.clear();patches.putAll(restored)
    }
    companion object { private const val MAX_BYTES=128L*1024L*1024L }
}
