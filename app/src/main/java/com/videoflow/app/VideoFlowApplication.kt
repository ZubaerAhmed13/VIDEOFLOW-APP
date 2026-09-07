package com.videoflow.app

import android.app.Application
import android.os.StrictMode
import com.videoflow.app.data.export.ExportRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VideoFlowApplication : Application() {
    @Inject lateinit var exportRepository: ExportRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processStartedAt=System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            exportRepository.markInterruptedAfterProcessRestart(processStartedAt,this@VideoFlowApplication)
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
