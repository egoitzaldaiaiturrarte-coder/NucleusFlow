package com.nucleusflow

import android.app.Application
import android.util.Log
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
        if (isPrivilegedServiceProcess()) {
            Log.i("NucleusFlow", "skipping app init in service process ${runCatching { java.io.File("/proc/self/cmdline").readText().trim('\u0000') }.getOrNull()}")
            return
        }
        ShizukuBridge.init(this)
        scope.launch { Shell.detectRoot() }
    }

    private fun isPrivilegedServiceProcess(): Boolean {
        val name = runCatching {
            java.io.File("/proc/self/cmdline").readText()
                .trim('\u0000', '\n', ' ')
        }.getOrNull() ?: return false
        return name.isNotEmpty() && name != packageName
    }
}
