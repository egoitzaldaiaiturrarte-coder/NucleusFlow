package com.nucleusflow.engine

data class SystemSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val governors: Map<Int, String>,
    val swappiness: Int,
    val vfsPressure: Int
)

object SnapshotManager {
    private var lastSnapshot: SystemSnapshot? = null

    fun createSnapshot(): SystemSnapshot {
        val govMap = mutableMapOf<Int, String>()
        KernelManager.cores.value.forEach { core ->
            govMap[core.id] = core.governor
        }
        val snap = SystemSnapshot(
            governors = govMap,
            swappiness = MemoryManager.status.value.swappiness,
            vfsPressure = MemoryManager.status.value.vfsCachePressure
        )
        lastSnapshot = snap
        return snap
    }

    fun restoreSnapshot(): Boolean {
        val snap = lastSnapshot ?: return false
        var success = true
        snap.governors.forEach { (coreId, gov) ->
            if (!KernelManager.setGovernor(coreId, gov)) success = false
        }
        MemoryManager.setVmParam("swappiness", snap.swappiness)
        MemoryManager.setVmParam("vfs_cache_pressure", snap.vfsPressure)
        return success
    }
}
