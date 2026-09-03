package com.videoflow.app.editor

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.data.db.ClipEntity
import com.videoflow.app.data.db.ImageOverlayEntity
import com.videoflow.app.data.db.KeyframeEntity
import com.videoflow.app.data.db.MediaAssetEntity
import com.videoflow.app.data.db.ProjectEntity
import com.videoflow.app.data.db.ProjectSettingsEntity
import com.videoflow.app.data.db.ProxyEntity
import com.videoflow.app.data.db.TextOverlayEntity
import com.videoflow.app.data.db.TrackEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.snapshot.SnapshotService
import com.videoflow.app.domain.editor.ProxyStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step2ComplexPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: VideoFlowDatabase

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        db = openDb()
        seedComplexProject()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        if (::context.isInitialized) context.deleteDatabase(DB_NAME)
    }

    @Test
    fun complexProjectReopensExactlyAndSnapshotRestoresTransactionally() = runBlocking {
        db.close()
        db = openDb()

        val loaded = EditorRepository(db).load(PROJECT_ID)
        assertEquals(5, loaded.timeline.tracks.size)
        assertEquals(5, loaded.timeline.clips.size)
        assertEquals(1, loaded.timeline.textOverlays.size)
        assertEquals(1, loaded.timeline.imageOverlays.size)
        assertEquals(8, loaded.timeline.keyframes.size)
        assertEquals(1, loaded.proxies.size)
        assertEquals(ProxyStatus.READY, loaded.proxies.single().status)
        assertEquals(2.0, loaded.timeline.clips.first { it.id == "c3" }.speed, 0.0)
        assertEquals(-6f, loaded.timeline.clips.first { it.id == "c4" }.gainDb, 0f)

        val snapshots = SnapshotService(db)
        val snapshot = snapshots.create(PROJECT_ID, "Certification snapshot")
        assertNotNull(db.snapshotDao().get(snapshot.id))

        db.editorDao().deleteKeyframes("c1")
        db.editorDao().deleteClip("c1")
        assertEquals(4, db.editorDao().getClips(PROJECT_ID).size)

        snapshots.restore(snapshot.id)
        assertEquals(5, db.editorDao().getClips(PROJECT_ID).size)
        val ownerIds = db.editorDao().getClips(PROJECT_ID).map { it.id } +
            db.editorDao().getTextOverlays(PROJECT_ID).map { it.id } +
            db.editorDao().getImageOverlays(PROJECT_ID).map { it.id }
        assertEquals(8, db.editorDao().getKeyframes(ownerIds).size)

        snapshots.delete(snapshot.id)
        assertEquals(0, snapshots.list(PROJECT_ID).size)
    }

    private fun openDb(): VideoFlowDatabase = Room.databaseBuilder(context, VideoFlowDatabase::class.java, DB_NAME).build()

    private suspend fun seedComplexProject() {
        db.projectDao().insert(ProjectEntity(PROJECT_ID, "Complex", 2, 1L, 2L, 2L))
        db.mediaAssetDao().insert(asset("video", "video/mp4", 20_000_000L))
        db.mediaAssetDao().insert(asset("audio", "audio/aac", 20_000_000L))
        db.mediaAssetDao().insert(asset("image", "image/png", null))
        db.editorDao().putProjectSettings(ProjectSettingsEntity(PROJECT_ID, 3840, 2160, 30_000, 1_001, 0xFF000000, 1L, 2L))

        val tracks = listOf(
            TrackEntity("v1", PROJECT_ID, "VIDEO", "V1", 0, false, false, false, true, 0f),
            TrackEntity("v2", PROJECT_ID, "VIDEO", "V2", 1, false, false, false, true, 0f),
            TrackEntity("a1", PROJECT_ID, "AUDIO", "A1", 2, false, false, false, true, -1f),
            TrackEntity("a2", PROJECT_ID, "AUDIO", "A2", 3, false, false, false, true, 0f),
            TrackEntity("o1", PROJECT_ID, "OVERLAY", "O1", 4, false, false, false, true, 0f)
        )
        db.editorDao().putTracks(tracks)

        db.editorDao().putClips(
            listOf(
                clip("c1", "v1", "video", 0, 0, 4_000_000, 1.0, 0f),
                clip("c2", "v1", "video", 5_000_000, 4_000_000, 8_000_000, 1.0, 0f),
                clip("c3", "v2", "video", 1_000_000, 0, 8_000_000, 2.0, 0f),
                clip("c4", "a1", "audio", 0, 0, 10_000_000, 1.0, -6f),
                clip("c5", "a2", "audio", 2_000_000, 2_000_000, 12_000_000, 1.0, -3f)
            )
        )

        db.editorDao().putTextOverlay(
            TextOverlayEntity("text1", PROJECT_ID, "o1", 500_000, 6_000_000, "VideoFlow", 42f, 700, false, 0xFFFFFFFF, 0.9f, "CENTER", 0.5f, 0.2f, 1f, 1f, 0f)
        )
        db.editorDao().putImageOverlay(
            ImageOverlayEntity("image1", PROJECT_ID, "o1", "image", 2_000_000, 9_000_000, 0.7f, 0.7f, 0.5f, 0.5f, 15f, 0.8f)
        )

        val keyframes = listOf(
            keyframe("k1", "c1", "CLIP", "OPACITY", 0, 0f, "LINEAR"),
            keyframe("k2", "c1", "CLIP", "OPACITY", 2_000_000, 1f, "LINEAR"),
            keyframe("k3", "c3", "CLIP", "POSITION_X", 0, 0.2f, "HOLD"),
            keyframe("k4", "c3", "CLIP", "POSITION_X", 2_000_000, 0.8f, "LINEAR"),
            keyframe("k5", "c4", "CLIP", "AUDIO_GAIN", 0, -12f, "LINEAR"),
            keyframe("k6", "c4", "CLIP", "AUDIO_GAIN", 5_000_000, -3f, "LINEAR"),
            keyframe("k7", "text1", "TEXT_OVERLAY", "SCALE_X", 1_000_000, 1.2f, "LINEAR"),
            keyframe("k8", "image1", "IMAGE_OVERLAY", "ROTATION", 1_000_000, 30f, "HOLD")
        )
        keyframes.forEach { db.editorDao().putKeyframe(it) }

        db.proxyDao().put(
            ProxyEntity("proxy1", "video", "/data/user/0/com.videoflow.app.debug/files/proxies/video.mp4", 1280, 720, "video/avc", "fp-video", "READY", "BALANCED", 3L, 1_000_000L)
        )
    }

    private fun asset(id: String, mime: String, durationUs: Long?) = MediaAssetEntity(
        assetId = id,
        projectId = PROJECT_ID,
        sourceUri = "content://test/$id",
        displayName = "$id.dat",
        mimeType = mime,
        sizeBytes = if (durationUs == null) 1_000L else 10_000_000L,
        durationUs = durationUs,
        width = if (id == "video") 3840 else if (id == "image") 1920 else null,
        height = if (id == "video") 2160 else if (id == "image") 1080 else null,
        rotationDegrees = 0,
        frameRate = if (id == "video") 29.97 else null,
        videoCodecMime = if (id == "video") "video/hevc" else null,
        audioCodecMime = if (id == "audio" || id == "video") "audio/mp4a-latm" else null,
        audioSampleRate = if (id == "audio" || id == "video") 48_000 else null,
        audioChannelCount = if (id == "audio" || id == "video") 2 else null,
        videoTrackCount = if (id == "video") 1 else 0,
        audioTrackCount = if (id == "audio" || id == "video") 1 else 0,
        videoBitrate = null,
        videoProfile = null,
        videoLevel = null,
        colorStandard = null,
        colorTransfer = null,
        colorRange = null,
        hdrStaticInfoPresent = false,
        fingerprintSha256 = "fp-$id",
        fingerprintAlgorithm = "test",
        fingerprintStrength = "STRONG_THREE_REGION",
        fingerprintSampledBytes = 1_000L,
        fingerprintNote = null,
        permissionPersisted = false,
        sourceStatus = "AVAILABLE",
        createdAt = 1L
    )

    private fun clip(
        id: String,
        trackId: String,
        assetId: String,
        timelineStartUs: Long,
        sourceStartUs: Long,
        sourceEndUs: Long,
        speed: Double,
        gainDb: Float
    ) = ClipEntity(
        id, PROJECT_ID, trackId, assetId, timelineStartUs, sourceStartUs, sourceEndUs, speed,
        1f, true, gainDb, 250_000, 250_000, 0.5f, 0.5f, 1f, 1f, 0f, false, false, 0f, 0f, 1f, 1f
    )

    private fun keyframe(id: String, owner: String, ownerType: String, property: String, timeUs: Long, value: Float, interpolation: String) =
        KeyframeEntity(id, owner, ownerType, property, timeUs, value, interpolation)

    companion object {
        private const val DB_NAME = "step2-complex-persistence"
        private const val PROJECT_ID = "project"
    }
}
