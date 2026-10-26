package com.nucleusflow.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val cores = MutableStateFlow<List<CoreInfo>>(emptyList())
    val memory = MutableStateFlow(MemInfo())
    val processes = MutableStateFlow<List<ProcInfo>>(emptyList())
    val packages = MutableStateFlow<List<String>>(emptyList())
    val environment = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val lastRefresh = MutableStateFlow(0L)
    val lastError = MutableStateFlow("")

    fun refreshAll(includeProcesses: Boolean = true, includePackages: Boolean = false) {
        scope.launch {
            busy.value = true
            runCatching { cores.value = SysProbe.cores() }
                .onFailure { lastError.value = it.message ?: "cores" }
            runCatching { memory.value = SysProbe.memory() }
                .onFailure { lastError.value = it.message ?: "memory" }
            if (includeProcesses) {
                runCatching { processes.value = SysProbe.processes() }
                    .onFailure { lastError.value = it.message ?: "processes" }
            }
            if (includePackages && packages.value.isEmpty()) {
                runCatching { packages.value = SysProbe.packages() }
                    .onFailure { lastError.value = it.message ?: "packages" }
            }
            lastRefresh.value = System.currentTimeMillis()
            busy.value = false
        }
    }

    fun refreshEnvironment() {
        scope.launch {
            runCatching { environment.value = SysProbe.environment() }
                .onFailure { environment.value = "ERR:${it.message}" }
        }
    }

    fun refreshPackages() {
        scope.launch {
            runCatching { packages.value = SysProbe.packages() }
                .onFailure { lastError.value = it.message ?: "packages" }
        }
    }
}
