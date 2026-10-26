package com.nucleusflow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CoreInfo(
    val index: Int,
    val curFreq: Long,
    val minFreq: Long,
    val maxFreq: Long,
    val hwMax: Long,
    val governor: String,
    val governors: List<String>,
    val frequencies: List<Long>,
    val path: String
)

data class ZramInfo(val name: String, val disksize: Long, val used: Long, val algorithm: String)

data class MemInfo(
    val totalKb: Long = 0,
    val freeKb: Long = 0,
    val availableKb: Long = 0,
    val cachedKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapFreeKb: Long = 0,
    val zram: List<ZramInfo> = emptyList(),
    val swappiness: Int = -1,
    val load: String = "",
    val uptime: Long = 0,
    val raw: String = ""
) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0)
    val usedPct: Int get() = if (totalKb > 0) ((usedKb * 100) / totalKb).toInt() else 0
}

data class ProcInfo(
    val pid: Int,
    val oomAdj: Int,
    val cpus: String,
    val state: String,
    val name: String
)

object SysProbe {

    private val CORES_SCRIPT = """
for d in /sys/devices/system/cpu/cpu[0-9]*; do
  [ -d "${'$'}d/cpufreq" ] || continue
  c=${'$'}{d##*/cpu}
  printf 'CORE\t%s\t%s\t%s\t%s\t%s\t%s\n' "${'$'}c" "$(cat ${'$'}d/cpufreq/scaling_cur_freq 2>/dev/null)" "$(cat ${'$'}d/cpufreq/scaling_min_freq 2>/dev/null)" "$(cat ${'$'}d/cpufreq/scaling_max_freq 2>/dev/null)" "$(cat ${'$'}d/cpufreq/cpuinfo_max_freq 2>/dev/null)" "$(cat ${'$'}d/cpufreq/scaling_governor 2>/dev/null)"
  printf 'GOVS\t%s\t%s\n' "${'$'}c" "$(cat ${'$'}d/cpufreq/scaling_available_governors 2>/dev/null)"
  printf 'FREQS\t%s\t%s\n' "${'$'}c" "$(cat ${'$'}d/cpufreq/scaling_available_frequencies 2>/dev/null)"
done
for d in /sys/devices/system/cpu/cpufreq/policy[0-9]*; do
  [ -d "${'$'}d" ] || continue
  c=${'$'}{d##*policy}
  printf 'CORE\t%s\t%s\t%s\t%s\t%s\t%s\n' "${'$'}c" "$(cat ${'$'}d/scaling_cur_freq 2>/dev/null)" "$(cat ${'$'}d/scaling_min_freq 2>/dev/null)" "$(cat ${'$'}d/scaling_max_freq 2>/dev/null)" "$(cat ${'$'}d/cpuinfo_max_freq 2>/dev/null)" "$(cat ${'$'}d/scaling_governor 2>/dev/null)"
  printf 'GOVS\t%s\t%s\n' "${'$'}c" "$(cat ${'$'}d/scaling_available_governors 2>/dev/null)"
  printf 'FREQS\t%s\t%s\n' "${'$'}c" "$(cat ${'$'}d/scaling_available_frequencies 2>/dev/null)"
done
printf 'PRESENT\t%s\n' "$(cat /sys/devices/system/cpu/present 2>/dev/null)"
""".trimIndent()

    private val MEM_SCRIPT = """
echo '@@MEMINFO'
cat /proc/meminfo 2>/dev/null
echo '@@SWAPS'
cat /proc/swaps 2>/dev/null
echo '@@ZRAM'
for z in /sys/block/zram*; do
  [ -d "${'$'}z" ] || continue
  printf 'ZRAM\t%s\t%s\t%s\t%s\n' "${'$'}{z##*/}" "$(cat ${'$'}z/disksize 2>/dev/null)" "$(cat ${'$'}z/mem_used_total 2>/dev/null)" "$(cat ${'$'}z/comp_algorithm 2>/dev/null)"
done
echo '@@VM'
cat /proc/sys/vm/swappiness 2>/dev/null
echo '@@LOAD'
cat /proc/loadavg 2>/dev/null
echo '@@UPTIME'
cat /proc/uptime 2>/dev/null
""".trimIndent()

