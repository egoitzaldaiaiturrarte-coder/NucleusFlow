package com.nucleusflow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ActionResult(val ok: Boolean, val message: String)

object Actions {

    suspend fun writeFile(path: String, value: String): ActionResult = withContext(Dispatchers.IO) {
        val out = Shell.execBlocking("echo '$value' > '$path' 2>&1")
        val readBack = Shell.execBlocking("cat '$path' 2>/dev/null").trim()
        if (readBack == value || (out.isBlank() && readBack.isNotEmpty())) {
            ActionResult(true, "$path = $readBack")
        } else {
            ActionResult(false, if (out.isBlank()) "No se pudo escribir $path (¿sin privilegios?)" else out.trim())
        }
    }

    suspend fun setGovernor(core: Int, governor: String): ActionResult = withContext(Dispatchers.IO) {
        val perCpu = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor"
        val policy = "/sys/devices/system/cpu/cpufreq/policy$core/scaling_governor"
        val exists = Shell.execBlocking("test -e '$perCpu' && echo yes || echo no").trim()
        val path = if (exists == "yes") perCpu else policy
        writeFile(path, governor)
    }

    suspend fun setFreq(core: Int, minHz: Long?, maxHz: Long?): ActionResult = withContext(Dispatchers.IO) {
        val base = "/sys/devices/system/cpu/cpu$core/cpufreq"
        val policy = "/sys/devices/system/cpu/cpufreq/policy$core"
        val exists = Shell.execBlocking("test -d '$base' && echo yes || echo no").trim()
        val dir = if (exists == "yes") base else policy
        val sb = StringBuilder()
        var ok = true
        if (maxHz != null) {
            val r = writeFile("$dir/scaling_max_freq", maxHz.toString())
            ok = ok && r.ok
            sb.append(r.message).append('\n')
        }
        if (minHz != null) {
            val r = writeFile("$dir/scaling_min_freq", minHz.toString())
            ok = ok && r.ok
            sb.append(r.message).append('\n')
        }
        ActionResult(ok, sb.toString().trim())
    }

    suspend fun setAffinity(pid: Int, mask: Long): ActionResult = withContext(Dispatchers.IO) {
        val hex = java.lang.Long.toHexString(mask)
        val out = Shell.execBlocking("taskset -p $hex $pid 2>&1")
        val check = Shell.execBlocking("grep -m1 '^Cpus_allowed_list:' /proc/$pid/status 2>/dev/null | cut -f2").trim()
        val failed = out.contains("failed", true) || out.contains("Invalid", true) || out.contains("denied", true)
        val ok = check.isNotEmpty() && !failed
        ActionResult(ok, if (ok) "pid $pid → CPUs $check" else out.trim().ifBlank { "Sin cambios en pid $pid" })
    }

    suspend fun setOomAdj(pid: Int, value: Int): ActionResult = withContext(Dispatchers.IO) {
        writeFile("/proc/$pid/oom_score_adj", value.toString())
    }

    suspend fun dropCaches(): ActionResult = withContext(Dispatchers.IO) {
        writeFile("/proc/sys/vm/drop_caches", "3")
    }

    suspend fun compactMemory(): ActionResult = withContext(Dispatchers.IO) {
        writeFile("/proc/sys/vm/compact_memory", "1")
    }

    suspend fun setSwappiness(value: Int): ActionResult = withContext(Dispatchers.IO) {
        writeFile("/proc/sys/vm/swappiness", value.toString())
    }

    suspend fun setZramSize(disk: String, bytes: Long): ActionResult = withContext(Dispatchers.IO) {
        val p = "/sys/block/$disk/disksize"
        val r = writeFile(p, bytes.toString())
        if (r.ok) {
            Shell.execBlocking("swapoff /dev/$disk 2>&1; mkswap /dev/$disk 2>&1; swapon /dev/$disk 2>&1; echo done")
            ActionResult(true, "$disk redimensionado a ${SysProbe.formatKb(bytes / 1024)}")
        } else r
    }

    suspend fun killProcess(pid: Int): ActionResult = withContext(Dispatchers.IO) {
        val out = Shell.execBlocking("kill -9 $pid 2>&1")
        ActionResult(out.isBlank(), if (out.isBlank()) "pid $pid terminado" else out.trim())
    }

    suspend fun runRaw(cmd: String): ActionResult = withContext(Dispatchers.IO) {
        val out = Shell.execBlocking(cmd)
        ActionResult(!out.startsWith("ERR:"), out.trim().ifEmpty { "(sin salida)" })
    }
}
