package com.nucleusflow

import android.app.Application
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
        ShizukuBridge.init(this)
        scope.launch { Shell.detectRoot() }
    }
}
