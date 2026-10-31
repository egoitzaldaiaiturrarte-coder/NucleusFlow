package com.nucleusflow.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class MemoryStatus(
    val zramEnabled: Boolean,
    val zramDisksize: String,
    val zramCompAlgorithm: String,
    val zramUsedTotal: String,
    val swappiness: Int,
    val vfsCachePressure: Int,
    val dirtyRatio: Int,
    val dirtyBackgroundRatio: Int,
    val swapInfo: String
)

object MemoryManager {
    private val _status = MutableStateFlow(
        MemoryStatus(
            zramEnabled = false,
            zramDisksize = "0",
            zramCompAlgorithm = "unknown",
            zramUsedTotal = "0",
            swappiness = 60,
            vfsCachePressure = 100,
            dirtyRatio = 20,
            dirtyBackgroundRatio = 10,
            swapInfo = ""
        )
    )
    val status: StateFlow<MemoryStatus> = _status.asStateFlow()

    fun scanMemory() {
        val zramPath = "/sys/block/zram0"
        val zramDir = File(zramPath)
        val zramExists = zramDir.exists()

        val disksize = if (zramExists) SystemExecutor.readFile("$zramPath/disksize", "ZRAM Disksize") else "0"
        val compAlgo = if (zramExists) SystemExecutor.readFile("$zramPath/comp_algorithm", "ZRAM CompAlgo") else "unknown"
        val memUsed = if (zramExists) SystemExecutor.readFile("$zramPath/mem_used_total", "ZRAM MemUsed") else "0"

        val swappiness = SystemExecutor.readFile("/proc/sys/vm/swappiness", "VM Swappiness").toIntOrNull() ?: 60
        val vfsPressure = SystemExecutor.readFile("/proc/sys/vm/vfs_cache_pressure", "VM VFS Pressure").toIntOrNull() ?: 100
        val dirtyRatio = SystemExecutor.readFile("/proc/sys/vm/dirty_ratio", "VM Dirty Ratio").toIntOrNull() ?: 20
        val dirtyBg = SystemExecutor.readFile("/proc/sys/vm/dirty_background_ratio", "VM Dirty Background").toIntOrNull() ?: 10

        val swaps = SystemExecutor.readFile("/proc/swaps", "Proc Swaps")

        _status.value = MemoryStatus(
            zramEnabled = zramExists && disksize != "0" && disksize.isNotBlank(),
            zramDisksize = disksize,
            zramCompAlgorithm = compAlgo,
            zramUsedTotal = memUsed,
            swappiness = swappiness,
            vfsCachePressure = vfsPressure,
            dirtyRatio = dirtyRatio,
            dirtyBackgroundRatio = dirtyBg,
            swapInfo = swaps
        )
    }

    fun setVmParam(param: String, value: Int): Boolean {
        val path = "/proc/sys/vm/$param"
        val success = SystemExecutor.writeFile(path, value.toString(), "VM param $param")
        if (success) scanMemory()
        return success
    }

    fun compactMemory(): Boolean {
        val path = "/proc/sys/vm/compact_memory"
        val success = SystemExecutor.writeFile(path, "1", "Compactación de Memoria")
        return success
    }

    fun pushToZram(): Boolean {
        // Drop caches & trigger compaction / swapout
        val s1 = SystemExecutor.writeFile("/proc/sys/vm/drop_caches", "3", "Liberar Caches (Drop Caches)")
        val s2 = compactMemory()
        return s1 || s2
    }
}
