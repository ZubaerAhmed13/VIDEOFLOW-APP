package com.videoflow.app.domain.ai

import com.videoflow.app.ai.watermark.AiTemporalCheckpoint
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.nio.file.Files

class AiTemporalCheckpointTest {
    @Test fun temporalRoiStateSurvivesCheckpointWithoutSourceFrames() {
        val directory=Files.createTempDirectory("vf-temporal").toFile()
        try {
            val file=java.io.File(directory,"state")
            val state=AiTemporalCheckpoint()
            state.patches["region:0"]=byteArrayOf(4,5,6,7)
            state.write(file)
            val restored=AiTemporalCheckpoint();restored.read(file)
            assertArrayEquals(state.patches["region:0"],restored.patches["region:0"])
            assertEquals(1,restored.patches.size)
        } finally { directory.deleteRecursively() }
    }
    @Test fun corruptAllocationRequestDoesNotReplaceValidState() {
        val directory=Files.createTempDirectory("vf-temporal-corrupt").toFile()
        try {
            val file=java.io.File(directory,"state")
            DataOutputStream(file.outputStream()).use { it.writeInt(1);it.writeInt(1);it.writeUTF("region");it.writeInt(Int.MAX_VALUE) }
            val state=AiTemporalCheckpoint();state.patches["retained"]=byteArrayOf(1)
            assertTrue(runCatching { state.read(file) }.isFailure)
            assertArrayEquals(byteArrayOf(1),state.patches["retained"])
        } finally { directory.deleteRecursively() }
    }
}
