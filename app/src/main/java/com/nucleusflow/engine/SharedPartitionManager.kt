package com.nucleusflow.engine

import android.content.Context
import android.util.Log
import com.nucleusflow.native.NativeCore
import java.io.File

/**
 * 4 particiones compartidas, activas siempre.
 * - Escritura: las 4 particiones son escribibles y accesibles desde los 8 cores (ids 0-7).
 * - Lectura: lectura unificada de las 4 particiones (merge ordenado por timestamp, sin duplicados).
 * - Activas todo el rato: se inicializan al arrancar la app y no se desactivan.
 */
object SharedPartitionManager {
    private const val TAG = "SharedPartitionManager"

    const val PARTITION_COUNT = 4
    const val CORE_COUNT = 8
    const val ALL_CORES_MASK = 0xFFL
    const val POINTER_PREFIX = "SUPABASE_POINTER"

    private val lock = Any()

    @Volatile
    private var baseDir: File? = null

    @Volatile
    private var supabasePointer: String? = null

    fun init(context: Context): Boolean {
        synchronized(lock) {
            if (baseDir == null) {
                val dir = File(context.filesDir, "nucleusflow_partitions")
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(TAG, "No se pudo crear ${dir.absolutePath}")
                    return false
                }
                baseDir = dir
            }
            for (p in 0 until PARTITION_COUNT) {
                val f = partitionFile(p)
                if (!f.exists() && !f.createNewFile()) {
                    Log.e(TAG, "No se pudo crear ${f.absolutePath}")
                    return false
                }
            }
        }
        ensureAllCoresOnline()
        applyAllCoresAffinity()
        Log.i(TAG, "4 particiones activas en ${baseDir?.absolutePath}")
        return true
    }

    fun isReady(): Boolean = baseDir != null

    fun partitionFile(partition: Int): File {
        val dir = baseDir ?: error("SharedPartitionManager no inicializado")
        require(partition in 0 until PARTITION_COUNT) { "Particion fuera de rango: $partition" }
        return File(dir, "particion_$partition.dat")
    }

    fun write(partition: Int, coreId: Int, text: String): Boolean {
        if (partition !in 0 until PARTITION_COUNT) return false
        val core = coreId.coerceIn(0, CORE_COUNT - 1)
        val record = "${System.currentTimeMillis()}|core=$core|${text.replace("\n", "\\n")}\n"
        return synchronized(lock) {
            try {
                partitionFile(partition).appendText(record)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error escribiendo particion $partition", e)
                false
            }
        }
    }

    fun writeAll(coreId: Int, text: String): Boolean {
        var ok = true
        for (p in 0 until PARTITION_COUNT) {
            if (!write(p, coreId, text)) ok = false
        }
        return ok
    }

    fun readPartition(partition: Int): String =
        if (partition in 0 until PARTITION_COUNT) partitionFile(partition).readText() else ""

    fun readUnified(): String = synchronized(lock) {
        if (baseDir == null) return ""
        data class Row(val ts: Long, val core: String, val text: String)

        val rows = mutableListOf<Row>()
        val seen = HashSet<String>()
        for (p in 0 until PARTITION_COUNT) {
            partitionFile(p).readLines().forEach { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size == 3) {
                    val ts = parts[0].toLongOrNull() ?: 0L
                    val core = parts[1].removePrefix("core=")
                    val text = parts[2]
                    val key = "$ts|$core|$text"
                    if (seen.add(key)) rows.add(Row(ts, core, text))
                }
            }
        }
        rows.sortedBy { it.ts }
            .joinToString("\n") { "${it.ts}|core=${it.core}|${it.text}" }
    }

    fun status(): String {
        if (baseDir == null) return "ERR:PARTITIONS_NOT_READY\n"
        val sb = StringBuilder()
        sb.append("PARTITIONS=$PARTITION_COUNT CORES=$CORE_COUNT AFFINITY=0x")
            .append(ALL_CORES_MASK.toString(16))
            .append('\n')
        for (p in 0 until PARTITION_COUNT) {
            val f = partitionFile(p)
            sb.append("p$p lines=").append(f.readLines().size)
                .append(" bytes=").append(f.length())
                .append('\n')
        }
        val unified = readUnified()
        val unifiedCount = if (unified.isEmpty()) 0 else unified.lines().size
        sb.append("unified_lines=").append(unifiedCount).append('\n')
        return sb.toString()
    }

    /**
     * Politica de memoria: el telefono guarda solo el enlace (puntero) a Supabase.
     * El contenido canonico vive en Supabase; las 4 particiones son el area de trabajo.
     * Esta funcion no borra datos: solo registra/actualiza el puntero en las 4 particiones.
     */
    fun setSupabasePointer(url: String): Boolean {
        if (url.isBlank()) return false
        supabasePointer = url.trim()
        return writeAll(0, "$POINTER_PREFIX|$supabasePointer|${System.currentTimeMillis()}")
    }

    fun getSupabasePointer(): String? {
        supabasePointer?.let { return it }
        if (baseDir == null) return null
        val last = readUnified().lineSequence()
            .lastOrNull { it.contains("|$POINTER_PREFIX|") }
            ?: return null
        val text = last.substringAfter("|core=").substringAfter("|", "")
        return text.removePrefix(POINTER_PREFIX).removePrefix("|").substringBefore("|").ifBlank { null }
    }

    fun ensureAllCoresOnline(): Boolean {
        var ok = true
        for (core in 0 until CORE_COUNT) {
            val path = "/sys/devices/system/cpu/cpu$core/online"
            try {
                val f = File(path)
                if (f.exists()) {
                    val cur = SystemExecutor.readFile(path, "CPU $core online").trim()
                    if (cur == "0" && !SystemExecutor.writeFile(path, "1", "CPU $core online")) ok = false
                }
            } catch (e: Throwable) {
                Log.w(TAG, "No se pudo forzar online CPU $core", e)
                ok = false
            }
        }
        return ok
    }

    fun applyAllCoresAffinity(): Boolean {
        return try {
            NativeCore.setAffinity(android.os.Process.myPid(), 0, ALL_CORES_MASK)
        } catch (e: Throwable) {
            Log.w(TAG, "Afinidad a los 8 cores no disponible", e)
            false
        }
    }
}
