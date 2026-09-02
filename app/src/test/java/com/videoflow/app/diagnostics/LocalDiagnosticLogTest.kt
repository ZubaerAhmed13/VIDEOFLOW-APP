package com.videoflow.app.diagnostics

import com.videoflow.app.data.diagnostics.DiagnosticLevel
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDiagnosticLogTest {
    @Test
    fun logIsBoundedAndMessagesAreTruncated() {
        val log = LocalDiagnosticLog()
        repeat(250) { log.add(DiagnosticLevel.DEBUG, "x".repeat(500)) }
        val events = log.snapshot()
        assertEquals(200, events.size)
        assertTrue(events.all { it.message.length <= 240 })
    }
}
