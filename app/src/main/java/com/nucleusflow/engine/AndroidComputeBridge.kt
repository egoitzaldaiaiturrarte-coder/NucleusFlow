package com.nucleusflow.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

object AndroidComputeBridge {
    private const val TAG = "AndroidComputeBridge"
    private const val PORT = 8899

    private val _isComputeServerRunning = MutableStateFlow(false)
    val isComputeServerRunning: StateFlow<Boolean> = _isComputeServerRunning.asStateFlow()

    private val _computeLogs = MutableStateFlow<List<String>>(emptyList())
    val computeLogs: StateFlow<List<String>> = _computeLogs.asStateFlow()

    private var serverSocket: ServerSocket? = null

    fun startComputeNode() {
        if (_isComputeServerRunning.value) return
        try {
            serverSocket = ServerSocket(PORT)
            _isComputeServerRunning.value = true
            log("Nodo de procesamiento real Android iniciado en puerto $PORT")

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

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = socket.getOutputStream().bufferedWriter()
            val request = reader.readLine() ?: ""
            log("Petición real recibida: $request")

            val response = when {
                request.startsWith("BOOST_ALL_CORES") -> {
                    // Set all clusters to performance governor for max capacity
                    KernelManager.clusters.value.forEach { cluster ->
                        KernelManager.setClusterGovernor(cluster.clusterId, "performance")
                    }
                    MemoryManager.compactMemory()
                    "SUCCESS: All cores boosted to performance governor & memory compacted.\n"
                }
                request.startsWith("EXEC:") -> {
                    val cmd = request.removePrefix("EXEC:")
                    SystemExecutor.executeCommand(cmd, "Remote Real Execution")
                }
                request.startsWith("SPAWN_APP:") -> {
                    val pkg = request.removePrefix("SPAWN_APP:")
                    val cmd = "am start -n $pkg"
                    SystemExecutor.executeCommand(cmd, "Remote App Spawn")
                }
                else -> "UNKNOWN_COMMAND\n"
            }

            writer.write(response)
            writer.flush()
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling compute client", e)
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
