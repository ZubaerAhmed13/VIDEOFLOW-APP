package com.videoflow.app.ui.effects

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videoflow.app.data.ai.AiWatermarkRepository
import com.videoflow.app.data.audio.AudioExtractionService
import com.videoflow.app.data.history.EditHistoryService
import com.videoflow.app.data.history.VisualEditsHistoryEntry
import com.videoflow.app.domain.effects.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ProfessionalToolState(val draft: VisualEdits = VisualEdits(), val busy: Boolean = false,
    val progress: Float = 0f, val error: String? = null, val loaded: Boolean = false)

@HiltViewModel
class ProfessionalToolsViewModel @Inject constructor(
    private val repository: AiWatermarkRepository, private val history: EditHistoryService,
    private val audio: AudioExtractionService, @ApplicationContext private val context: Context
) : ViewModel() {
    private val mutable = MutableStateFlow(ProfessionalToolState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var before = VisualEdits()
    fun open(projectId: String) {
        task?.cancel()
        mutable.value = ProfessionalToolState(busy = true)
        task = viewModelScope.launch {
            try {
                before = repository.visualEdits.load(projectId)
                mutable.value = ProfessionalToolState(draft = before, loaded = true)
            } catch (e: Exception) { if (e !is CancellationException) mutable.value = ProfessionalToolState(error = e.message) }
        }
    }
    fun draft(value: VisualEdits) { mutable.value = mutable.value.copy(draft = value, error = null) }
    fun cancel() { task?.cancel(); mutable.value = ProfessionalToolState() }
    fun apply(projectId: String, label: String, done: () -> Unit) = work {
        val next = mutable.value.draft
        repository.visualEdits.replace(projectId, next)
        history.touchProject(projectId)
        history.record(VisualEditsHistoryEntry(projectId, label, before, next))
        done()
    }
    fun extract(projectId: String, clipId: String, mute: Boolean, done: () -> Unit) = work {
        audio.extract(projectId, clipId, mute) { mutable.value = mutable.value.copy(progress = it) }
        done()
    }
    fun autoEnhance(clipId: String, uri: String, startUs: Long, endUs: Long) = work {
        val recommended = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            var sum = 0.0; var dark = 0L; var bright = 0L; var count = 0L
            try {
                retriever.setDataSource(context, Uri.parse(uri))
                for (fraction in listOf(.15, .5, .85)) {
                    currentCoroutineContext().ensureActive()
                    val time = startUs + ((endUs-startUs).toDouble()*fraction).toLong()
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= 27) {
                        retriever.getScaledFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST, 128, 128)
                    } else {
                        retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)?.let { original ->
                            val scaled = android.graphics.Bitmap.createScaledBitmap(original,128,128,true)
                            if (scaled !== original) original.recycle()
                            scaled
                        }
                    } ?: error("Could not analyze a representative frame")
                    try {
                        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                            val pixel = bitmap.getPixel(x,y)
                            val l = (.2126*((pixel shr 16) and 255)+.7152*((pixel shr 8) and 255)+.0722*(pixel and 255))/255.0
                            sum += l; count++; if (l < .12) dark++; if (l > .88) bright++
                        }
                    } finally { bitmap.recycle() }
                }
            } finally { retriever.release() }
            require(count > 0)
            AutoEnhanceRecommendation.fromLuminance(sum/count, dark.toDouble()/count, bright.toDouble()/count)
        }
        draft(mutable.value.draft.copy(enhance = mutable.value.draft.enhance + (clipId to recommended)))
    }
    private fun work(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        task = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = null)
            try { block() } catch (e: Exception) { if (e !is CancellationException) mutable.value = mutable.value.copy(error = e.message ?: "Processing failed") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
