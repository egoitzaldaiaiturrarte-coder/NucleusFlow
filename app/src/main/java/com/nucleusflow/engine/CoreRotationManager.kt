package com.nucleusflow.engine

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Rotacion adaptativa de los 8 nucleos (5 + 3).
 *
 * - Grupo A: los 5 nucleos que originalmente tienen la frecuencia minima mas baja
 *   (tipicamente 300 MHz). Su minimo se eleva a 1200 MHz (o el valor soportado mas
 *   cercano por encima).
 * - Grupo B: los 3 nucleos restantes. Conservan sus valores originales salvo cuando
 *   les toca el turno de boost.
 * - Turno de boost: recorre primero los 5 del Grupo A y luego los 3 del Grupo B,
 *   en bucle continuo. El nucleo en turno se fija a la frecuencia mas alta soportada
 *   sin superar 2200 MHz ("lo que pueda").
 *
 * El estado se replica en las 4 particiones compartidas para que ningun agente
 * pierda informacion durante el relevo.
 */
object CoreRotationManager {
    private const val TAG = "CoreRotationManager"

    const val BOOST_FREQ_KHZ = 2_200_000L
    const val BASE_MIN_FREQ_KHZ = 1_200_000L
    const val DEFAULT_DWELL_MS = 12_000L
    const val MIN_DWELL_MS = 3_000L
    const val MAX_DWELL_MS = 60_000L

    private const val LOW_FLOOR_MAX_KHZ = 400_000L
    private const val GROUP_A_SIZE = 5

    private data class CoreSnapshot(
        val id: Int,
        val origMin: Long,
        val origMax: Long,
        val availFreqs: List<Long>
    )

    private val _state = MutableStateFlow(CoreRotationState())
    val state: StateFlow<CoreRotationState> = _state.asStateFlow()

    private val lock = Any()
    private var rotationJob: Job? = null
    private var lastSnapshot: List<CoreSnapshot> = emptyList()

