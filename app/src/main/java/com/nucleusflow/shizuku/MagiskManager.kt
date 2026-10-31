package com.nucleusflow.shizuku

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object MagiskManager {
    private const val TAG = "MagiskManager"

    fun isMagisk(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --version"))
            val out = BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                val sb = StringBuilder()
                br.readLine()?.let { sb.append(it) }
                sb.toString()
            }
            val code = proc.waitFor()
            val available = code == 0 && out.isNotBlank()
            if (available) {
                Log.i(TAG, "Magisk detectado: ${out.trim()}")
            }
            available
        } catch (e: Throwable) {
            Log.w(TAG, "Magisk no disponible", e)
            false
        }
    }

    fun version(): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --version"))
            val out = BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                br.readLine()?.orEmpty() ?: ""
            }
            proc.waitFor()
            out.trim()
        } catch (e: Throwable) {
            "unknown"
        }
    }

    fun exec(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = StringBuilder()
            BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) out.append(line).append('\n')
            }
            val code = proc.waitFor()
            if (code != 0) return "ERR:magisk_exec_exit_${code}"
            out.toString()
        } catch (e: Throwable) {
            "ERR:magisk_exec_${e.message}"
        }
    }
}
