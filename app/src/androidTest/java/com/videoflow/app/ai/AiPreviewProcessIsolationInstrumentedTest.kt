package com.videoflow.app.ai

import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.videoflow.app.MainActivity
import com.videoflow.app.ai.watermark.AiPreviewProcessClient
import com.videoflow.app.ai.watermark.AiPreviewWorkerDiedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Fatal :ai_preview death must never take the editor/main process with it. */
@RunWith(AndroidJUnit4::class)
class AiPreviewProcessIsolationInstrumentedTest {
    @Test
    fun workerDeathPreservesEditorAndWorkerCanRestart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mainPid = Process.myPid()
        val client = AiPreviewProcessClient(context)

        ActivityScenario.launch(MainActivity::class.java).use {
            val firstWorker = client.pingWorkerProcess()
            assertTrue(firstWorker.endsWith(":ai_preview"))
            assertEquals(mainPid, Process.myPid())

            val failure = runCatching { client.crashWorkerForCertification() }.exceptionOrNull()
            assertTrue(
                "Fatal isolated-worker death must surface as AiPreviewWorkerDiedException, got $failure",
                failure is AiPreviewWorkerDiedException
            )
            assertEquals("Main/editor PID changed after AI worker death", mainPid, Process.myPid())

            // Android may need a short moment to reap the dead Binder/process before a fresh bind.
            delay(300L)
            val restartedWorker = client.pingWorkerProcess()
            assertTrue(restartedWorker.endsWith(":ai_preview"))
            assertEquals("Editor must remain in the original process", mainPid, Process.myPid())
        }

        println("AI_PREVIEW_PROCESS_ISOLATION_CERTIFIED main_pid=$mainPid")
    }
}
