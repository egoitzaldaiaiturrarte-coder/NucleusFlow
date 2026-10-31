package com.nucleusflow.engine

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.keystore.MasterKey
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
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.AES256_GCM_SPEC).build()
        val prefs = EncryptedSharedPreferences.create(context, "nucleus_compute_auth", masterKey)
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
                            KernelManager.setGovernor(coreId, "performance")
                            KernelManager.setFrequencyRange(coreId, cluster.maxFreq, cluster.maxFreq)
                        }
                    }
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
