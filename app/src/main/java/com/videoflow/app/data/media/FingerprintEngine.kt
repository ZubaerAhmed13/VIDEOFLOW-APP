package com.videoflow.app.data.media

import com.videoflow.app.domain.model.FingerprintResult
import com.videoflow.app.domain.model.FingerprintStrength
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

interface RandomAccessReader : AutoCloseable {
    val size: Long
    fun readAt(offset: Long, buffer: ByteArray, length: Int): Int
}

class FingerprintEngine(
    private val sampleBytes: Int = 4 * 1024 * 1024,
    private val chunkBytes: Int = 256 * 1024
) {
    init {
        require(sampleBytes > 0)
        require(chunkBytes > 0)
    }

    fun fingerprint(
        reader: RandomAccessReader,
        durationUs: Long?,
        width: Int?,
        height: Int?,
        cancellationCheck: () -> Unit = {}
    ): FingerprintResult {
        require(reader.size >= 0L)
        val digest = MessageDigest.getInstance("SHA-256")
        fun meta(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }

        meta(ALGORITHM)
        meta(reader.size.toString())
        meta(durationUs?.toString() ?: "?")
        meta(width?.toString() ?: "?")
        meta(height?.toString() ?: "?")

        val size = reader.size
        if (size == 0L) {
            return FingerprintResult(
                sha256 = digest.digest().toHex(),
                strength = FingerprintStrength.FULL_SMALL_FILE,
                sampledBytes = 0L
            )
        }

        val totalWindowBytes = sampleBytes.toLong() * 3L
        return if (size <= totalWindowBytes) {
            hashEntireSmallFile(reader, digest, cancellationCheck)
        } else {
            hashThreeRegions(reader, digest, cancellationCheck)
        }
    }

    private fun hashEntireSmallFile(
        reader: RandomAccessReader,
        digest: MessageDigest,
        cancellationCheck: () -> Unit
    ): FingerprintResult {
        var offset = 0L
        var total = 0L
        val bufferSize = min(chunkBytes.toLong(), max(1L, reader.size)).toInt()
        val buffer = ByteArray(bufferSize)
        while (offset < reader.size) {
            cancellationCheck()
            val wanted = min(buffer.size.toLong(), reader.size - offset).toInt()
            val read = reader.readAt(offset, buffer, wanted)
            if (read <= 0) {
                throw IllegalStateException("Unexpected end of media while fingerprinting at offset $offset")
            }
            digest.update(buffer, 0, read)
            offset += read.toLong()
            total += read.toLong()
        }
        return FingerprintResult(
            sha256 = digest.digest().toHex(),
            strength = FingerprintStrength.FULL_SMALL_FILE,
            sampledBytes = total
        )
    }

    private fun hashThreeRegions(
        reader: RandomAccessReader,
        digest: MessageDigest,
        cancellationCheck: () -> Unit
    ): FingerprintResult {
        val window = sampleBytes.toLong()
        val starts = listOf(
            0L,
            max(0L, (reader.size - window) / 2L),
            max(0L, reader.size - window)
        ).distinct()

        var total = 0L
        val buffer = ByteArray(min(chunkBytes, sampleBytes))
        for (start in starts) {
            var offset = start
            var remaining = sampleBytes
            while (remaining > 0) {
                cancellationCheck()
                val wanted = min(buffer.size, remaining)
                val read = reader.readAt(offset, buffer, wanted)
                if (read <= 0) {
                    throw IllegalStateException("Random access unavailable at offset $offset")
                }
                digest.update(buffer, 0, read)
                offset += read.toLong()
                remaining -= read
                total += read.toLong()
            }
        }

        return FingerprintResult(
            sha256 = digest.digest().toHex(),
            strength = FingerprintStrength.STRONG_THREE_REGION,
            sampledBytes = total
        )
    }

    companion object {
        const val ALGORITHM = "VideoFlowSampleSHA256-v1"
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }
