package com.nucleusflow.engine

import com.nucleusflow.native.NativeCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ThreadInfo(
    val tid: Int,
    val name: String,
    val affinityMask: Long
)

data class ProcessInfo(
    val pid: Int,
    val name: String,
    val packageName: String,
    val oomScoreAdj: Int,
    val threads: List<ThreadInfo>,
    val affinityMask: Long
)

object ProcessManager {
    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes: StateFlow<List<ProcessInfo>> = _processes.asStateFlow()

    fun scanProcesses() {
        val procDir = File("/proc")
        if (!procDir.exists()) return

        val pids = procDir.listFiles { file -> file.name.matches(Regex("[0-9]+")) } ?: return
        val list = mutableListOf<ProcessInfo>()

        for (dir in pids) {
            val pidStr = dir.name
            val pid = pidStr.toIntOrNull() ?: continue
            val cmdlineFile = File(dir, "cmdline")
            if (!cmdlineFile.exists()) continue

            val name = try {
                cmdlineFile.readText().trim('\u0000').takeIf { it.isNotBlank() } ?: pidStr
            } catch (e: Throwable) {
                pidStr
            }

            val shortName = name.substringAfterLast('/')

            val oomFile = File(dir, "oom_score_adj")
            val oomScore = try {
                oomFile.readText().trim().toIntOrNull() ?: 0
            } catch (e: Throwable) {
                0
            }

            val procAffinity = NativeCore.getAffinity(pid, 0)

            // Scan threads
            val taskDir = File(dir, "task")
            val threads = mutableListOf<ThreadInfo>()
            if (taskDir.exists()) {
                val tDirs = taskDir.listFiles { file -> file.name.matches(Regex("[0-9]+")) }
                if (tDirs != null) {
                    for (tDir in tDirs) {
                        val tid = tDir.name.toIntOrNull() ?: continue
                        val tComm = File(tDir, "comm").let { if (it.exists()) it.readText().trim() else "thread_$tid" }
                        val tAff = NativeCore.getAffinity(pid, tid)
                        threads.add(ThreadInfo(tid = tid, name = tComm, affinityMask = tAff))
                    }
                }
            }

            list.add(
                ProcessInfo(
                    pid = pid,
                    name = shortName,
                    packageName = name,
                    oomScoreAdj = oomScore,
                    threads = threads,
                    affinityMask = procAffinity
                )
            )
        }
        _processes.value = list.sortedBy { it.name }
    }

    fun setProcessAffinity(pid: Int, mask: Long): Boolean {
        val success = NativeCore.setAffinity(pid, 0, mask)
        if (!success) {
            // Fallback to taskset via shell
            val resp = SystemExecutor.executeCommand("taskset -p 0x${mask.toString(16)} $pid", "taskset PID $pid")
            return !resp.startsWith("ERR")
        }
        scanProcesses()
        return success
    }

    fun setThreadAffinity(pid: Int, tid: Int, mask: Long): Boolean {
        val success = NativeCore.setAffinity(pid, tid, mask)
        if (!success) {
            val resp = SystemExecutor.executeCommand("taskset -p 0x${mask.toString(16)} $tid", "taskset TID $tid")
            return !resp.startsWith("ERR")
        }
        scanProcesses()
        return success
    }

    fun setOomScoreAdj(pid: Int, score: Int): Boolean {
        val path = "/proc/$pid/oom_score_adj"
        val success = SystemExecutor.writeFile(path, score.toString(), "OOM Score PID $pid")
        scanProcesses()
        return success
    }
}
