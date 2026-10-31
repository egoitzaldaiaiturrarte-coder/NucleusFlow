package com.nucleusflow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nucleusflow.engine.KernelManager
import com.nucleusflow.engine.MemoryManager
import com.nucleusflow.engine.ProcessManager
import kotlinx.coroutines.*

class ProtectionService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        startEngineLoop()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "nucleus_flow_channel",
            "NucleusFlow Protection Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "nucleus_flow_channel")
            .setContentTitle("NucleusFlow Activo")
            .setContentText("Control adaptativo de núcleos, hilos y memoria en ejecución.")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
    }

    private fun startEngineLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    KernelManager.scanCores()
                    ProcessManager.scanProcesses()
                    MemoryManager.scanMemory()
                } catch (e: Throwable) {
                    // Ignore errors during periodic poll
                }
                delay(5000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
