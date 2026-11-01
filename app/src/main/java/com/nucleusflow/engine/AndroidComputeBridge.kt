package com.nucleusflow.engine

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AndroidComputeBridge {
    private const val TAG = "AndroidComputeBridge"
    private const val PORT = 8899
    private const val AUTH_PREFIX = "AUTH:"
    private const val MAX_AUTH_FAILURES = 5
    private const val BLOCK_DURATION_MS = 60_000L

    private val _isComputeServerRunning = MutableStateFlow(false)
    val isComputeServerRunning: StateFlow<Boolean> = _isComputeServerRunning.asStateFlow()

    private val _computeLogs = MutableStateFlow<List<String>>(emptyList())
    val computeLogs: StateFlow<List<String>> = _computeLogs.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _listenOnAllInterfaces = MutableStateFlow(false)
    val listenOnAllInterfaces: StateFlow<Boolean> = _listenOnAllInterfaces.asStateFlow()

    fun setListenOnAllInterfaces(listen: Boolean) {
        _listenOnAllInterfaces.value = listen
    }

    private var serverSocket: ServerSocket? = null

    private var failures = 0
    private var blockedUntilMillis = 0L
    private var currentToken: String? = null

    fun startComputeNode(context: Context) {
        if (_isComputeServerRunning.value) return
        if (System.currentTimeMillis() < blockedUntilMillis) {
            log("Nodo bloqueado temporalmente por rate-limit de autenticacion.")
            _isComputeServerRunning.value = false
            return
        }
        try {
            val host = if (_listenOnAllInterfaces.value) "0.0.0.0" else "127.0.0.1"
            serverSocket = ServerSocket(PORT, 50, InetAddress.getByName(host))
            currentToken = getToken(context).also { _authToken.value = it }
            _isComputeServerRunning.value = true
            log("Nodo de procesamiento real Android iniciado en puerto $PORT (bind: $host)")
            Thread {
                while (_isComputeServerRunning.value) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        Log.e(TAG, "Compute socket error", e)
                        break
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start compute server", e)
            _isComputeServerRunning.value = false
        }
    }

    private fun getToken(context: Context): String {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "nucleus_compute_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val existing = prefs.getString("auth_token", null)
        if (!existing.isNullOrBlank()) return existing
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("auth_token", token).apply()
        return token
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = socket.getOutputStream().bufferedWriter()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val response = processLine(line)
                writer.write(response)
                writer.flush()
            }
            writer.close()
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling compute client", e)
        }
    }

    private fun processLine(line: String): String {
        if (System.currentTimeMillis() < blockedUntilMillis) {
            return "ERR:TOO_MANY_FAILURES\n"
        }
        if (!line.startsWith(AUTH_PREFIX)) {
            recordAuthFailure()
            return "ERR:AUTH_REQUIRED\n"
        }
        val parts = line.substring(AUTH_PREFIX.length).split(":", limit = 2)
        val token = parts[0]
        val command = parts.getOrNull(1) ?: ""
        val expected = currentToken
        if (expected != null && MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())) {
            failures = 0
            return when {
                command == "COMPUTE_CORES" -> {
                    val count = Runtime.getRuntime().availableProcessors()
                    "SUCCESS:CoreCount=$count\n"
                }
                command.startsWith("BOOST_ALL_CORES") -> {
                    KernelManager.clusters.value.forEach { cluster ->
                        KernelManager.setClusterGovernor(cluster.clusterId, "performance")
                        cluster.coreIds.forEach { coreId ->
                            KernelManager.setFrequencyRange(coreId, cluster.maxFreq, cluster.maxFreq, rescan = false)
                        }
                    }
                    KernelManager.scanCores()
                    MemoryManager.compactMemory()
                    "SUCCESS: All cores boosted to performance governor & memory compacted.\n"
                }
                command.startsWith("EXEC:") -> {
                    val cmd = command.removePrefix("EXEC:")
                    SystemExecutor.executeCommand(cmd, "Remote Real Execution")
                }
                command.startsWith("SPAWN_APP:") -> {
                    val pkg = command.removePrefix("SPAWN_APP:")
                    val cmd = "am start -n $pkg"
                    SystemExecutor.executeCommand(cmd, "Remote App Spawn")
                }
                command == "PARTITION_STATUS" -> SharedPartitionManager.status()
                command.startsWith("PARTITION_WRITE_ALL:") -> {
                    val text = command.removePrefix("PARTITION_WRITE_ALL:")
                    if (SharedPartitionManager.writeAll(0, text)) "SUCCESS:Written to 4 partitions\n" else "ERR:PARTITION_WRITE_FAILED\n"
                }
                command.startsWith("PARTITION_WRITE:") -> {
                    val rest = command.removePrefix("PARTITION_WRITE:")
                    val idx = rest.indexOf(':')
                    if (idx <= 0) {
                        "ERR:PARTITION_FORMAT\n"
                    } else {
                        val p = rest.substring(0, idx).toIntOrNull()
                        val text = rest.substring(idx + 1)
                        if (p != null && SharedPartitionManager.write(p, 0, text)) "SUCCESS:Written to partition $p\n" else "ERR:PARTITION_WRITE_FAILED\n"
                    }
                }
                command == "PARTITION_READ" -> {
                    if (!SharedPartitionManager.isReady()) "ERR:PARTITIONS_NOT_READY\n"
                    else "SUCCESS:\n" + SharedPartitionManager.readUnified() + "\n"
                }
                command == "START_ROTATION" -> {
                    if (CoreRotationManager.start()) "SUCCESS:Rotation started\n"
                    else "ERR:ROTATION_START_FAILED\n"
                }
                command.startsWith("START_ROTATION:") -> {
                    val dwell = command.removePrefix("START_ROTATION:").toLongOrNull()
                        ?: CoreRotationManager.DEFAULT_DWELL_MS
                    if (CoreRotationManager.start(dwell)) "SUCCESS:Rotation started dwellMs=$dwell\n"
                    else "ERR:ROTATION_START_FAILED\n"
                }
                command == "STOP_ROTATION" -> {
                    CoreRotationManager.stop()
                    "SUCCESS:Rotation stopped, original frequencies restored\n"
                }
                command == "ROTATION_STATUS" -> CoreRotationManager.status()
                command == "MEMORY_POINTER" -> {
                    val pointer = SharedPartitionManager.getSupabasePointer()
                    if (pointer == null) "ERR:NO_POINTER\n" else "SUCCESS:SUPABASE_POINTER=$pointer\n"
                }
                command.startsWith("MEMORY_POINTER:") -> {
                    val url = command.removePrefix("MEMORY_POINTER:")
                    if (SharedPartitionManager.setSupabasePointer(url)) "SUCCESS:Pointer stored\n"
                    else "ERR:POINTER_STORE_FAILED\n"
                }
                else -> "UNKNOWN_COMMAND\n"
            }
        }
        recordAuthFailure()
        return "ERR:AUTH_REQUIRED\n"
    }

    private fun recordAuthFailure() {
        failures++
        if (failures >= MAX_AUTH_FAILURES) {
            blockedUntilMillis = System.currentTimeMillis() + BLOCK_DURATION_MS
            failures = 0
            log("Demasiados fallos de autenticacion. Bloqueo temporal de 60s.")
        } else {
            log("Fallo de autenticacion rechazado (intento ${failures}/$MAX_AUTH_FAILURES).")
        }
    }

    private fun log(msg: String) {
        val current = _computeLogs.value.toMutableList()
        current.add(0, msg)
        if (current.size > 50) current.removeAt(current.size - 1)
        _computeLogs.value = current
    }

    fun stopComputeNode() {
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        _isComputeServerRunning.value = false
        log("Nodo de procesamiento Android detenido.")
    }
}
