package com.videoflow.app.data.diagnostics

object DiagnosticRedaction {
    private val uri=Regex("(?i)\\b(?:content|file|https?|ftp)://[^\\s<>]+")
    private val path=Regex("(?<![A-Za-z0-9])/(?:storage|sdcard|data|mnt|workspace)/[^\\s<>]+")
    fun clean(message: String): String = path.replace(uri.replace(message,"[media location]"),"[private path]")
        .map { if(it.isISOControl()) ' ' else it }.joinToString("")
}
