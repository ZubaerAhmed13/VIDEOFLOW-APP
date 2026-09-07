package com.videoflow.app.render

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import com.videoflow.app.domain.export.FinalRenderPlan
import java.io.File

/** Read-only checks. Must run before any output probe, truncate, mux or failure cleanup. */
object ExportDestinationSafety {
    const val SOURCE_CONFLICT = "Choose a new output file. Export cannot overwrite media used by this project."

    fun problem(context: Context, plan: FinalRenderPlan, destination: Uri): String? {
        if(destination.scheme !in setOf("content","file")) return "Choose an Android document destination for this export."
        val inputs=plan.originalSources.values.map { Uri.parse(it.sourceUri) }.distinct()
        val targetIdentity=identity(context,destination)
        if(inputs.any { sameDocument(context,it,destination) ||
                (targetIdentity!=null && identity(context,it)==targetIdentity) }) return SOURCE_CONFLICT
        return null
    }

    private fun sameDocument(context: Context, a: Uri, b: Uri): Boolean {
        if(a.normalizeScheme().buildUpon().fragment(null).build()==b.normalizeScheme().buildUpon().fragment(null).build()) return true
        if(a.scheme=="file" && b.scheme=="file") return runCatching {
            File(requireNotNull(a.path)).canonicalFile==File(requireNotNull(b.path)).canonicalFile
        }.getOrDefault(false)
        return a.authority==b.authority && runCatching {
            DocumentsContract.isDocumentUri(context,a) && DocumentsContract.isDocumentUri(context,b) &&
                DocumentsContract.getDocumentId(a)==DocumentsContract.getDocumentId(b)
        }.getOrDefault(false)
    }

    private fun identity(context: Context, uri: Uri): Pair<Long,Long>? = runCatching {
        context.contentResolver.openFileDescriptor(uri,"r")?.use { descriptor ->
            Os.fstat(descriptor.fileDescriptor).let { stat ->
                if(OsConstants.S_ISREG(stat.st_mode)) stat.st_dev to stat.st_ino else null
            }
        }
    }.getOrNull()
}
