@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.MainActivity
import com.videoflow.app.di.AppModule
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.data.media.*
import com.videoflow.app.data.project.*
import com.videoflow.app.domain.ai.*
import com.videoflow.app.domain.export.*
import com.videoflow.app.export.ExportForegroundService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** CI invokes the phases in separate app processes, with adb force-stop between them. */
@RunWith(AndroidJUnit4::class)
class Step5ProcessDeathTest {
    @Test fun startRealForegroundAiJob() = runBlocking {
        val f=Step5MediaFixture();val db=AppModule.provideDatabase(f.context)
        val prefs=f.context.getSharedPreferences("step5-process-death",0)
        val source=f.source()
        val projects=ProjectRepository(db,f.context,MediaAnalyzer(f.context),UriFingerprintService(f.context),LocalDiagnosticLog())
        val id=projects.createProject("Step 5 process recovery")
        val asset=(projects.addMedia(id,source) as AddMediaResult.Added).asset
        val editor=EditorRepository(db);val clip=editor.addClip(id,asset.id,0L)
        AiWatermarkRepository(f.context).upsert(AiWatermarkEffect("recovery-ai",id,clip.id,0,clip.timelineDurationUs,NormalizedRoi(.1f,.1f,.8f,.8f)))
        val repository=ExportRepository(db,editor)
        val output=f.destination()
        val job=repository.createJob(id,output.toString(),"interrupted.mp4",ExportSettings())
        prefs.edit().putString("project",id).putString("job",job.id).putString("source",source.toString()).putString("output",output.toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            f.instrumentation.runOnMainSync { ExportForegroundService.start(f.context,job.id) }
            withTimeout(120_000L) { while(repository.getJob(job.id)!!.status != ExportJobStatus.RENDERING) {
                val state=repository.getJob(job.id)!!
                check(state.status !in setOf(ExportJobStatus.FAILED,ExportJobStatus.COMPLETED)) { state.failureMessage ?: state.status.name }
                delay(100)
            } }
            f.evidence("process-death.txt","REAL_FOREGROUND_RENDERING job=${job.id}")
        }
        db.close()
    }
    @Test fun restartRecognizesInterruptedJobAndPreservesEditableProject() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("step5-process-death",0)
        val id=checkNotNull(prefs.getString("project",null));val jobId=checkNotNull(prefs.getString("job",null))
        val db=AppModule.provideDatabase(context)
        try {
            val editor=EditorRepository(db);val repository=ExportRepository(db,editor)
            withTimeout(30_000) { while(repository.getJob(jobId)!!.status !in setOf(ExportJobStatus.INTERRUPTED,ExportJobStatus.CANCELLED)) delay(100) }
            assertNotNull(db.projectDao().get(id));assertEquals(1,editor.load(id).timeline.clips.size)
            assertEquals(1,AiWatermarkRepository(context).load(id).size)
            val output=android.net.Uri.parse(prefs.getString("output",null))
            context.contentResolver.openFileDescriptor(output,"r")!!.use { assertEquals(0L,it.statSize) }
            ProjectDeletionService(db,AiWatermarkRepository(context)).deleteProject(id)
            for(key in listOf("source","output")) context.contentResolver.delete(android.net.Uri.parse(prefs.getString(key,null)),null,null)
            prefs.edit().clear().commit()
        } finally { db.close() }
    }
}
