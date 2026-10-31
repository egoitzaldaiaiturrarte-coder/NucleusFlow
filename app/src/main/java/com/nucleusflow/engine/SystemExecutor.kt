package com.nucleusflow.engine

import android.util.Log
import com.nucleusflow.native.NativeCore
import com.nucleusflow.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ExecutionLog(
    val id: Long = System.currentTimeMillis(),
    val target: String,
    val path: String,
    val method: String,
    val command: String,
    val response: String,
    val success: Boolean,
    val timestamp: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
)

object SystemExecutor {
    private const val TAG = "SystemExecutor"

    private val _logs = MutableStateFlow<List<ExecutionLog>>(emptyList())
    val logs: StateFlow<List<ExecutionLog>> = _logs.asStateFlow()

    fun readFile(path: String, targetDesc: String = "Lectura archivo"): String {
        // Try native first if readable
        try {
            val content = NativeCore.readFile(path)
            if (content.isNotBlank()) {
                addLog(targetDesc, path, "NativeCore.readFile", "read $path", content.take(100), true)
                return content.trim()
            }
        } catch (ignored: Throwable) {}

        // Try standard File read
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val content = file.readText()
                addLog(targetDesc, path, "Java File.readText", "cat $path", content.take(100), true)
                return content.trim()
            }
        } catch (ignored: Throwable) {}

        // Try Shizuku / Root exec
        val cmd = "cat \"$path\""
        val resp = ShizukuManager.exec(cmd)
        val success = !resp.startsWith("ERR") && resp.isNotBlank()
        addLog(targetDesc, path, ShizukuManager.connectionMode.value, cmd, resp.take(100), success)
        return resp.trim()
    }

    fun writeFile(path: String, value: String, targetDesc: String = "Escritura archivo"): Boolean {
        // Try NativeCore write
        try {
            if (NativeCore.writeFile(path, value)) {
                addLog(targetDesc, path, "NativeCore.writeFile", "echo '$value' > $path", "OK", true)
                return true
            }
        } catch (ignored: Throwable) {}

        // Try standard Java File write
        try {
            val file = File(path)
            if (file.exists() && file.canWrite()) {
                file.writeText(value)
                addLog(targetDesc, path, "Java File.writeText", "echo '$value' > $path", "OK", true)
                return true
            }
        } catch (ignored: Throwable) {}

        // Try Shizuku / Root exec
        val escapedVal = value.replace("\"", "\\\"")
        val cmd = "echo -n \"$escapedVal\" > \"$path\""
        val resp = ShizukuManager.exec(cmd)
        val success = !resp.startsWith("ERR")
        addLog(targetDesc, path, ShizukuManager.connectionMode.value, cmd, resp.ifBlank { "OK" }, success)
        return success
    }

    fun executeCommand(command: String, targetDesc: String = "Comando sistema"): String {
        val resp = ShizukuManager.exec(command)
        val success = !resp.startsWith("ERR")
        addLog(targetDesc, "-", ShizukuManager.connectionMode.value, command, resp.take(200), success)
        return resp
    }

    private fun addLog(target: String, path: String, method: String, command: String, response: String, success: Boolean) {
        val log = ExecutionLog(
            target = target,
            path = path,
            method = method,
            command = command,
            response = response,
            success = success
        )
        val current = _logs.value.toMutableList()
        current.add(0, log)
        if (current.size > 100) current.removeAt(current.size - 1)
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
