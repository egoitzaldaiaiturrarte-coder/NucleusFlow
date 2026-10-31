package com.nucleusflow.engine

import com.nucleusflow.native.NativeCore
import com.nucleusflow.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagResult(
    val testName: String,
    val success: Boolean,
    val details: String
)

object DiagManager {
    private val _results = MutableStateFlow<List<DiagResult>>(emptyList())
    val results: StateFlow<List<DiagResult>> = _results.asStateFlow()

    fun runDiagnostics() {
        val list = mutableListOf<DiagResult>()

        // 1. Native Library Load
        try {
            val testAff = NativeCore.getAffinity(android.os.Process.myPid(), 0)
            list.add(DiagResult("Librería Nativa NDK (nucleusflow)", true, "Cargada correctamente. Affinity test PID: $testAff"))
        } catch (e: Throwable) {
            list.add(DiagResult("Librería Nativa NDK (nucleusflow)", false, "Error: ${e.message}"))
        }

        // 2. Shizuku / Root Status
        val mode = ShizukuManager.connectionMode.value
        val privileged = ShizukuManager.isRootOrPrivileged()
        list.add(DiagResult("Modo de Privilegio", privileged, "Modo actual: $mode"))

        // 3. Kernel Read Test
        val cpuModel = SystemExecutor.readFile("/proc/cpuinfo", "Diag CPUInfo").lineSequence().firstOrNull() ?: "No disponible"
        list.add(DiagResult("Lectura /proc/cpuinfo", cpuModel.isNotBlank(), cpuModel))

        // 4. Test Sysfs Write / Read
        val testWrite = SystemExecutor.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "Diag Governor")
        list.add(DiagResult("Acceso Sysfs CPU Governor", testWrite.isNotBlank(), "Governor actual CPU0: $testWrite"))

        _results.value = list
    }
}
