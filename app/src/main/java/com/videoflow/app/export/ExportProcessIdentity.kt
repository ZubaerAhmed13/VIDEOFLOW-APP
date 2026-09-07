package com.videoflow.app.export

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

/** Process identity without relying on shared singleton state between :export and the editor. */
object ExportProcessIdentity {
    fun currentProcessName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        val pid = Process.myPid()
        return context.getSystemService(ActivityManager::class.java)
            ?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
    }

    fun isMainProcess(context: Context): Boolean =
        currentProcessName(context) == context.packageName

    fun isExportProcess(context: Context): Boolean =
        currentProcessName(context) == "${context.packageName}:export"
}
