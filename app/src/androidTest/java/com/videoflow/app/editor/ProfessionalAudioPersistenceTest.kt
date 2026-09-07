package com.videoflow.app.editor

import android.content.ContentValues
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.audio.AudioExtractionService
import com.videoflow.app.data.audio.WaveformService
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.editor.EditorRepository
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.history.VisualEditsHistoryEntry
import com.videoflow.app.data.media.MediaAnalyzer
import com.videoflow.app.data.media.UriFingerprintService
import com.videoflow.app.data.project.AddMediaResult
import com.videoflow.app.data.project.ProjectRepository
import com.videoflow.app.data.snapshot.SnapshotService
import com.videoflow.app.domain.effects.*
import com.videoflow.app.domain.editor.TrackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfessionalAudioPersistenceTest {
    @Test fun extractWaveformUndoRedoAndReopenWithVisualSnapshot() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="professional-audio-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,VideoFlowDatabase::class.java,name).build()
        val resolver=context.contentResolver
        val source=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,"professional-audio-fixture.mp4")
            put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VideoFlowCertification")
        })!!
        var projectId: String?=null
        val ai=AiWatermarkRepository(context)
        try {
            resolver.openOutputStream(source)!!.use { out -> instrumentation.context.assets.open("sample_av.mp4").use { it.copyTo(out,64*1024) } }
            val projects=ProjectRepository(db,context,MediaAnalyzer(context),UriFingerprintService(context),LocalDiagnosticLog())
            val id=projects.createProject("Professional extraction test"); projectId=id
            val sourceAsset=(projects.addMedia(id,source) as AddMediaResult.Added).asset
            val editor=EditorRepository(db)
            val video=editor.addClip(id,sourceAsset.id,0L)
            val history=EditHistoryService(db,ai)
            val service=AudioExtractionService(context,db,editor,projects,history)
            val audioId=service.extract(id,video.id,false) {}
            val loaded=editor.load(id)
            val audio=loaded.timeline.clips.single { it.assetId==audioId }
            assertEquals(TrackType.AUDIO,loaded.timeline.tracks.single { it.id==audio.trackId }.type)
            assertEquals(video.sourceStartUs,audio.sourceStartUs)
            assertEquals(video.speed,audio.speed,0.0)
            assertEquals(0f,loaded.timeline.clips.single { it.id==video.id }.gainDb,0f)
            val asset=db.mediaAssetDao().get(audioId)!!
            assertEquals(sourceAsset.audioChannelCount,asset.audioChannelCount)
            assertEquals(sourceAsset.audioSampleRate,asset.audioSampleRate)
            assertTrue(asset.permissionPersisted)
            assertTrue(asset.displayName.startsWith("Extracted from"))
            assertEquals(0,asset.videoTrackCount)
            assertTrue(WaveformService(context,db).loadOrGenerate(audioId,128).peaks.any { it>0f })
            history.undo(); assertEquals(1,editor.load(id).timeline.clips.size)
            history.redo(); assertEquals(2,editor.load(id).timeline.clips.size)
            val visual=VisualEdits(listOf(VideoEffectNode("effect",video.id,VisualEffectType.SEPIA,0L,video.timelineDurationUs)),
                mapOf(video.id to EnhanceParameters(mapOf(Adjustment.EXPOSURE to .1f))))
            ai.visualEdits.replace(id,visual)
            history.record(VisualEditsHistoryEntry(id,"Effects and Enhance",VisualEdits(),visual))
            history.undo(); assertEquals(VisualEdits(),ai.visualEdits.load(id))
            history.redo(); assertEquals(visual,ai.visualEdits.load(id))
            val snapshot=SnapshotService(db,ai).create(id,"With extracted audio and effects")
            db.close(); db=Room.databaseBuilder(context,VideoFlowDatabase::class.java,name).build()
            assertEquals(2,EditorRepository(db).load(id).timeline.clips.size)
            assertEquals(visual,AiWatermarkRepository(context).visualEdits.load(id))
            ai.visualEdits.replace(id,VisualEdits()); db.editorDao().deleteClip(audio.id)
            SnapshotService(db,ai).restore(snapshot.id)
            assertEquals(2,EditorRepository(db).load(id).timeline.clips.size)
            assertEquals(visual,ai.visualEdits.load(id))
        } finally {
            projectId?.let { ai.visualEdits.delete(it); File(context.filesDir,"extracted-audio/$it").deleteRecursively() }
            if(db.isOpen) db.close()
            context.deleteDatabase(name); resolver.delete(source,null,null)
        }
    }
}
