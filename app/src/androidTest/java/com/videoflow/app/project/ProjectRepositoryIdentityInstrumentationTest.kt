package com.videoflow.app.project

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.media.MediaAnalyzer
import com.videoflow.app.data.media.UriFingerprintService
import com.videoflow.app.data.project.AddMediaResult
import com.videoflow.app.data.project.IdentityMatch
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.test.TestMediaProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ProjectRepositoryIdentityInstrumentationTest {
    private lateinit var context: Context
    private lateinit var db: VideoFlowDatabase
    private lateinit var repository: ProjectRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearFixtures()
        db = Room.inMemoryDatabaseBuilder(context, VideoFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProjectRepository(
            db = db,
            context = context,
            analyzer = MediaAnalyzer(context),
            fingerprinter = UriFingerprintService(context),
            diagnosticLog = LocalDiagnosticLog()
        )
    }

    @After
    fun tearDown() {
        db.close()
        clearFixtures()
    }

    @Test
    fun duplicateCandidateDoesNotWriteUntilExplicitConfirmation() = runBlocking {
        val projectId = repository.createProject("Duplicate safety")
        val uri = TestMediaProvider.uri("sample_av.mp4")

        assertTrue(repository.addMedia(projectId, uri) is AddMediaResult.Added)
        assertEquals(1, repository.getProject(projectId)!!.mediaAssets.size)

        val duplicate = repository.addMedia(projectId, uri)
        assertTrue(duplicate is AddMediaResult.DuplicateCandidate)
        assertEquals("Duplicate candidate must not exist in Room before confirmation", 1, repository.getProject(projectId)!!.mediaAssets.size)

        repository.confirmAddDuplicate((duplicate as AddMediaResult.DuplicateCandidate).candidate)
        assertEquals(2, repository.getProject(projectId)!!.mediaAssets.size)
    }

    @Test
    fun duplicateCancelLeavesRoomUnchanged() = runBlocking {
        val projectId = repository.createProject("Duplicate cancel")
        val uri = TestMediaProvider.uri("sample_av.mp4")
        repository.addMedia(projectId, uri)
        val duplicate = repository.addMedia(projectId, uri)
        assertTrue(duplicate is AddMediaResult.DuplicateCandidate)

        // Cancellation intentionally performs no repository write.
        assertEquals(1, repository.getProject(projectId)!!.mediaAssets.size)
    }

    @Test
    fun sameContentUriWithDifferentUnderlyingMediaBecomesChanged() = runBlocking {
        val projectId = repository.createProject("Source identity")
        val uri = TestMediaProvider.uri("sample_av.mp4")
        repository.addMedia(projectId, uri)
        val saved = repository.getProject(projectId)!!.mediaAssets.single()
        assertEquals(SourceStatus.AVAILABLE, saved.sourceStatus)

        overwriteMaterializedFixture("sample_av.mp4", "sample_video_only.mp4")

        assertEquals(SourceStatus.CHANGED, repository.verifySource(saved))
        assertEquals(SourceStatus.CHANGED, repository.getProject(projectId)!!.mediaAssets.single().sourceStatus)
    }

    @Test
    fun wrongStrongFileIsRejectedByRelinkAndOriginalReferenceRemains() = runBlocking {
        val projectId = repository.createProject("Relink mismatch")
        val originalUri = TestMediaProvider.uri("sample_av.mp4")
        repository.addMedia(projectId, originalUri)
        val saved = repository.getProject(projectId)!!.mediaAssets.single()

        val validation = repository.relink(saved.id, TestMediaProvider.uri("sample_video_only.mp4"))

        assertEquals(IdentityMatch.MISMATCH, validation.match)
        assertEquals(originalUri.toString(), repository.getProject(projectId)!!.mediaAssets.single().sourceUri)
    }

    @Test
    fun unchangedSourceRevalidationReturnsAvailable() = runBlocking {
        val projectId = repository.createProject("Unchanged")
        val uri = TestMediaProvider.uri("sample_av.mp4")
        repository.addMedia(projectId, uri)
        val saved = repository.getProject(projectId)!!.mediaAssets.single()

        assertEquals(SourceStatus.AVAILABLE, repository.verifySource(saved))
    }

    private fun overwriteMaterializedFixture(targetName: String, assetName: String) {
        val target = File(context.cacheDir, "step1-media-fixtures/$targetName")
        check(target.exists())
        context.assets.open(assetName).use { input ->
            target.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
        }
    }

    private fun clearFixtures() {
        File(context.cacheDir, "step1-media-fixtures").deleteRecursively()
    }
}
