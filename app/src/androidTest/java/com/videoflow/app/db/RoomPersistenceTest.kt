package com.videoflow.app.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.data.db.MediaAssetEntity
import com.videoflow.app.data.db.ProjectEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: VideoFlowDatabase
    private lateinit var dbFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("step1-room-test.db")
        dbFile.delete()
        db = Room.databaseBuilder(context, VideoFlowDatabase::class.java, dbFile.name)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() {
        db.close()
        dbFile.delete()
    }

    @Test
    fun projectAndTenGigabyteMediaPersistAcrossDatabaseReopen() = runBlocking {
        val project = ProjectEntity("p", "Test", 1, 1L, 1L, null)
        db.projectDao().insert(project)
        db.mediaAssetDao().insert(largeEntity())
        db.close()

        db = Room.databaseBuilder(context, VideoFlowDatabase::class.java, dbFile.name)
            .allowMainThreadQueries()
            .build()
        val out = db.projectDao().get("p")!!
        assertEquals(10L * 1024L * 1024L * 1024L, out.media.single().sizeBytes)
        assertEquals(29.97, out.media.single().frameRate!!, 0.0001)
    }

    @Test
    fun projectCanBeCreatedRenamedAndDeletedWithoutDeletingOriginalMedia() = runBlocking {
        val project = ProjectEntity("p", "Before", 1, 1L, 1L, null)
        db.projectDao().insert(project)
        db.mediaAssetDao().insert(largeEntity())
        db.projectDao().update(project.copy(name = "After", updatedAt = 2L))
        assertEquals("After", db.projectDao().get("p")!!.project.name)
        db.projectDao().delete("p")
        assertNull(db.projectDao().get("p"))
    }

    private fun largeEntity() = MediaAssetEntity(
        assetId = "a",
        projectId = "p",
        sourceUri = "content://test/video",
        displayName = "video.mp4",
        mimeType = "video/mp4",
        sizeBytes = 10L * 1024L * 1024L * 1024L,
        durationUs = 9_000_000_000L,
        width = 3840,
        height = 2160,
        rotationDegrees = 0,
        frameRate = 29.97,
        videoCodecMime = "video/avc",
        audioCodecMime = "audio/mp4a-latm",
        audioSampleRate = 48_000,
        audioChannelCount = 2,
        videoTrackCount = 1,
        audioTrackCount = 1,
        videoBitrate = 50_000_000,
        videoProfile = null,
        videoLevel = null,
        colorStandard = null,
        colorTransfer = null,
        colorRange = null,
        hdrStaticInfoPresent = false,
        fingerprintSha256 = "hash",
        fingerprintAlgorithm = "VideoFlowSampleSHA256-v1",
        fingerprintStrength = "STRONG_THREE_REGION",
        fingerprintSampledBytes = 12L * 1024L * 1024L,
        fingerprintNote = null,
        permissionPersisted = true,
        sourceStatus = "AVAILABLE",
        createdAt = 1L
    )
}
