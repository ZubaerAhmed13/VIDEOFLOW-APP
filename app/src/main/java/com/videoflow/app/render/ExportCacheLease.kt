package com.videoflow.app.render

/** Shared with explicit Settings cleanup; a running renderer owns its resumable files. */
object ExportCacheLease { val mutex=kotlinx.coroutines.sync.Mutex() }
