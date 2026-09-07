@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.net.Uri
import android.system.Os
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.ai.watermark.AiModelPackManager
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.db.*
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.data.history.*
import com.videoflow.app.domain.ai.*
import com.videoflow.app.domain.export.*
import com.videoflow.app.render.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class Step5RecoverySecurityTest {
    @Test fun destinationAndDescriptorAliasesNeverTruncateOriginalMedia() = runBlocking {
        val f=Step5MediaFixture();val dir=File(f.context.filesDir,"extracted-audio/${f.id}").apply { mkdirs() }
        try {
            val file=File(dir,"original.mp4");f.instrumentation.context.assets.open("sample_av.mp4").use { input -> file.outputStream().use { input.copyTo(it,64*1024) } }
            val alias=file // Distinct content/file URIs expose the same inode without requiring privileged hard-link creation.
            val source=FileProvider.getUriForFile(f.context,"${f.context.packageName}.derived",file)
            val destination=Uri.fromFile(alias)
            val plan=f.plan(source);val before=hash(file)
            val engine=Media3RenderEngine(f.context,f.ai,AiModelPackManager(f.context))
            for(uri in listOf(source,source.buildUpon().fragment("alias").build(),destination,Uri.fromFile(alias))) {
                assertEquals(ExportDestinationSafety.SOURCE_CONFLICT,ExportDestinationSafety.problem(f.context,plan,uri))
                assertFalse(engine.prepare(plan,OutputDestination(uri,"unsafe.mp4"),f.settings).ready)
                assertTrue(runCatching { SmartCopyEngine(f.context).copy(plan,uri) }.isFailure)
                assertEquals(before,hash(file))
            }
            assertNotNull(ExportDestinationSafety.problem(f.context,plan,Uri.parse("https://example.test/output")))
        } finally { dir.deleteRecursively();f.close() }
    }
    @Test fun concurrentAtomicSidecarUpdatesAcrossRepositoryInstancesDoNotLoseRegions() = runBlocking {
        val f=Step5MediaFixture()
        try {
            coroutineScope { repeat(40) { n -> launch(Dispatchers.IO) {
                AiWatermarkRepository(f.context).upsert(AiWatermarkEffect("region-$n",f.id,f.clip.id,0L,3_600_000_000L,NormalizedRoi(.1f,.1f,.2f,.2f)))
            } } }
            assertEquals(40,AiWatermarkRepository(f.context).load(f.id).size)
            assertTrue(runCatching { f.ai.load("../escape") }.isFailure)
        } finally { f.close() }
    }
    @Test fun failedUndoKeepsHistoryAndStartupOnlyInterruptsEarlierProcessJobs() = runBlocking {
        val f=Step5MediaFixture();val db=Room.inMemoryDatabaseBuilder(f.context,VideoFlowDatabase::class.java).build()
        try {
            db.projectDao().insert(ProjectEntity(f.id,"Recovery",2,1L,1L,null))
            val repository=ExportRepository(db,EditorRepository(db))
            val old=repository.createJob(f.id,f.destination().toString(),"old.mp4",ExportSettings(),10L)
            val queued=repository.createJob(f.id,f.destination().toString(),"queued.mp4",ExportSettings(),20L)
            val new=repository.createJob(f.id,f.destination().toString(),"new.mp4",ExportSettings(),100L)
            repository.updateJob(old.id,ExportJobStatus.RENDERING,.3f)
            assertEquals(2,repository.markInterruptedAfterProcessRestart(100L))
            assertEquals(ExportJobStatus.INTERRUPTED,repository.getJob(old.id)!!.status)
            assertEquals(ExportJobStatus.INTERRUPTED,repository.getJob(queued.id)!!.status)
            assertEquals(ExportJobStatus.QUEUED,repository.getJob(new.id)!!.status)
            val history=EditHistoryService(db,f.ai)
            history.record(ClipHistoryEntry(f.id,"Retryable edit",listOf(f.clip),emptyList()))
            db.close()
            assertTrue(runCatching { history.undo() }.isFailure)
            assertTrue(history.state.value.canUndo)
            assertEquals("Retryable edit",history.state.value.undoLabel)
            assertFalse(history.state.value.canRedo)
        } finally { if(db.isOpen) db.close();f.close() }
    }
    @Test fun deletingProjectCleansRealDerivedMediaAndKeepsOriginalAndUnrelatedCaches()=runBlocking {
        val f=Step5MediaFixture();val db=Room.inMemoryDatabaseBuilder(f.context,VideoFlowDatabase::class.java).build()
        var projectId: String?=null
        val unrelated=File(f.context.cacheDir,"waveforms/unrelated-${f.id}.vfwp").apply { parentFile!!.mkdirs();writeText("retain") }
        try {
            val source=f.source()
            fun originalHash(): String = f.resolver.openInputStream(source)!!.use { input ->
                val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024)
                while(true) { val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            val before=originalHash()
            val projects=com.videoflow.app.data.project.ProjectRepository(db,f.context,com.videoflow.app.data.media.MediaAnalyzer(f.context),
                com.videoflow.app.data.media.UriFingerprintService(f.context),com.videoflow.app.data.diagnostics.LocalDiagnosticLog())
            val id=projects.createProject("Derived lifecycle");projectId=id
            val asset=(projects.addMedia(id,source) as com.videoflow.app.data.project.AddMediaResult.Added).asset
            val editor=EditorRepository(db);val clip=editor.addClip(id,asset.id,0L)
            val audio=com.videoflow.app.data.audio.AudioExtractionService(f.context,db,editor,projects,EditHistoryService(db,f.ai)).extract(id,clip.id,false) {}
            assertNotNull(com.videoflow.app.data.media.ThumbnailService(f.context,db).loadOrGenerate(asset.id))
            assertTrue(com.videoflow.app.data.audio.WaveformService(f.context,db).loadOrGenerate(audio,128).peaks.any { it>0 })
            val derived=File(f.context.filesDir,"extracted-audio/$id");assertTrue(derived.listFiles()!!.isNotEmpty())
            val cacheDirs=listOf("waveforms","step2-thumbnails").map { File(f.context.cacheDir,it) }
            val ids=listOf(asset.id,audio)
            assertTrue(cacheDirs.flatMap { it.listFiles().orEmpty().toList() }.any { file -> ids.any { file.name.startsWith("$it-") } })
            com.videoflow.app.data.project.ProjectDeletionService(db,f.ai).deleteProject(id)
            assertNull(db.projectDao().get(id));assertFalse(derived.exists())
            assertTrue(cacheDirs.flatMap { it.listFiles().orEmpty().toList() }.none { file -> ids.any { file.name.startsWith("$it-") } })
            assertTrue(unrelated.exists());assertEquals(before,originalHash())
        } finally {
            projectId?.let { File(f.context.filesDir,"extracted-audio/$it").deleteRecursively() }
            unrelated.delete();db.close();f.close()
        }
    }
    private fun hash(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024)
        file.inputStream().use { input -> while(true) { val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
