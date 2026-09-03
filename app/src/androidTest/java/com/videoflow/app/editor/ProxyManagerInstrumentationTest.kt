package com.videoflow.app.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.data.db.MediaAssetEntity
import com.videoflow.app.data.db.ProjectEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.proxy.ProxyManager
import com.videoflow.app.domain.editor.ProxyQuality
import com.videoflow.app.domain.editor.ProxyStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProxyManagerInstrumentationTest {
    private lateinit var context: Context
    private lateinit var db: VideoFlowDatabase

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        File(context.filesDir, "proxies").deleteRecursively()
        db = openDb()
        db.projectDao().insert(ProjectEntity("p", "Proxy test", 2, 1L, 1L, 1L))
        db.mediaAssetDao().insert(videoAsset())
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        if (::context.isInitialized) {
            context.deleteDatabase(DB_NAME)
            File(context.filesDir, "proxies").deleteRecursively()
        }
    }

    @Test
    fun proxyGenerationPersistsInvalidatesAndDeletesWithoutTouchingOriginal() = runBlocking {
        val manager = ProxyManager(context, db)
        val generated = manager.generate(ASSET_ID, ProxyQuality.BALANCED)

        assertEquals(ProxyStatus.READY.name, generated.status)
        val proxyFile = File(generated.path)
        assertTrue(proxyFile.isFile)
        assertTrue(proxyFile.length() > 0L)
        assertTrue(generated.width <= 320)
        assertTrue(generated.height <= 240)

        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(generated.path)
            val durationMs = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            assertNotNull(durationMs)
            assertTrue(requireNotNull(durationMs) >= 2_500L)
        } finally {
            metadata.release()
        }

        db.close()
        db = openDb()
        val persisted = db.proxyDao().getForAsset(ASSET_ID)
        assertNotNull(persisted)
        assertEquals(ProxyStatus.READY.name, persisted?.status)
        assertEquals(generated.path, persisted?.path)
        assertEquals(FINGERPRINT, persisted?.sourceFingerprint)

        val changed = requireNotNull(db.mediaAssetDao().get(ASSET_ID)).copy(sourceStatus = "CHANGED")
        db.mediaAssetDao().update(changed)
        val reopenedManager = ProxyManager(context, db)
        reopenedManager.reconcile(ASSET_ID)
        assertEquals(ProxyStatus.STALE.name, db.proxyDao().getForAsset(ASSET_ID)?.status)

        reopenedManager.delete(ASSET_ID)
        assertEquals(null, db.proxyDao().getForAsset(ASSET_ID))
        assertFalse(proxyFile.exists())

        context.contentResolver.openFileDescriptor(SOURCE_URI, "r")!!.use { descriptor ->
            assertTrue(descriptor.fileDescriptor.valid())
        }
    }

    private fun openDb(): VideoFlowDatabase = Room.databaseBuilder(context, VideoFlowDatabase::class.java, DB_NAME).build()

    private fun videoAsset() = MediaAssetEntity(
        assetId = ASSET_ID,
        projectId = "p",
        sourceUri = SOURCE_URI.toString(),
        displayName = "sample_av.mp4",
        mimeType = "video/mp4",
        sizeBytes = 256_000L,
        durationUs = 3_000_000L,
        width = 320,
        height = 240,
        rotationDegrees = 0,
        frameRate = 30.0,
        videoCodecMime = "video/avc",
        audioCodecMime = "audio/mp4a-latm",
        audioSampleRate = 48_000,
        audioChannelCount = 1,
        videoTrackCount = 1,
        audioTrackCount = 1,
        videoBitrate = null,
        videoProfile = null,
        videoLevel = null,
        colorStandard = null,
        colorTransfer = null,
        colorRange = null,
        hdrStaticInfoPresent = false,
        fingerprintSha256 = FINGERPRINT,
        fingerprintAlgorithm = "test",
        fingerprintStrength = "STRONG_THREE_REGION",
        fingerprintSampledBytes = 256_000L,
        fingerprintNote = null,
        permissionPersisted = false,
        sourceStatus = "AVAILABLE",
        createdAt = 1L
    )

    companion object {
        private const val DB_NAME = "step2-proxy-instrumentation"
        private const val ASSET_ID = "video-asset"
        private const val FINGERPRINT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        private val SOURCE_URI: Uri = Uri.parse("content://com.videoflow.app.test.media/sample_av.mp4")
    }
}
