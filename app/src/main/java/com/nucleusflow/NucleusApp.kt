package com.nucleusflow

import android.app.Application
import com.nucleusflow.engine.AndroidComputeBridge
import com.nucleusflow.shizuku.ShizukuManager

class NucleusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
        AndroidComputeBridge.startComputeNode()
    }
}
