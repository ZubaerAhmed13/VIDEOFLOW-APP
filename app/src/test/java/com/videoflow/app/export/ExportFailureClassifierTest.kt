package com.videoflow.app.export

import com.videoflow.app.domain.export.ExportFailureCode
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFailureClassifierTest {
    @Test
    fun `maps common export failures to stable user facing codes`() {
        assertEquals(ExportFailureCode.PERMISSION_LOST, ExportFailureClassifier.classify(SecurityException("permission denied for content://secret/path")).code)
        assertEquals(ExportFailureCode.ENCODER_INIT_FAILED, ExportFailureClassifier.classify(IllegalStateException("MediaCodec encoder initialization failed")).code)
        assertEquals(ExportFailureCode.DECODER_FAILED, ExportFailureClassifier.classify(IllegalStateException("decoder stopped")).code)
        assertEquals(ExportFailureCode.MUXER_FAILED, ExportFailureClassifier.classify(IllegalStateException("muxer write failed")).code)
        assertEquals(ExportFailureCode.STORAGE_FULL, ExportFailureClassifier.classify(IOException("ENOSPC no space left on device")).code)
        assertEquals(ExportFailureCode.DESTINATION_IO, ExportFailureClassifier.classify(IOException("write failed")).code)
    }

    @Test
    fun `AI and graphics failures stay recoverable and do not expose raw details`() {
        val ai = ExportFailureClassifier.classify(IllegalStateException("LaMa inference failed at /private/source.mp4"))
        assertEquals(ExportFailureCode.UNKNOWN, ai.code)
        assertTrue(ai.userMessage.contains("Local AI"))
        assertFalse(ai.userMessage.contains("/private/source.mp4"))

        val gl = ExportFailureClassifier.classify(IllegalStateException("GL context lost"))
        assertEquals(ExportFailureCode.UNKNOWN, gl.code)
        assertTrue(gl.userMessage.contains("graphics"))
    }

    @Test
    fun `fatal VM or linkage failures are left to isolated export process`() {
        assertTrue(ExportFailureClassifier.shouldRethrow(StackOverflowError()))
        assertTrue(ExportFailureClassifier.shouldRethrow(LinkageError("native linkage")))
        assertFalse(ExportFailureClassifier.shouldRethrow(OutOfMemoryError("pressure")))
        assertFalse(ExportFailureClassifier.shouldRethrow(IllegalStateException("ordinary failure")))
    }
}
