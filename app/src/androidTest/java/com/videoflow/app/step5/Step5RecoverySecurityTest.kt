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
    @Test fun destinationAndHardLinkAliasesNeverTruncateOriginalMedia() = runBlocking {
        val f=Step5MediaFixture();val dir=File(f.context.filesDir,"extracted-audio/${f.id}").apply { mkdirs() }
        try {
            val file=File(dir,"original.mp4");f.instrumentation.context.assets.open("sample_av.mp4").use { input -> file.outputStream().use { input.copyTo(it,64*1024) } }
            val alias=File(dir,"alias.mp4");Os.link(file.path,alias.path)
            val source=FileProvider.getUriForFile(f.context,"${f.context.packageName}.derived",file)
            val destination=FileProvider.getUriForFile(f.context,"${f.context.packageName}.derived",alias)
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
    private fun hash(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024)
        file.inputStream().use { input -> while(true) { val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
