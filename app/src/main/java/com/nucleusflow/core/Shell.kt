package com.nucleusflow.core

import com.nucleusflow.shizuku.IUserService
import com.nucleusflow.shizuku.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object Shell {
    @Volatile
    var service: IUserService? = null

    @Volatile
    var rootAvailable: Boolean = false
        private set

    @Volatile
    var lastMode: String = "local"

    suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) { execBlocking(cmd) }

    fun execBlocking(cmd: String): String {
        service?.let { svc ->
            lastMode = "shizuku"
            return try {
                svc.exec(cmd) ?: ""
            } catch (t: Throwable) {
                service = null
                lastMode = "local"
                "ERR:${t.message}"
            }
        }
        val privileged = ShizukuBridge.execPrivileged(cmd)
        if (privileged != null && !privileged.startsWith("ERR:newProcess")) {
            lastMode = "shizuku"
            return privileged
        }
        if (rootAvailable) {
            lastMode = "root"
            return runProcess(arrayOf("su", "-c", cmd), 12000)
        }
        lastMode = "local"
        return runProcess(arrayOf("sh", "-c", cmd), 12000)
    }

    fun detectRoot(): Boolean {
        val ok = try {
            val p = ProcessBuilder("sh", "-c", "su -c id").redirectErrorStream(true).start()
            val finished = p.waitFor(2500, TimeUnit.MILLISECONDS)
            if (!finished) {
                p.destroyForcibly()
                false
            } else {
                val out = p.inputStream.bufferedReader().readText()
                p.exitValue() == 0 && out.contains("uid=0")
            }
        } catch (t: Throwable) {
            false
        }
        rootAvailable = ok
        return ok
    }

    private fun runProcess(cmd: Array<String>, timeoutMs: Long): String {
        return try {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val deadline = System.currentTimeMillis() + timeoutMs
            var line: String?
            while (true) {
                if (reader.ready()) {
                    line = reader.readLine() ?: break
                    out.append(line).append('\n')
                    if (out.length > 200_000) break
                } else {
                    if (!p.isAlive) {
                        while (true) {
                            val rest = reader.readLine() ?: break
                            out.append(rest).append('\n')
                        }
                        break
                    }
                    if (System.currentTimeMillis() > deadline) {
                        p.destroyForcibly()
                        out.append("ERR:timeout\n")
                        break
                    }
                    Thread.sleep(10)
                }
            }
            p.waitFor(500, TimeUnit.MILLISECONDS)
            out.toString()
        } catch (t: Throwable) {
            "ERR:${t.message}"
        }
    }
}
