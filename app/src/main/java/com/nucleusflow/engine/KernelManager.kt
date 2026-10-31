package com.nucleusflow.engine

import com.nucleusflow.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class CpuCoreInfo(
    val id: Int,
    val path: String,
    val isOnline: Boolean,
    val curFreq: Long,
    val minFreq: Long,
    val maxFreq: Long,
    val availableMinFreqs: List<Long>,
    val availableMaxFreqs: List<Long>,
    val governor: String,
    val availableGovernors: List<String>,
    val isWritable: Boolean
)

data class CpuCluster(
    val clusterId: Int,
    val coreIds: List<Int>,
    val governor: String,
    val minFreq: Long,
    val maxFreq: Long,
    val availableGovernors: List<String>,
    val availableFreqs: List<Long>
)

object KernelManager {
    private val _cores = MutableStateFlow<List<CpuCoreInfo>>(emptyList())
    val cores: StateFlow<List<CpuCoreInfo>> = _cores.asStateFlow()

    private val _clusters = MutableStateFlow<List<CpuCluster>>(emptyList())
    val clusters: StateFlow<List<CpuCluster>> = _clusters.asStateFlow()

    fun scanCores() {
        val cpuDir = File("/sys/devices/system/cpu")
        if (!cpuDir.exists()) return

        val coreDirs = cpuDir.listFiles { file -> file.name.matches(Regex("cpu[0-9]+")) } ?: return
        val list = mutableListOf<CpuCoreInfo>()

        for (coreDir in coreDirs.sortedBy { it.name }) {
            val name = coreDir.name
            val id = name.removePrefix("cpu").toIntOrNull() ?: continue
            val corePath = coreDir.absolutePath

            val onlineFile = File(coreDir, "online")
            val isOnline = if (onlineFile.exists()) SystemExecutor.readFile(onlineFile.absolutePath, "CPU $id Online").trim() != "0" else true

            val curFreq = SystemExecutor.readFile("$corePath/cpufreq/scaling_cur_freq", "CPU $id CurFreq").toLongOrNull() ?: 0L
            val minFreq = SystemExecutor.readFile("$corePath/cpufreq/scaling_min_freq", "CPU $id MinFreq").toLongOrNull() ?: 0L
            val maxFreq = SystemExecutor.readFile("$corePath/cpufreq/scaling_max_freq", "CPU $id MaxFreq").toLongOrNull() ?: 0L

            val availFreqsStr = SystemExecutor.readFile("$corePath/cpufreq/scaling_available_frequencies", "CPU $id AvailFreqs")
            val availFreqs = availFreqsStr.split(Regex("\\s+"))
                .mapNotNull { it.toLongOrNull() }
                .sorted()

            val gov = SystemExecutor.readFile("$corePath/cpufreq/scaling_governor", "CPU $id Governor")
            val availGovStr = SystemExecutor.readFile("$corePath/cpufreq/scaling_available_governors", "CPU $id AvailGovs")
            val availGovs = availGovStr.split(Regex("\\s+")).filter { it.isNotBlank() }

            val govFile = File(coreDir, "cpufreq/scaling_governor")
            val isWritable = govFile.exists() && govFile.canWrite() || ShizukuManager.isRootOrPrivileged()

            list.add(
                CpuCoreInfo(
                    id = id,
                    path = corePath,
                    isOnline = isOnline,
                    curFreq = curFreq,
                    minFreq = minFreq,
                    maxFreq = maxFreq,
                    availableMinFreqs = availFreqs,
                    availableMaxFreqs = availFreqs,
                    governor = gov,
                    availableGovernors = availGovs,
                    isWritable = isWritable
                )
            )
        }
        _cores.value = list
        buildClusters(list)
    }

    private fun buildClusters(coresList: List<CpuCoreInfo>) {
        if (coresList.isEmpty()) return
        // Group cores by max frequency or similar properties
        val grouped = coresList.groupBy { it.maxFreq }
        val clusterList = mutableListOf<CpuCluster>()
        var clusterId = 0

        for ((maxFreq, cList) in grouped.entries.sortedBy { it.key }) {
            clusterList.add(
                CpuCluster(
                    clusterId = clusterId++,
                    coreIds = cList.map { it.id },
                    governor = cList.firstOrNull()?.governor ?: "unknown",
                    minFreq = cList.firstOrNull()?.minFreq ?: 0L,
                    maxFreq = maxFreq,
                    availableGovernors = cList.firstOrNull()?.availableGovernors ?: emptyList(),
                    availableFreqs = cList.firstOrNull()?.availableMaxFreqs ?: emptyList()
                )
            )
        }
        _clusters.value = clusterList
    }

    fun setGovernor(coreId: Int, governor: String): Boolean {
        val path = "/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_governor"
        val success = SystemExecutor.writeFile(path, governor, "Cambiar Governor CPU $coreId")
        if (success) scanCores()
        return success
    }

    fun setClusterGovernor(clusterId: Int, governor: String): Boolean {
        val cluster = _clusters.value.find { it.clusterId == clusterId } ?: return false
        var allSuccess = true
        for (coreId in cluster.coreIds) {
            if (!setGovernor(coreId, governor)) {
                allSuccess = false
            }
        }
        return allSuccess
    }

    fun setFrequencyRange(coreId: Int, minFreq: Long, maxFreq: Long): Boolean {
        val pathMin = "/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_min_freq"
        val pathMax = "/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_max_freq"
        val s1 = SystemExecutor.writeFile(pathMin, minFreq.toString(), "CPU $coreId MinFreq")
        val s2 = SystemExecutor.writeFile(pathMax, maxFreq.toString(), "CPU $coreId MaxFreq")
        scanCores()
        return s1 && s2
    }
}
