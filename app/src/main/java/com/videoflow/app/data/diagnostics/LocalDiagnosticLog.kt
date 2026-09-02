package com.videoflow.app.data.diagnostics

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticLevel { DEBUG, INFO, WARN, ERROR }

data class DiagnosticEvent(val timeMs: Long, val level: DiagnosticLevel, val message: String)

@Singleton
class LocalDiagnosticLog @Inject constructor() {
    private val events = ArrayDeque<DiagnosticEvent>()

    @Synchronized
    fun add(level: DiagnosticLevel, message: String) {
        // Messages are deliberately descriptive but must not include content URIs or user filenames.
        events.addLast(DiagnosticEvent(System.currentTimeMillis(), level, message.take(240)))
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<DiagnosticEvent> = events.toList()

    companion object {
        private const val MAX_EVENTS = 200
    }
}
