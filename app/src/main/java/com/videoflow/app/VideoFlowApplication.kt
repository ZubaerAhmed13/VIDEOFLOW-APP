package com.videoflow.app

import android.app.Application
import android.os.StrictMode
import com.videoflow.app.data.export.ExportRepository
import com.videoflow.app.export.ExportProcessIdentity
import com.videoflow.app.export.ExportProcessRecoveryManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltAndroidApp
class VideoFlowApplication : Application() {
    @Inject lateinit var exportRepository: ExportRepository
    @Inject lateinit var exportRecoveryManager: ExportProcessRecoveryManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processStartedAt = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        // Hilt/Application is instantiated in both the editor and :export processes. Only the main
        // process may classify jobs from a previous app lifetime as interrupted; doing this in
        // :export would immediately invalidate the job that caused the process to start.
        if (ExportProcessIdentity.isMainProcess(this)) {
            appScope.launch {
                exportRepository.markInterruptedAfterProcessRestart(processStartedAt, this@VideoFlowApplication)
                while (isActive) {
                    exportRecoveryManager.reconcile()
                    delay(5_000L)
                }
            }
        }
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
