package com.videoflow.app.media

import com.videoflow.app.data.media.FingerprintEngine
import com.videoflow.app.data.media.RandomAccessReader
import com.videoflow.app.domain.model.FingerprintStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintEngineTest {
    private class MemoryReader(private val data: ByteArray) : RandomAccessReader {
        override val size: Long = data.size.toLong()
        override fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
            if (offset >= size) return -1
            val count = minOf(length.toLong(), size - offset).toInt()
            data.copyInto(buffer, 0, offset.toInt(), offset.toInt() + count)
            return count
        }
        override fun close() = Unit
    }

    private class SparseReader(override val size: Long) : RandomAccessReader {
        val offsets = mutableListOf<Long>()
        override fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
            offsets += offset
            if (offset >= size) return -1
            val count = minOf(length.toLong(), size - offset).toInt()
            for (i in 0 until count) buffer[i] = ((offset + i) and 0xff).toByte()
            return count
        }
        override fun close() = Unit
    }

    @Test
    fun deterministic() {
        val engine = FingerprintEngine(16, 8)
        val data = ByteArray(128) { it.toByte() }
        assertEquals(
            engine.fingerprint(MemoryReader(data), 1, 1, 1).sha256,
            engine.fingerprint(MemoryReader(data), 1, 1, 1).sha256
        )
    }

    @Test
    fun firstMiddleAndEndMatter() {
        val engine = FingerprintEngine(16, 8)
        val base = ByteArray(128) { it.toByte() }
        val hash = engine.fingerprint(MemoryReader(base), 1, 1, 1).sha256
        for (index in listOf(0, 64, 127)) {
            val changed = base.clone()
            changed[index] = (changed[index] + 1).toByte()
            assertNotEquals(hash, engine.fingerprint(MemoryReader(changed), 1, 1, 1).sha256)
        }
    }

    @Test
    fun smallFileIsFullyReadWithBoundedChunks() {
        val engine = FingerprintEngine(16, 8)
        val result = engine.fingerprint(MemoryReader(ByteArray(20) { 7 }), null, null, null)
        assertEquals(FingerprintStrength.FULL_SMALL_FILE, result.strength)
        assertEquals(20L, result.sampledBytes)
    }

    @Test(expected = IllegalStateException::class)
    fun truncatedReaderDoesNotClaimFullFingerprint() {
        val broken = object : RandomAccessReader {
            override val size = 100L
            private var calls = 0
            override fun readAt(offset: Long, buffer: ByteArray, length: Int): Int {
                calls++
                return if (calls == 1) minOf(8, length) else -1
            }
            override fun close() = Unit
        }
        FingerprintEngine(sampleBytes = 64, chunkBytes = 8).fingerprint(broken, null, null, null)
    }

    @Test
    fun hundredGigabyteStructuralReaderUses64BitOffsetsAndOnlyBoundedSamples() {
        val size = 100L * 1024L * 1024L * 1024L
        val reader = SparseReader(size)
        val result = FingerprintEngine().fingerprint(reader, 9_000_000_000L, 7680, 4320)
        assertEquals(FingerprintStrength.STRONG_THREE_REGION, result.strength)
        assertEquals(12L * 1024L * 1024L, result.sampledBytes)
        assertTrue(reader.offsets.any { it > Int.MAX_VALUE.toLong() })
        assertTrue(reader.offsets.maxOrNull()!! < size)
    }

    @Test
    fun cancellationHookIsConsultedDuringBoundedHashing() {
        var checks = 0
        val reader = SparseReader(10L * 1024L * 1024L * 1024L)
        runCatching {
            FingerprintEngine(sampleBytes = 1024, chunkBytes = 128).fingerprint(reader, null, null, null) {
                checks++
                if (checks == 3) error("cancel")
            }
        }
        assertTrue(checks >= 3)
    }
}