    private val PROC_SCRIPT = """
for p in /proc/[0-9]*; do
  pid=${'$'}{p##*/}
  [ -r "${'$'}p/cmdline" ] || continue
  name=$(tr '\0' ' ' < "${'$'}p/cmdline" 2>/dev/null)
  [ -z "${'$'}name" ] && name=$(cat "${'$'}p/comm" 2>/dev/null)
  oom=$(cat "${'$'}p/oom_score_adj" 2>/dev/null)
  cpus=$(grep -m1 '^Cpus_allowed_list:' "${'$'}p/status" 2>/dev/null | cut -f2)
  state=$(awk '{print ${'$'}3}' "${'$'}p/stat" 2>/dev/null)
  printf 'P\t%s\t%s\t%s\t%s\t%s\n' "${'$'}pid" "${'$'}oom" "${'$'}cpus" "${'$'}state" "${'$'}name"
done
""".trimIndent()

    suspend fun cores(): List<CoreInfo> = withContext(Dispatchers.IO) {
        val out = Shell.execBlocking(CORES_SCRIPT)
        val govMap = HashMap<Int, List<String>>()
        val freqMap = HashMap<Int, List<Long>>()
        val cores = ArrayList<CoreInfo>()
        val seen = HashSet<Int>()
        for (line in out.lineSequence()) {
            val parts = line.trim().split('\t')
            if (parts.size < 2) continue
            when (parts[0]) {
                "GOVS" -> {
                    val idx = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val list = parts.getOrNull(2).orEmpty().trim().split(' ').filter { it.isNotBlank() }
                    govMap[idx] = list
                }
                "FREQS" -> {
                    val idx = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val list = parts.getOrNull(2).orEmpty().trim().split(' ')
                        .mapNotNull { it.toLongOrNull() }
                    if (list.isNotEmpty()) freqMap[idx] = list
                }
                "CORE" -> {
                    val idx = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    if (!seen.add(idx)) continue
                    val cur = parts.getOrNull(2)?.toLongOrNull() ?: 0
                    val min = parts.getOrNull(3)?.toLongOrNull() ?: 0
                    val max = parts.getOrNull(4)?.toLongOrNull() ?: 0
                    val hwMax = parts.getOrNull(5)?.toLongOrNull() ?: max
                    val gov = parts.getOrNull(6).orEmpty().trim()
                    cores.add(
                        CoreInfo(
                            index = idx,
                            curFreq = cur,
                            minFreq = min,
                            maxFreq = max,
                            hwMax = hwMax,
                            governor = gov,
                            governors = emptyList(),
                            frequencies = emptyList(),
                            path = "/sys/devices/system/cpu/cpu$idx/cpufreq"
                        )
                    )
                }
            }
        }
        cores.sortedBy { it.index }.map { c ->
            val govs = if (!govMap.containsKey(c.index)) {
                runCatching {
                    Shell.execBlocking(
                        "cat /sys/devices/system/cpu/cpufreq/policy${c.index}/scaling_available_governors 2>/dev/null"
                    ).trim().split(' ').filter { it.isNotBlank() }
                }.getOrDefault(emptyList())
            } else govMap[c.index] ?: emptyList()
            c.copy(governors = govs, frequencies = freqMap[c.index] ?: emptyList())
        }
    }

