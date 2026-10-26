package com.nucleusflow

import android.app.Application
import android.os.Build
import com.nucleusflow.core.Shell
import com.nucleusflow.shizuku.ShizukuBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NucleusApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (isPrivilegedServiceProcess()) return
        ShizukuBridge.init(this)
        scope.launch { Shell.detectRoot() }
    }

    private fun isPrivilegedServiceProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { processName }.getOrNull()
        } else {
            runCatching {
                java.io.File("/proc/self/cmdline").readText().trim('\u0000', '\n')
            }.getOrNull()
        } ?: return false
        return name != packageName
    }
}
