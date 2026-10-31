package com.nucleusflow.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppProfile(
    val packageName: String,
    val appName: String,
    val governor: String,
    val affinityMask: Long,
    val oomScoreAdj: Int,
    val isEnabled: Boolean
)

object PerAppManager {
    private val _profiles = MutableStateFlow<Map<String, AppProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, AppProfile>> = _profiles.asStateFlow()

    private val _currentForegroundApp = MutableStateFlow("Ninguna")
    val currentForegroundApp: StateFlow<String> = _currentForegroundApp.asStateFlow()

    fun saveProfile(profile: AppProfile) {
        val current = _profiles.value.toMutableMap()
        current[profile.packageName] = profile
        _profiles.value = current
    }

    fun removeProfile(packageName: String) {
        val current = _profiles.value.toMutableMap()
        current.remove(packageName)
        _profiles.value = current
    }

    fun updateForegroundApp(packageName: String) {
        if (_currentForegroundApp.value != packageName) {
            _currentForegroundApp.value = packageName
            applyProfileForApp(packageName)
        }
    }

    private fun applyProfileForApp(packageName: String) {
        val profile = _profiles.value[packageName] ?: return
        if (!profile.isEnabled) return

        if (profile.governor.isNotBlank() && profile.governor != "default") {
            // Apply governor to all clusters/cores
            KernelManager.clusters.value.forEach { cluster ->
                KernelManager.setClusterGovernor(cluster.clusterId, profile.governor)
            }
        }

        if (profile.affinityMask != 0L) {
            // Apply affinity to matching processes
            ProcessManager.processes.value.filter { it.packageName.contains(packageName) }.forEach { proc ->
                ProcessManager.setProcessAffinity(proc.pid, profile.affinityMask)
            }
        }
    }
}