    suspend fun memory(): MemInfo = withContext(Dispatchers.IO) {
        val out = Shell.execBlocking(MEM_SCRIPT)
        var total = 0L; var free = 0L; var avail = 0L; var cached = 0L
        var swapTotal = 0L; var swapFree = 0L
        var swappiness = -1
        var load = ""
        var uptime = 0L
        val zram = ArrayList<ZramInfo>()
        var section = ""
        for (line in out.lineSequence()) {
            val t = line.trim()
            when {
                t == "@@MEMINFO" -> section = "mem"
                t == "@@SWAPS" -> section = "swaps"
                t == "@@ZRAM" -> section = "zram"
                t == "@@VM" -> section = "vm"
                t == "@@LOAD" -> section = "load"
                t == "@@UPTIME" -> section = "uptime"
                t.startsWith("ZRAM\t") -> {
                    val p = t.split('\t')
                    zram.add(ZramInfo(p[1], p.getOrNull(2)?.toLongOrNull() ?: 0, p.getOrNull(3)?.toLongOrNull() ?: 0, p.getOrNull(4).orEmpty()))
                }
                section == "mem" -> {
                    val key = t.substringBefore(':').trim()
                    val value = t.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0
                    when (key) {
                        "MemTotal" -> total = value
                        "MemFree" -> free = value
                        "MemAvailable" -> avail = value
                        "Cached" -> cached = value
                        "SwapTotal" -> swapTotal = value
                        "SwapFree" -> swapFree = value
                    }
                }
                section == "swaps" -> {
                    if (t.isNotEmpty() && !t.startsWith("Filename")) {
                        val p = t.split(Regex("\\s+"))
                        if (p.size >= 4) {
                            val size = (p[2].toLongOrNull() ?: 0) / 1024
                            val used = (p[3].toLongOrNull() ?: 0) / 1024
                            if (swapTotal == 0L) swapTotal = size
                            if (swapFree == 0L) swapFree = (size - used).coerceAtLeast(0)
                        }
                    }
                }
                section == "vm" -> swappiness = t.toIntOrNull() ?: -1
                section == "load" -> if (t.contains(' ')) load = t
                section == "uptime" -> uptime = t.substringBefore('.').toLongOrNull() ?: 0
            }
        }
        if (swapTotal == 0L && zram.isNotEmpty()) {
            val dz = zram.sumOf { it.disksize } / 1024
            swapTotal = dz
        }
        MemInfo(
            totalKb = total,
            freeKb = free,
            availableKb = avail,
            cachedKb = cached,
            swapTotalKb = swapTotal,
            swapFreeKb = swapFree,
            zram = zram,
            swappiness = swappiness,
            load = load,
            uptime = uptime,
            raw = out
        )
    }

    suspend fun processes(): List<ProcInfo> = withContext(Dispatchers.IO) {
        val out = Shell.execBlocking(PROC_SCRIPT)
        val list = ArrayList<ProcInfo>()
        for (line in out.lineSequence()) {
            if (!line.startsWith("P\t")) continue
            val p = line.split('\t')
            if (p.size < 6) continue
            val pid = p[1].toIntOrNull() ?: continue
            val name = p[5].trim().ifEmpty { "?" }
            list.add(ProcInfo(pid, p[2].toIntOrNull() ?: 0, p[3].ifEmpty { "?" }, p[4].ifEmpty { "?" }, name))
            if (list.size >= 600) break
        }
        list.sortedWith(compareByDescending<ProcInfo> { it.oomAdj }.thenBy { it.pid })
    }

    suspend fun packages(userOnly: Boolean = true): List<String> = withContext(Dispatchers.IO) {
        val flag = if (userOnly) "-3" else ""
        val out = Shell.execBlocking("pm list packages $flag 2>/dev/null")
        out.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .sorted()
            .toList()
    }

    suspend fun environment(): String = withContext(Dispatchers.IO) {
        Shell.execBlocking(
            """
echo '== ID =='; id 2>&1
echo '== KERNEL =='; uname -a 2>&1
echo '== ANDROID =='; getprop ro.build.version.release 2>&1; getprop ro.product.model 2>&1
echo '== PATHS =='
for f in /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor /proc/sys/vm/swappiness /proc/sys/vm/compact_memory /proc/sys/vm/drop_caches /sys/block/zram0/disksize; do
  if [ -e "${'$'}f" ]; then
    if [ -w "${'$'}f" ]; then echo "W ${'$'}f"; else echo "R ${'$'}f"; fi
  else
    echo "- ${'$'}f"
  fi
done
""".trimIndent()
        )
    }

    fun formatHz(hz: Long): String {
        if (hz <= 0) return "—"
        return if (hz >= 1_000_000) String.format("%.2f GHz", hz / 1_000_000.0)
        else String.format("%d MHz", hz / 1000)
    }

    fun formatKb(kb: Long): String {
        if (kb <= 0) return "—"
        val mb = kb / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.0f MB", mb)
    }
}
