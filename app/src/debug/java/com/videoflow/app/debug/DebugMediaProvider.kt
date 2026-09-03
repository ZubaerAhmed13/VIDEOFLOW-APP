package com.videoflow.app.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import kotlin.concurrent.thread

/**
 * Debug-only content provider used by Step 1 instrumentation tests to exercise
 * the same content:// access path as a user-selected SAF document.
 *
 * The weak fixture is intentionally pipe-backed so random seek/stat size are
 * unavailable. This verifies provider-limited fingerprint behavior without
 * weakening production code. The class is excluded from release builds.
 */
class DebugMediaProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "video/mp4"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val name = requireName(uri)
        val file = materializeBacking(name)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            val row = newRow()
            columns.forEach { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> row.add(name)
                    OpenableColumns.SIZE -> row.add(if (isWeak(name)) null else file.length())
                    else -> row.add(null)
                }
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = requireName(uri)
        if (isWeak(name)) return openPipe(backingAssetName(name))
        return ParcelFileDescriptor.open(materializeBacking(name), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        val descriptor = openFile(uri, mode)
        return AssetFileDescriptor(descriptor, 0L, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun openPipe(assetName: String): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        val appContext = requireNotNull(context)
        thread(name = "VideoFlowDebugProviderPipe", isDaemon = true) {
            runCatching {
                appContext.assets.open(assetName).use { input ->
                    ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
            }.onFailure { runCatching { writeSide.close() } }
        }
        return readSide
    }

    private fun materializeBacking(name: String): File {
        val assetName = backingAssetName(name)
        val appContext = requireNotNull(context)
        val dir = File(appContext.cacheDir, "step1-media-fixtures").apply { mkdirs() }
        val file = File(dir, name)
        if (!file.exists()) {
            appContext.assets.open(assetName).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
        }
        return file
    }

    private fun requireName(uri: Uri): String {
        val name = requireNotNull(uri.lastPathSegment).substringAfterLast('/')
        require(name in ALLOWED_FIXTURES) { "Unsupported fixture: $name" }
        return name
    }

    private fun isWeak(name: String): Boolean = name == WEAK_FIXTURE

    private fun backingAssetName(name: String): String = if (isWeak(name)) "sample_av.mp4" else name

    companion object {
        private const val WEAK_FIXTURE = "weak_sample_av.mp4"
        private val ALLOWED_FIXTURES = setOf(
            "sample_av.mp4",
            "sample_video_only.mp4",
            "sample_rotated.mp4",
            "malformed.mp4",
            WEAK_FIXTURE
        )
    }
}