    @Synchronized
    fun start(dwellMs: Long = DEFAULT_DWELL_MS): Boolean {
        if (_state.value.isRunning) return true

        KernelManager.scanCores()
        val cores = KernelManager.cores.value
        if (cores.isEmpty()) {
            _state.value = _state.value.copy(lastError = "Sin nucleos detectados")
            logAndShare("ROTATION_ERROR sin nucleos detectados")
            return false
        }

        val snapshots = cores
            .map { CoreSnapshot(it.id, it.minFreq, it.maxFreq, it.availableMaxFreqs) }
            .sortedBy { it.id }
        val (groupA, groupB) = classify(snapshots)
        if (groupA.isEmpty()) {
            _state.value = _state.value.copy(lastError = "No se pudo clasificar el Grupo A")
            logAndShare("ROTATION_ERROR clasificacion fallida")
            return false
        }

        val dwell = dwellMs.coerceIn(MIN_DWELL_MS, MAX_DWELL_MS)
        val byId = snapshots.associateBy { it.id }
        val sequence = (groupA + groupB).mapNotNull { byId[it] }
        if (sequence.isEmpty()) {
            _state.value = _state.value.copy(lastError = "Secuencia de rotacion vacia")
            logAndShare("ROTATION_ERROR secuencia vacia")
            return false
        }
        lastSnapshot = snapshots
        _state.value = CoreRotationState(
            isRunning = true,
            dwellMs = dwell,
            groupA = groupA,
            groupB = groupB,
            boostCore = -1,
            baseMinFreqKhz = BASE_MIN_FREQ_KHZ,
            boostFreqKhz = BOOST_FREQ_KHZ,
            slot = 0,
            cyclesCompleted = 0,
            lastError = null
        )
        logAndShare(
            "ROTATION_START dwellMs=$dwell groupA=${groupA.joinToString(",")} " +
                "groupB=${groupB.joinToString(",")}"
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        rotationJob = scope.launch {
            runLoop(sequence, groupA.toSet(), dwell)
        }
        return true
    }

    @Synchronized
    fun stop(): Boolean {
        val job = rotationJob
        rotationJob = null
        job?.cancel()

        val snaps = lastSnapshot
        var restored = 0
        SystemExecutor.quiet {
            snaps.forEach { snap ->
                if (writeFreq(snap.id, snap.origMin, snap.origMax)) restored++
            }
        }
        _state.value = _state.value.copy(isRunning = false, boostCore = -1, slot = 0)
        logAndShare("ROTATION_STOP restored=$restored/${snaps.size}")
        KernelManager.scanCores()
        return true
    }

    fun status(): String {
        val s = _state.value
        val sb = StringBuilder()
        sb.append("ROTATION=").append(if (s.isRunning) "RUNNING" else "STOPPED").append('\n')
        sb.append("DWELL_MS=").append(s.dwellMs).append('\n')
        sb.append("GROUP_A=").append(s.groupA.joinToString(",")).append('\n')
        sb.append("GROUP_B=").append(s.groupB.joinToString(",")).append('\n')
        sb.append("BOOST_CORE=").append(s.boostCore).append('\n')
        sb.append("SLOT=").append(s.slot).append(" CYCLES=").append(s.cyclesCompleted).append('\n')
        sb.append("BASE_MIN_KHZ=").append(s.baseMinFreqKhz)
            .append(" BOOST_KHZ=").append(s.boostFreqKhz).append('\n')
        s.lastError?.let { sb.append("LAST_ERROR=").append(it).append('\n') }
        return sb.toString()
    }

    private suspend fun runLoop(
        sequence: List<CoreSnapshot>,
        groupA: Set<Int>,
        dwell: Long
    ) {
        var slot = 0
        var cycles = 0
        try {
            while (currentCoroutineIsActive()) {
                val boost = sequence[slot]
                applySlot(sequence, groupA, boost)

                _state.value = _state.value.copy(
                    isRunning = true,
                    boostCore = boost.id,
                    slot = slot,
                    cyclesCompleted = cycles,
                    lastError = null
                )
                val boostKhz = boostFreqOf(boost)
                // Distribuye los eventos de turno entre las 4 particiones.
                push(slot % SharedPartitionManager.PARTITION_COUNT,
                    "ROTATION_SLOT slot=$slot boostCore=${boost.id} " +
                        "boostKhz=$boostKhz groupA=${groupA.joinToString(",")}")

                delay(dwell)

                slot++
                if (slot >= sequence.size) {
                    slot = 0
                    cycles++
                    logAndShare("ROTATION_CYCLE completed=$cycles")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Fallo en el bucle de rotacion", e)
            _state.value = _state.value.copy(
                isRunning = false,
                lastError = e.message ?: e.javaClass.simpleName
            )
            logAndShare("ROTATION_ERROR ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun applySlot(
        snapshots: List<CoreSnapshot>,
        groupA: Set<Int>,
        boost: CoreSnapshot
    ) {
        val boostFreq = boostFreqOf(boost)
        var applied = 0
        SystemExecutor.quiet {
        snapshots.forEach { snap ->
            val ok = when {
                snap.id == boost.id -> writeFreq(snap.id, boostFreq, boostFreq)
                snap.id in groupA -> {
                    val base = baseMinFreqOf(snap)
                    writeFreq(snap.id, base, snap.origMax.coerceAtLeast(base))
                }
                else -> writeFreq(snap.id, snap.origMin, snap.origMax)
            }
            if (ok) applied++
        }
        }
        if (applied == 0) {
            _state.value = _state.value.copy(
                lastError = "Sin permisos de escritura cpufreq (Shizuku/Root?)"
            )
        }
    }

    private fun classify(cores: List<CoreSnapshot>): Pair<List<Int>, List<Int>> {
        if (cores.size <= GROUP_A_SIZE) {
            return cores.map { it.id } to emptyList()
        }

        val lowFloor = cores.filter { it.origMin in 1..LOW_FLOOR_MAX_KHZ }
        val groupCores: List<CoreSnapshot> = when {
            lowFloor.size == GROUP_A_SIZE -> lowFloor
            lowFloor.size > GROUP_A_SIZE -> lowFloor.sortedBy { it.origMax }.take(GROUP_A_SIZE)
            else -> {
                val lowIds = lowFloor.map { it.id }.toSet()
                val rest = cores.filter { it.id !in lowIds }.sortedBy { it.origMax }
                (lowFloor.sortedBy { it.origMax } + rest).take(GROUP_A_SIZE)
            }
        }
        val aIds = groupCores.map { it.id }
        val bIds = cores.map { it.id }.filter { it !in aIds }
        return aIds to bIds
    }

    private fun baseMinFreqOf(snap: CoreSnapshot): Long {
        val freqs = snap.availFreqs.filter { it > 0 && it <= snap.origMax }
        if (freqs.isEmpty()) return minOf(BASE_MIN_FREQ_KHZ, snap.origMax)
        return freqs.filter { it >= BASE_MIN_FREQ_KHZ }.minOrNull()
            ?: freqs.maxOrNull()
            ?: minOf(BASE_MIN_FREQ_KHZ, snap.origMax)
    }

    private fun boostFreqOf(snap: CoreSnapshot): Long {
        val freqs = snap.availFreqs.filter { it > 0 }
        if (freqs.isNotEmpty()) {
            freqs.filter { it <= BOOST_FREQ_KHZ }.maxOrNull()?.let { return it }
            return freqs.maxOrNull()!!
        }
        return if (snap.origMax > 0) snap.origMax else BOOST_FREQ_KHZ
    }

    /**
     * Escribe los limites de frecuencia en el orden seguro:
     * primero sube max (siempre valido) y luego min; si falla, intenta el orden
     * inverso (necesario al bajar max por debajo del min actual).
     */
    private fun writeFreq(coreId: Int, minFreq: Long, maxFreq: Long): Boolean {
        val lo = minFreq.coerceAtMost(maxFreq)
        val hi = maxFreq.coerceAtLeast(lo)
        val minPath = "/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_min_freq"
        val maxPath = "/sys/devices/system/cpu/cpu$coreId/cpufreq/scaling_max_freq"

        val raiseMax = SystemExecutor.writeFile(maxPath, hi.toString(), "Rotacion CPU $coreId MaxFreq")
        val setMin = SystemExecutor.writeFile(minPath, lo.toString(), "Rotacion CPU $coreId MinFreq")
        if (raiseMax && setMin) return true

        val lowerMin = SystemExecutor.writeFile(minPath, lo.toString(), "Rotacion CPU $coreId MinFreq")
        val lowerMax = SystemExecutor.writeFile(maxPath, hi.toString(), "Rotacion CPU $coreId MaxFreq")
        return lowerMin && lowerMax
    }

    private fun logAndShare(message: String) {
        Log.i(TAG, message)
        pushAll(message)
    }

    private fun pushAll(message: String) {
        if (!SharedPartitionManager.isReady()) return
        try {
            SharedPartitionManager.writeAll(_state.value.boostCore.coerceAtLeast(0), message)
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo persistir en particiones: ${e.message}")
        }
    }

    private fun push(partition: Int, message: String) {
        if (!SharedPartitionManager.isReady()) return
        try {
            SharedPartitionManager.write(
                partition.coerceIn(0, SharedPartitionManager.PARTITION_COUNT - 1),
                _state.value.boostCore.coerceAtLeast(0),
                message
            )
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo persistir turno en particion: ${e.message}")
        }
    }

    private suspend fun currentCoroutineIsActive(): Boolean =
        kotlin.coroutines.coroutineContext.isActive
}

data class CoreRotationState(
    val isRunning: Boolean = false,
    val dwellMs: Long = 0L,
    val groupA: List<Int> = emptyList(),
    val groupB: List<Int> = emptyList(),
    val boostCore: Int = -1,
    val baseMinFreqKhz: Long = 0L,
    val boostFreqKhz: Long = 0L,
    val slot: Int = 0,
    val cyclesCompleted: Int = 0,
    val lastError: String? = null
)
