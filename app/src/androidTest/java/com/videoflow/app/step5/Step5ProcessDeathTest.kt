@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.videoflow.app.step5

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
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
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** CI invokes the phases in separate app processes, with adb killing only the isolated :export process between them. */
@RunWith(AndroidJUnit4::class)
class Step5ProcessDeathTest {
    @Test fun startRealForegroundAiJob() = runBlocking {
        val f=Step5MediaFixture();val db=AppModule.provideDatabase(f.context)
        val prefs=f.context.getSharedPreferences("step5-process-death",0)
        // The source must satisfy the same durable background-export contract as a real project.
        // Step5MediaFixture inserts through the target app's resolver, so MediaStore records this
        // row as app-owned and ProjectRepository can safely authorize it without weakening SAF rules.
        val source=f.source()
        val projects=ProjectRepository(db,f.context,MediaAnalyzer(f.context),UriFingerprintService(f.context),LocalDiagnosticLog())
        val id=projects.createProject("Step 5 process recovery")
        val asset=(projects.addMedia(id,source) as AddMediaResult.Added).asset
        val editor=EditorRepository(db);val clip=editor.addClip(id,asset.id,0L)
        AiWatermarkRepository(f.context).upsert(AiWatermarkEffect("recovery-ai",id,clip.id,0,clip.timelineDurationUs,NormalizedRoi(.1f,.1f,.8f,.8f)))
        val repository=ExportRepository(db,editor)
        val outputFile=privateFixtureFile(f.context,"output-${System.nanoTime()}.mp4")
        val output=privateFixtureUri(f.context,outputFile)
        // Exercise a real foreground AI export in :export at the fixture's native dimensions.
        // The app-owned MediaStore source proves the durable source-authority path; the private
        // FileProvider output stays deterministic across instrumentation, editor and :export.
        // Direct user-selected SAF/MediaStore muxing remains independently certified by
        // SafMediaMuxerFactoryInstrumentedTest.
        val settings=ExportSettings(resolutionPreset=ExportResolutionPreset.CUSTOM,
            customWidth=320,customHeight=240,videoBitrateOverride=4_000_000,
            audioBitrate=128_000,audioChannels=1)
        val job=repository.createJob(id,output.toString(),"interrupted.mp4",settings)
        prefs.edit()
            .putString("project",id)
            .putString("job",job.id)
            .putString("source",source.toString())
            .putString("output",output.toString())
            .putString("outputPath",outputFile.absolutePath)
            .commit()
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
            val output=Uri.parse(prefs.getString("output",null))
            context.contentResolver.openFileDescriptor(output,"r")!!.use { assertEquals(0L,it.statSize) }
            ProjectDeletionService(db,AiWatermarkRepository(context)).deleteProject(id)
            runCatching { context.contentResolver.delete(Uri.parse(prefs.getString("source",null)),null,null) }
            prefs.getString("outputPath",null)?.let { path -> runCatching { File(path).delete() } }
            prefs.edit().clear().commit()
            Unit
        } finally { db.close() }
    }

    private fun privateFixtureFile(context: Context,name: String): File =
        File(context.filesDir,"ai-jobs/process-death/$name").apply {
            parentFile?.mkdirs()
            if(!exists()) check(createNewFile()) { "Could not create process-death fixture $absolutePath" }
        }

    private fun privateFixtureUri(context: Context,file: File): Uri =
        FileProvider.getUriForFile(context,"${context.packageName}.derived",file)
}
